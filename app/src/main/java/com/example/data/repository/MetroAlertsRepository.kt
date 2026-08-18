package com.example.data.repository

import android.util.Log
import com.example.ui.metro.AccessibilityIncident
import com.example.ui.metro.MetroIncident
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

    private val _twitterIncidents = MutableStateFlow<List<MetroIncident>>(emptyList())
    val twitterIncidents = _twitterIncidents.asStateFlow()

    private val _activeIncidents = MutableStateFlow<List<MetroIncident>>(emptyList())
    val activeIncidents = _activeIncidents.asStateFlow()

    private val _isAlertsLoading = MutableStateFlow(true)
    val isAlertsLoading = _isAlertsLoading.asStateFlow()

    private val _twitterLoading = MutableStateFlow(false)
    val twitterLoading = _twitterLoading.asStateFlow()


    suspend fun fetchAccessibilityAlerts() = withContext(Dispatchers.IO) {
        try {
            val url = "https://metroapi.alexbadi.es/accesibilidad"
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", com.example.data.network.NetworkModule.USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("HTTP Error: ${response.code}")
                }
                val responseBody = response.body?.string() ?: throw Exception("Empty response body")
                val jsonObject = JSONObject(responseBody)
                
                val incsArray = jsonObject.optJSONArray("incidencias_accesibilidad") ?: return@use
                val transArray = jsonObject.optJSONArray("incidencias_accesibilidad_translations") ?: org.json.JSONArray()

                // Map of incident_id -> Map of locale -> (titulo, descripcion)
                val translationsMap = mutableMapOf<Int, MutableMap<String, Pair<String, String>>>()
                for (i in 0 until transArray.length()) {
                    val tObj = transArray.optJSONObject(i) ?: continue
                    if (!tObj.isNull("deleted_at")) continue
                    
                    val incId = tObj.optInt("incidencia_id", -1)
                    val locale = tObj.optString("locale", "")
                    val titulo = tObj.optString("titulo", "")
                    val descripcion = tObj.optString("descripcion", "")
                    
                    if (incId != -1 && locale.isNotEmpty()) {
                        translationsMap.getOrPut(incId) { mutableMapOf() }[locale] = Pair(titulo, descripcion)
                    }
                }

                val list = mutableListOf<AccessibilityIncident>()
                for (i in 0 until incsArray.length()) {
                    val item = incsArray.optJSONObject(i) ?: continue
                    if (!item.isNull("deleted_at")) {
                        continue
                    }
                    
                    val id = item.optInt("id", -1)
                    if (id == -1) continue
                    
                    val estacionId = if (item.isNull("estacion_id")) null else item.optInt("estacion_id")
                    val creadoEl = item.optString("created_at", "")

                    val transForInc = translationsMap[id]
                    val esPair = transForInc?.get("ES") ?: Pair("", "")
                    val caPair = transForInc?.get("CA") ?: Pair("", "")

                    val titleEs = esPair.first
                    val descEs = esPair.second
                    val titleCa = caPair.first
                    val descCa = caPair.second

                    list.add(
                        AccessibilityIncident(
                            id = id.toString(),
                            tituloEs = titleEs,
                            descripcionEs = descEs,
                            tituloCa = titleCa,
                            descripcionCa = descCa,
                            creadoEl = creadoEl,
                            estacionId = estacionId
                        )
                    )
                }
                _accessibilityIncidents.value = list
            }
        } catch (e: Exception) {
            Log.w("MetroAlertsRepository", "Error fetching accessibility alerts: ${e.message}")
        }
    }

    suspend fun fetchTwitterIncidents() = withContext(Dispatchers.IO) {
        _twitterLoading.value = true
        try {
            val url = "https://metroapi.alexbadi.es/db/incidencias"
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", com.example.data.network.NetworkModule.USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w("MetroAlertsRepository", "HTTP error ${response.code} fetching twitter incidents")
                    return@use
                }
                val responseBody = response.body?.string() ?: return@use
                val jsonObject = JSONObject(responseBody)
                val resArray = jsonObject.optJSONArray("res") ?: return@use

                val incidentsList = mutableListOf<MetroIncident>()
                for (i in 0 until resArray.length()) {
                    val item = resArray.optJSONObject(i) ?: continue
                    val dataObj = item.optJSONObject("data") ?: continue

                    val id = item.optString("_id", "")
                    val langsObj = dataObj.optJSONObject("langs")
                    val caObj = langsObj?.optJSONObject("CA")
                    val enObj = langsObj?.optJSONObject("EN")
                    val esObj = langsObj?.optJSONObject("ES")

                    val descEs = esObj?.optString("descripcion") ?: ""
                    val descCa = caObj?.optString("descripcion") ?: ""
                    val descEn = enObj?.optString("descripcion") ?: ""

                    val lineaFgv = if (dataObj.isNull("linea_fgv")) null else dataObj.optString("linea_fgv")
                    val updatedAt = if (dataObj.isNull("updated_at")) null else dataObj.optString("updated_at")

                    incidentsList.add(MetroIncident(id, descEs, descCa, descEn, lineaFgv, updatedAt))
                }
                incidentsList.sortByDescending { it.updatedAt ?: "" }
                _twitterIncidents.value = incidentsList
            }
        } catch (e: Exception) {
            Log.w("MetroAlertsRepository", "Error fetching twitter incidents: ${e.message}")
        } finally {
            _twitterLoading.value = false
        }
    }

    suspend fun fetchRealTimeAlerts() = withContext(Dispatchers.IO) {
        _isAlertsLoading.value = true
        try {
            val url = "https://metroapi.alexbadi.es/incidencias"
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", com.example.data.network.NetworkModule.USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w("MetroAlertsRepository", "HTTP error ${response.code} fetching real-time alerts")
                    return@use
                }
                val responseBody = response.body?.string() ?: return@use
                val jsonObject = JSONObject(responseBody)
                val resArray = jsonObject.optJSONArray("incidencias_alexbadi") ?: return@use

                val list = mutableListOf<MetroIncident>()
                for (i in 0 until resArray.length()) {
                    val item = resArray.optJSONObject(i) ?: continue
                    if (!item.isNull("deleted_at")) {
                        continue
                    }

                    val id = item.optString("id", "")
                    val alexbadiId = item.optString("alexbadi_id", id)
                    val langsObj = item.optJSONObject("langs")
                    val caObj = langsObj?.optJSONObject("CA")
                    val enObj = langsObj?.optJSONObject("EN")
                    val esObj = langsObj?.optJSONObject("ES")

                    val descEs = esObj?.optString("descripcion") ?: ""
                    val descCa = caObj?.optString("descripcion") ?: ""
                    val descEn = enObj?.optString("descripcion") ?: ""

                    val lineaFgv = if (item.isNull("linea_fgv")) null else item.optString("linea_fgv")
                    val updatedAt = if (item.isNull("updated_at")) null else item.optString("updated_at")

                    list.add(MetroIncident(alexbadiId, descEs, descCa, descEn, lineaFgv, updatedAt))
                }
                _activeIncidents.value = list.distinctBy { incident ->
                    val key = if (incident.id.isNotBlank()) incident.id.trim()
                    else (incident.descriptionEs.trim() + "_" + incident.descriptionCa.trim()).ifBlank { incident.toString() }
                    Pair(key, incident.lineaFgv)
                }
            }
        } catch (e: Exception) {
            Log.w("MetroAlertsRepository", "Error fetching active incidents: ${e.message}")
        } finally {
            _isAlertsLoading.value = false
        }
    }

    suspend fun fetchAllAlerts() {
        fetchRealTimeAlerts()
        fetchAccessibilityAlerts()
    }
}
