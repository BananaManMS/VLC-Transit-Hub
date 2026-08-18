package com.example.ui.metro

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

object MetroCardNetworkSource {

    private val defaultClient = com.example.data.network.NetworkModule.okHttpClient.newBuilder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    suspend fun fetchCardFromNetwork(
        trimmedCard: String,
        client: OkHttpClient = defaultClient
    ): JSONObject {
        val urlViajes = "https://metroapi.alexbadi.es/viajes/$trimmedCard"
        val urlTarjeta = "https://metroapi.alexbadi.es/tarjeta/$trimmedCard"

        val requestViajes = Request.Builder()
            .url(urlViajes)
            .header("User-Agent", com.example.data.network.NetworkModule.USER_AGENT)
            .build()

        val requestTarjeta = Request.Builder()
            .url(urlTarjeta)
            .header("User-Agent", com.example.data.network.NetworkModule.USER_AGENT)
            .build()

        return withContext(Dispatchers.IO) {
            val merged = JSONObject()
            var tarjetaSuccess = false

            try {
                client.newCall(requestTarjeta).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank()) {
                            val json = JSONObject(body)
                            val resObj = json.optJSONObject("resultado")
                            if (resObj != null) {
                                tarjetaSuccess = true
                                val resKeys = resObj.keys()
                                while (resKeys.hasNext()) {
                                    val key = resKeys.next()
                                    val value = resObj.get(key)
                                    if (value != null && value != JSONObject.NULL) {
                                        merged.put(key, value)
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // ignore
            }

            var viajesSuccess = false
            try {
                client.newCall(requestViajes).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank()) {
                            val json = JSONObject(body)
                            val infoObj = json.optJSONObject("info")
                            val viajesArray = json.optJSONArray("viajes") ?: JSONArray()

                            if (!tarjetaSuccess && infoObj != null) {
                                val infoKeys = infoObj.keys()
                                while (infoKeys.hasNext()) {
                                    val key = infoKeys.next()
                                    val value = infoObj.get(key)
                                    if (value != null && value != JSONObject.NULL) {
                                        merged.put(key, value)
                                    }
                                }
                            }

                            merged.put("viajes", viajesArray)
                            merged.put("viajes_realizados", viajesArray.length())
                            viajesSuccess = true
                        }
                    }
                }
            } catch (e: Exception) {
                // ignore
            }

            if (!tarjetaSuccess && !viajesSuccess) {
                throw Exception("La tarjeta no existe o no se pudieron obtener los datos")
            }

            if (!merged.has("viajes")) {
                merged.put("viajes", JSONArray())
                merged.put("viajes_realizados", 0)
            }

            if (merged.has("zona") && !merged.has("zonas")) {
                merged.put("zonas", merged.optString("zona"))
            }

            if (!merged.has("operador")) {
                merged.put("operador", "Metrovalencia")
            }

            merged
        }
    }
}
