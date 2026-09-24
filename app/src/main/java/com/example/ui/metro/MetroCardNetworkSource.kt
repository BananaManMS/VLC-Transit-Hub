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
    ): JSONObject = withContext(Dispatchers.IO) {
        val clean = trimmedCard.filter { it.isDigit() }
        val candidates = mutableListOf<String>()
        candidates.add(clean)
        if (clean.length == 12) {
            candidates.add(clean.take(10))
        }

        var lastException: Exception? = null
        for (candidate in candidates) {
            try {
                return@withContext queryApiForCard(candidate, client)
            } catch (e: Exception) {
                lastException = e
            }
        }
        throw lastException ?: Exception("No se pudo obtener información de la tarjeta $trimmedCard desde el servidor.")
    }

    private fun queryApiForCard(
        card: String,
        client: OkHttpClient
    ): JSONObject {
        val url = "https://metrovalencia-cloudflare-worker-api-tester-224385556854.europe-west2.run.app/v1/tarjeta/$card"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", com.example.data.network.NetworkModule.USER_AGENT)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP Error: ${response.code}")
            }
            val body = response.body?.string() ?: throw Exception("Empty body")
            
            if (!body.trim().startsWith("<")) {
                val root = JSONObject(body)
                val success = root.optBoolean("success", false)
                if (success) {
                    val dataObj = root.optJSONObject("data")
                    if (dataObj != null) {
                        val tarjetasArray = dataObj.optJSONArray("tarjetas")
                        if (tarjetasArray != null && tarjetasArray.length() > 0) {
                            val firstCard = tarjetasArray.getJSONObject(0)
                            val merged = JSONObject()

                            val title = firstCard.optString("titulo", "Móbilis / SUMA")
                            merged.put("nombre", title)
                            merged.put("titulo", title)
                            
                            val tipo = firstCard.optString("tipo", "MULTIVIAJE")
                            merged.put("clase", tipo)
                            
                            val rawSaldo = firstCard.optDouble("saldo", 0.0)
                            merged.put("saldo_restante", rawSaldo * 100.0)
                            merged.put("viajes_restantes", rawSaldo.toInt())
                            merged.put("saldo", firstCard.optString("saldoFormateado", "${rawSaldo.toInt()} viajes"))

                            val zona = firstCard.optString("zona", "A")
                            val zonaFormatted = if (zona.isBlank()) "Zona A" else if (zona.startsWith("Zona", ignoreCase = true)) zona else "Zona $zona"
                            merged.put("zona", zonaFormatted)
                            merged.put("zonas", zonaFormatted)

                            val fechaCaducidad = firstCard.optString("fechaCaducidad", "")
                            merged.put("fecha_caducidad", fechaCaducidad)
                            merged.put("caducidad", fechaCaducidad)
                            merged.put("fechaCaducidad", fechaCaducidad)

                            val ampliado = firstCard.optBoolean("ampliado", false)
                            val ampliadoFormateado = firstCard.optString("ampliadoFormateado", if (ampliado) "Sí" else "No")
                            merged.put("ampliado", if (ampliado) "Ampliado" else "No ampliado")
                            merged.put("ampliadoFormateado", ampliadoFormateado)

                            merged.put("viajes", firstCard.optJSONArray("viajes") ?: JSONArray())
                            merged.put("viajes_realizados", 0)

                            return merged
                        } else {
                            val msg = dataObj.optString("mensaje", "No se encontraron títulos cargados para la tarjeta $card.")
                            throw Exception(msg)
                        }
                    }
                }
            }

            throw Exception("No se pudo obtener información de la tarjeta $card desde el servidor.")
        }
    }
}
