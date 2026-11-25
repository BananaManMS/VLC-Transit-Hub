package com.example.data.repository

import android.util.Log
import com.example.ui.metro.AccessibilityIncident
import com.example.ui.metro.MetroIncident
import com.example.ui.metro.MetroNewsItem
import com.example.ui.metro.MetroNotice
import com.example.ui.metro.MetroNoticeCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.example.data.network.NetworkModule
import okhttp3.OkHttpClient
import org.json.JSONObject

class MetroAlertsRepository(
    private val httpClient: OkHttpClient = defaultClient
) {
    companion object {
        internal const val CACHE_DURATION_MS = 15 * 60 * 1000L // 15 minutes unified alerts TTL
        internal const val NEWS_CACHE_DURATION_MS = 6 * 60 * 60 * 1000L // 6 hours TTL for rarely changing news
        @Volatile private var cachedAccessibility: List<AccessibilityIncident> = emptyList()
        @Volatile private var cachedIncidents: List<MetroIncident> = emptyList()
        @Volatile private var cachedNotices: List<MetroNotice> = emptyList()
        @Volatile private var cachedNews: List<MetroNewsItem> = emptyList()
        @Volatile private var lastFetchTimestamp: Long = 0L
        @Volatile private var lastNewsFetchTimestamp: Long = 0L
        private val fetchMutex = kotlinx.coroutines.sync.Mutex()
        private val newsMutex = kotlinx.coroutines.sync.Mutex()

        private val defaultClient: OkHttpClient by lazy {
            NetworkModule.okHttpClient.newBuilder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()
        }

        private val _accessibilityIncidents = MutableStateFlow<List<AccessibilityIncident>>(emptyList())
        private val _activeIncidents = MutableStateFlow<List<MetroIncident>>(emptyList())
        private val _specialNotices = MutableStateFlow<List<MetroNotice>>(emptyList())
        private val _metroNews = MutableStateFlow<List<MetroNewsItem>>(emptyList())
        private val _isAlertsLoading = MutableStateFlow(true)
        private val _hasAlertsError = MutableStateFlow(false)
        private val _alertsErrorDetail = MutableStateFlow<String?>(null)
        private val _isNewsLoading = MutableStateFlow(false)

        fun hasValidCache(): Boolean {
            val now = System.currentTimeMillis()
            return lastFetchTimestamp > 0L && (now - lastFetchTimestamp < CACHE_DURATION_MS)
        }

        fun hasValidNewsCache(): Boolean {
            val now = System.currentTimeMillis()
            return lastNewsFetchTimestamp > 0L && (now - lastNewsFetchTimestamp < NEWS_CACHE_DURATION_MS)
        }

        @androidx.annotation.VisibleForTesting
        fun clearCacheForTesting() {
            lastFetchTimestamp = 0L
            lastNewsFetchTimestamp = 0L
            cachedAccessibility = emptyList()
            cachedIncidents = emptyList()
            cachedNotices = emptyList()
            cachedNews = emptyList()
            _accessibilityIncidents.value = emptyList()
            _activeIncidents.value = emptyList()
            _specialNotices.value = emptyList()
            _metroNews.value = emptyList()
            _hasAlertsError.value = false
            _alertsErrorDetail.value = null
            _isAlertsLoading.value = false
            _isNewsLoading.value = false
        }

        fun getCachedIncidents(): List<MetroIncident> = cachedIncidents
        fun getCachedNotices(): List<MetroNotice> = cachedNotices
        fun getCachedAccessibility(): List<AccessibilityIncident> = cachedAccessibility
    }

    val accessibilityIncidents = _accessibilityIncidents.asStateFlow()
    val activeIncidents = _activeIncidents.asStateFlow()
    val specialNotices = _specialNotices.asStateFlow()
    val metroNews = _metroNews.asStateFlow()
    val isAlertsLoading = _isAlertsLoading.asStateFlow()
    val hasAlertsError = _hasAlertsError.asStateFlow()
    val alertsErrorDetail = _alertsErrorDetail.asStateFlow()
    val isNewsLoading = _isNewsLoading.asStateFlow()

    suspend fun fetchAllAlerts(force: Boolean = false, includeNews: Boolean = false) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && lastFetchTimestamp > 0L && (now - lastFetchTimestamp < CACHE_DURATION_MS)) {
            _accessibilityIncidents.value = cachedAccessibility
            _activeIncidents.value = cachedIncidents
            _specialNotices.value = cachedNotices
            if (cachedNews.isNotEmpty() || lastNewsFetchTimestamp > 0L) {
                _metroNews.value = cachedNews
                _isNewsLoading.value = false
            }
            _isAlertsLoading.value = false
            _hasAlertsError.value = false
            if (includeNews) {
                fetchNews(force = false)
            }
            return@withContext
        }

        fetchMutex.withLock {
            val lockNow = System.currentTimeMillis()
            if (!force && lastFetchTimestamp > 0L && (lockNow - lastFetchTimestamp < CACHE_DURATION_MS)) {
                _accessibilityIncidents.value = cachedAccessibility
                _activeIncidents.value = cachedIncidents
                _specialNotices.value = cachedNotices
                if (cachedNews.isNotEmpty()) {
                    _metroNews.value = cachedNews
                    _isNewsLoading.value = false
                }
                _isAlertsLoading.value = false
                _hasAlertsError.value = false
                return@withLock
            }

            _isAlertsLoading.value = true
            _hasAlertsError.value = false
            _alertsErrorDetail.value = null
            try {
                val url = "${NetworkModule.VIATGEM_PROXY_BASE_URL}/v1/avisos"
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .header("User-Agent", NetworkModule.USER_AGENT)
                    .header("X-App-Secret", com.example.BuildConfig.METROVALENCIA_API_SECRET.trim())
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw Exception("HTTP Error: ${response.code}")
                    }
                    val responseBody = response.body?.string() ?: throw Exception("Empty response body")
                
                if (!responseBody.trim().startsWith("<")) {
                    val rootJson = JSONObject(responseBody)
                    val success = rootJson.optBoolean("success", false)
                    if (success) {
                        val dataObj = rootJson.optJSONObject("data")
                        if (dataObj != null) {
                            // Helper function to format lines array
                            fun formatLines(item: JSONObject): String? {
                                val lineasArr = item.optJSONArray("lineas_afectadas") ?: return null
                                if (lineasArr.length() == 0) return null
                                val list = mutableListOf<String>()
                                for (l in 0 until lineasArr.length()) {
                                    val valStr = lineasArr.optString(l, "").trim()
                                    if (valStr.isNotEmpty()) {
                                        val formatted = if (valStr.all { it.isDigit() }) "L$valStr" else valStr
                                        list.add(formatted)
                                    }
                                }
                                return if (list.isNotEmpty()) list.joinToString(", ") else null
                            }

                            fun JSONObject.optCleanStr(key: String): String? {
                                if (this.isNull(key)) return null
                                val str = this.optString(key, "").trim()
                                if (str.isEmpty() || str.equals("null", ignoreCase = true)) return null
                                return str
                            }

                            // 1. Map accesibilidad
                            val accArray = dataObj.optJSONArray("accesibilidad") ?: dataObj.optJSONArray("incidencias_accesibilidad") ?: org.json.JSONArray()
                            val accList = mutableListOf<AccessibilityIncident>()
                            for (i in 0 until accArray.length()) {
                                val item = accArray.optJSONObject(i) ?: continue
                                val id = item.optString("id", "acc-$i")
                                val titulo = item.optCleanStr("titulo") ?: ""
                                val rawDescripcion = item.optCleanStr("descripcion") ?: ""
                                val descripcion = com.example.util.StationAccessibilityHelper.cleanAccessibilityAlertText(rawDescripcion)
                                if (titulo.isBlank() && descripcion.isBlank()) continue

                                val estIdStr = item.optString("estacion_id", "")
                                val estacionId = estIdStr.toIntOrNull() ?: if (!item.isNull("estacion_id")) item.optInt("estacion_id") else null
                                val estacionNombre = item.optCleanStr("estacion_nombre")
                                val lineasAfectadas = formatLines(item)
                                val creadoEl = item.optCleanStr("fecha_publicacion")
                                    ?: item.optCleanStr("fecha")
                                    ?: item.optCleanStr("updated_at")
                                    ?: item.optCleanStr("created_at")
                                accList.add(
                                    AccessibilityIncident(
                                        id = id,
                                        tituloEs = titulo,
                                        descripcionEs = descripcion,
                                        tituloCa = titulo,
                                        descripcionCa = descripcion,
                                        creadoEl = creadoEl,
                                        estacionId = estacionId,
                                        estacionNombre = estacionNombre,
                                        lineasAfectadas = lineasAfectadas
                                    )
                                )
                            }
                            _accessibilityIncidents.value = accList

                            // 2. Map prioritarios (Network Incidents) - Genuine circulation incidents & circulation notices
                            val prioArray = dataObj.optJSONArray("prioritarios") ?: org.json.JSONArray()
                            val prioList = mutableListOf<MetroIncident>()
                            val prioNoticesForGeneralTab = mutableListOf<MetroNotice>()
                            val promotedPriorityIds = mutableSetOf<String>()
                            val promotedPrioritySignatures = mutableSetOf<String>()

                            for (i in 0 until prioArray.length()) {
                                val item = prioArray.optJSONObject(i) ?: continue
                                val categoria = item.optCleanStr("categoria") ?: "incidencia"
                                val catNombre = item.optCleanStr("categoria_nombre") ?: "Incidencia"
                                val id = item.optString("id", "prio-$i")
                                val titulo = item.optCleanStr("titulo") ?: ""
                                val descripcion = item.optCleanStr("descripcion") ?: ""
                                if (titulo.isBlank() && descripcion.isBlank()) continue

                                val fullDesc = if (descripcion.isNotBlank() && !descripcion.equals(titulo, ignoreCase = true)) {
                                    if (titulo.isNotBlank()) "$titulo: $descripcion" else descripcion
                                } else {
                                    if (titulo.isNotBlank()) titulo else descripcion
                                }
                                val lineaFgv = formatLines(item)
                                val updatedAt = item.optCleanStr("fecha_publicacion")
                                    ?: item.optCleanStr("updated_at")
                                    ?: item.optCleanStr("fecha")

                                val resolvedCat = MetroNoticeCategory.resolveEffectiveCategory(
                                    category = categoria,
                                    categoryName = catNombre,
                                    title = titulo,
                                    description = descripcion,
                                    isFromPriorityFeed = true,
                                    hasAffectedLines = !lineaFgv.isNullOrBlank()
                                )

                                // Promote genuine circulation incidents & priority circulation notices to active network incidents
                                if (resolvedCat.isCirculationImpact) {
                                    promotedPriorityIds.add(id)
                                    promotedPrioritySignatures.add("${titulo.trim().lowercase()}|${descripcion.trim().lowercase()}")
                                    prioList.add(
                                        MetroIncident(
                                            id = id,
                                            descriptionEs = fullDesc,
                                            descriptionCa = fullDesc,
                                            descriptionEn = fullDesc,
                                            lineaFgv = lineaFgv,
                                            updatedAt = updatedAt,
                                            category = resolvedCat.rawKey
                                        )
                                    )
                                } else if (resolvedCat != MetroNoticeCategory.ACCESIBILIDAD) {
                                    // Non-circulation priority items (e.g. weather alerts, promotions, planned works) go to general notices
                                    prioNoticesForGeneralTab.add(
                                        MetroNotice(
                                            id = id,
                                            category = resolvedCat.rawKey,
                                            categoryName = catNombre,
                                            title = titulo,
                                            description = descripcion,
                                            publicationDate = updatedAt,
                                            lineasAfectadas = lineaFgv
                                        )
                                    )
                                }
                            }
                            _activeIncidents.value = prioList

                            // 3. Map avisos (Special Notices, Works & General Announcements - filtering out accessibility and promoted priority incidents)
                            val avisosArray = dataObj.optJSONArray("avisos") ?: org.json.JSONArray()
                            val avisosList = mutableListOf<MetroIncident>()
                            val specialNoticesList = mutableListOf<MetroNotice>()
                            // Include any non-circulation notices that came from prioritarios
                            specialNoticesList.addAll(prioNoticesForGeneralTab)

                            for (i in 0 until avisosArray.length()) {
                                val item = avisosArray.optJSONObject(i) ?: continue
                                val categoria = item.optCleanStr("categoria") ?: ""
                                val id = item.optString("id", "av-$i")
                                val catNombre = item.optCleanStr("categoria_nombre") ?: "Aviso"
                                val titulo = item.optCleanStr("titulo") ?: ""
                                val descripcion = item.optCleanStr("descripcion") ?: ""
                                if (titulo.isBlank() && descripcion.isBlank()) continue

                                val signature = "${titulo.trim().lowercase()}|${descripcion.trim().lowercase()}"
                                if (id in promotedPriorityIds || signature in promotedPrioritySignatures) {
                                    continue
                                }

                                val fecha = item.optCleanStr("fecha_publicacion")
                                    ?: item.optCleanStr("updated_at")
                                    ?: item.optCleanStr("fecha")
                                val lineaFgv = formatLines(item)

                                val resolvedCat = MetroNoticeCategory.resolveEffectiveCategory(
                                    category = categoria,
                                    categoryName = catNombre,
                                    title = titulo,
                                    description = descripcion,
                                    isFromPriorityFeed = false,
                                    hasAffectedLines = !lineaFgv.isNullOrBlank()
                                )

                                // Add to special notices if category is not accessibility
                                if (resolvedCat != MetroNoticeCategory.ACCESIBILIDAD) {
                                    specialNoticesList.add(
                                        MetroNotice(
                                            id = "aviso_$id",
                                            category = resolvedCat.rawKey,
                                            categoryName = catNombre,
                                            title = titulo,
                                            description = descripcion,
                                            publicationDate = fecha,
                                            lineasAfectadas = lineaFgv
                                        )
                                    )
                                }

                                val fullDesc = if (descripcion.isNotBlank() && !descripcion.equals(titulo, ignoreCase = true)) {
                                    if (titulo.isNotBlank()) "$titulo: $descripcion" else descripcion
                                } else {
                                    if (titulo.isNotBlank()) titulo else descripcion
                                }
                                avisosList.add(
                                    MetroIncident(
                                        id = "aviso_$id",
                                        descriptionEs = fullDesc,
                                        descriptionCa = fullDesc,
                                        descriptionEn = fullDesc,
                                        lineaFgv = lineaFgv,
                                        updatedAt = fecha,
                                        category = resolvedCat.rawKey
                                    )
                                )
                            }
                            avisosList.sortByDescending { it.updatedAt ?: "" }
                            val notices = specialNoticesList.distinctBy { "${it.category}_${it.title}_${it.publicationDate}" }
                            _specialNotices.value = notices

                            // Cache successful responses in companion object for fast subsequent reads
                            cachedAccessibility = accList
                            cachedIncidents = prioList
                            cachedNotices = notices
                            lastFetchTimestamp = System.currentTimeMillis()
                        }
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            _hasAlertsError.value = true
            _alertsErrorDetail.value = e.message
            Log.w("MetroAlertsRepository", "Notice: Unified alerts unavailable or timed out: ${e.message}")
        } finally {
            _isAlertsLoading.value = false
        }
        }

        // Fetch news lazily only when explicitly requested (e.g., from Avisos tab)
        if (includeNews) {
            try {
                fetchNews(force = force)
            } catch (_: Exception) {}
        }
    }

    suspend fun fetchNews(force: Boolean = false) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && lastNewsFetchTimestamp > 0L && (now - lastNewsFetchTimestamp < NEWS_CACHE_DURATION_MS)) {
            _metroNews.value = cachedNews
            _isNewsLoading.value = false
            return@withContext
        }

        newsMutex.withLock {
            val lockNow = System.currentTimeMillis()
            if (!force && lastNewsFetchTimestamp > 0L && (lockNow - lastNewsFetchTimestamp < NEWS_CACHE_DURATION_MS)) {
                _metroNews.value = cachedNews
                _isNewsLoading.value = false
                return@withLock
            }

            _isNewsLoading.value = true
            try {
                val url = "${NetworkModule.VIATGEM_PROXY_BASE_URL}/v1/noticias"
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .header("User-Agent", NetworkModule.USER_AGENT)
                    .header("X-App-Secret", com.example.BuildConfig.METROVALENCIA_API_SECRET.trim())
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val body = response.body?.string() ?: return@use
                    val root = JSONObject(body)
                    if (root.optBoolean("success", false)) {
                        val dataObj = root.optJSONObject("data")
                        val noticiasArr = dataObj?.optJSONArray("noticias") ?: root.optJSONArray("noticias")
                        if (noticiasArr != null) {
                            val newsList = mutableListOf<MetroNewsItem>()
                            for (i in 0 until noticiasArr.length()) {
                                val item = noticiasArr.optJSONObject(i) ?: continue
                                val id = item.optLong("id", i.toLong())
                                val idFgv = if (!item.isNull("id_fgv")) item.optLong("id_fgv") else null
                                val titulo = item.optString("titulo", "")
                                val rawDesc = item.optString("descripcion", "")
                                val cleanDesc = rawDesc
                                    .replace("&#8230;", "...")
                                    .replace("&raquo;", "»")
                                    .replace("&laquo;", "«")
                                    .replace("&amp;", "&")
                                    .replace(Regex("<[^>]*>"), "")
                                    .trim()
                                val rawCont = item.optString("contenido", item.optString("content", item.optString("cuerpo", "")))
                                val cleanCont = rawCont
                                    .replace("<br>", "\n")
                                    .replace("<br/>", "\n")
                                    .replace("<br />", "\n")
                                    .replace("</p>", "\n\n")
                                    .replace("<p>", "")
                                    .replace("&#8230;", "...")
                                    .replace("&raquo;", "»")
                                    .replace("&laquo;", "«")
                                    .replace("&amp;", "&")
                                    .replace("&nbsp;", " ")
                                    .replace(Regex("<[^>]*>"), "")
                                    .trim()
                                val rawUrl = if (!item.isNull("url")) item.optString("url", "").trim() else ""
                                val urlNews = if (rawUrl.isNotEmpty() && !rawUrl.equals("null", ignoreCase = true)) rawUrl else null
                                val imgUrl = item.optString("url_imagen", "").ifEmpty { null }
                                val fecha = item.optString("fecha_publicacion", item.optString("created_at", item.optString("fecha", "")))

                                newsList.add(
                                    MetroNewsItem(
                                        id = id,
                                        idFgv = idFgv,
                                        title = titulo,
                                        description = cleanDesc,
                                        content = cleanCont.ifEmpty { cleanDesc },
                                        url = urlNews,
                                        imageUrl = imgUrl,
                                        publicationDate = fecha
                                    )
                                )
                            }
                            _metroNews.value = newsList
                            cachedNews = newsList
                            lastNewsFetchTimestamp = System.currentTimeMillis()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("MetroAlertsRepository", "Notice: News service unavailable or timed out: ${e.message}")
            } finally {
                _isNewsLoading.value = false
            }
        }
    }
}
