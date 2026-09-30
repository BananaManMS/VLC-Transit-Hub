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
import kotlinx.coroutines.withContext
import com.example.data.network.NetworkModule
import okhttp3.OkHttpClient
import org.json.JSONObject

class MetroAlertsRepository {
    private val client = NetworkModule.okHttpClient.newBuilder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val _accessibilityIncidents = MutableStateFlow<List<AccessibilityIncident>>(emptyList())
    val accessibilityIncidents = _accessibilityIncidents.asStateFlow()

    private val _activeIncidents = MutableStateFlow<List<MetroIncident>>(emptyList())
    val activeIncidents = _activeIncidents.asStateFlow()

    private val _specialNotices = MutableStateFlow<List<MetroNotice>>(emptyList())
    val specialNotices = _specialNotices.asStateFlow()

    private val _metroNews = MutableStateFlow<List<MetroNewsItem>>(emptyList())
    val metroNews = _metroNews.asStateFlow()

    private val _isAlertsLoading = MutableStateFlow(true)
    val isAlertsLoading = _isAlertsLoading.asStateFlow()

    private val _hasAlertsError = MutableStateFlow(false)
    val hasAlertsError = _hasAlertsError.asStateFlow()

    private val _isNewsLoading = MutableStateFlow(false)
    val isNewsLoading = _isNewsLoading.asStateFlow()

    suspend fun fetchAllAlerts() = withContext(Dispatchers.IO) {
        _isAlertsLoading.value = true
        _hasAlertsError.value = false
        try {
            val url = "https://metrovalencia-cloudflare-worker-api-tester-224385556854.europe-west2.run.app/v1/avisos"
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", NetworkModule.USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
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

                            // 2. Map prioritarios (Network Incidents) - Only genuine circulation incidents (incidencia, aviso)
                            val prioArray = dataObj.optJSONArray("prioritarios") ?: org.json.JSONArray()
                            val prioList = mutableListOf<MetroIncident>()
                            val prioNoticesForGeneralTab = mutableListOf<MetroNotice>()

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

                                // Strict filter: only real circulation incidents (incidencia or aviso impacting transit)
                                if (MetroNoticeCategory.isRealCirculationIncident(categoria, titulo, descripcion)) {
                                    prioList.add(MetroIncident(id, fullDesc, fullDesc, fullDesc, lineaFgv, updatedAt, categoria))
                                } else if (!categoria.equals("accesibilidad", ignoreCase = true)) {
                                    // Non-circulation priority items (e.g. weather alerts, promotions) go to general notices
                                    prioNoticesForGeneralTab.add(
                                        MetroNotice(
                                            id = id,
                                            category = categoria,
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

                            // 3. Map avisos (Special Notices, Works & General Announcements - filtering out accessibility)
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

                                val fecha = item.optCleanStr("fecha_publicacion")
                                    ?: item.optCleanStr("updated_at")
                                    ?: item.optCleanStr("fecha")
                                val lineaFgv = formatLines(item)

                                // Add to special notices if category is not accessibility
                                if (!categoria.equals("accesibilidad", ignoreCase = true)) {
                                    specialNoticesList.add(
                                        MetroNotice(
                                            id = "aviso_$id",
                                            category = categoria,
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
                                avisosList.add(MetroIncident("aviso_$id", fullDesc, fullDesc, fullDesc, lineaFgv, fecha, categoria))
                            }
                            avisosList.sortByDescending { it.updatedAt ?: "" }
                            _specialNotices.value = specialNoticesList.distinctBy { "${it.category}_${it.title}_${it.publicationDate}" }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            _hasAlertsError.value = true
            Log.w("MetroAlertsRepository", "Notice: Unified alerts unavailable or timed out: ${e.message}")
        } finally {
            _isAlertsLoading.value = false
        }

        // Also fetch news from /v1/noticias
        fetchNews()
    }

    suspend fun fetchNews() = withContext(Dispatchers.IO) {
        _isNewsLoading.value = true
        try {
            val url = "https://metrovalencia-cloudflare-worker-api-tester-224385556854.europe-west2.run.app/v1/noticias"
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", NetworkModule.USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
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
