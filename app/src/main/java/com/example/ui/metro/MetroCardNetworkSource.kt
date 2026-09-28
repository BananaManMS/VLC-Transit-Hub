package com.example.ui.metro

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
        val url = "https://metrovalencia-cloudflare-worker-api-tester-224385556854.europe-west2.run.app/v1/tarjetas/consultar"
        val apiSecret = "ApiVLCTH2584"

        val jsonPayload = JSONObject().apply {
            put("numero", card)
            put("numeroTarjeta", card)
        }.toString()

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonPayload.toRequestBody(mediaType)

        // Try POST request first
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("X-App-Secret", apiSecret)
            .addHeader("X-API-Secret", apiSecret)
            .addHeader("User-Agent", com.example.data.network.NetworkModule.USER_AGENT)
            .build()

        var responseString: String? = null
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    responseString = response.body?.string()
                }
            }
        } catch (_: Exception) { }

        // Fallback to GET with query parameter if POST didn't return a body
        if (responseString.isNullOrBlank()) {
            val getUrl = "$url?numero=$card"
            val getRequest = Request.Builder()
                .url(getUrl)
                .addHeader("X-App-Secret", apiSecret)
                .addHeader("X-API-Secret", apiSecret)
                .addHeader("User-Agent", com.example.data.network.NetworkModule.USER_AGENT)
                .build()

            client.newCall(getRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("HTTP Error: ${response.code}")
                }
                responseString = response.body?.string()
            }
        }

        val body = responseString ?: throw Exception("Respuesta vacía del servidor")
        if (body.trim().startsWith("<")) {
            throw Exception("Respuesta en formato HTML inválido desde el servidor.")
        }

        val root = JSONObject(body)
        val success = root.optBoolean("success", false)
        val dataObj = root.optJSONObject("data")

        if (success && dataObj != null) {
            val encontrada = dataObj.optBoolean("encontrada", true)
            if (!encontrada) {
                val msg = dataObj.optString("mensaje", "No se encontraron títulos cargados para la tarjeta $card.")
                throw Exception(msg)
            }

            val tarjetasArray = dataObj.optJSONArray("tarjetas")
                ?: dataObj.optJSONArray("titulos")
                ?: root.optJSONArray("tarjetas")

            if (tarjetasArray != null && tarjetasArray.length() > 0) {
                val firstCard = tarjetasArray.getJSONObject(0)
                val merged = JSONObject()

                val (title, tipo) = extractCardTitleAndClass(firstCard)
                merged.put("nombre", title)
                merged.put("titulo", title)
                merged.put("clase", tipo)
                merged.put("tipo", tipo)
                
                val rawSaldo = if (firstCard.has("saldo")) firstCard.optDouble("saldo", 0.0) else firstCard.optDouble("saldo_restante", 0.0)
                merged.put("saldo_restante", rawSaldo * 100.0)
                merged.put("viajes_restantes", rawSaldo.toInt())
                merged.put("saldo", firstCard.optString("saldoFormateado", "${rawSaldo.toInt()} viajes"))

                val zona = firstCard.optString("zona", firstCard.optString("zonas", "A"))
                val zonaFormatted = if (zona.isBlank()) "Zona A" else if (zona.startsWith("Zona", ignoreCase = true)) zona else "Zona $zona"
                merged.put("zona", zonaFormatted)
                merged.put("zonas", zonaFormatted)

                val fechaCaducidad = firstCard.optString("fechaCaducidad", firstCard.optString("caducidad", ""))
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
            } else if (dataObj.has("tarjeta")) {
                val singleObj = dataObj.getJSONObject("tarjeta")
                val merged = JSONObject()
                val (title, tipo) = extractCardTitleAndClass(singleObj)
                merged.put("nombre", title)
                merged.put("titulo", title)
                merged.put("clase", tipo)
                merged.put("tipo", tipo)
                val rawSaldo = singleObj.optDouble("saldo", 0.0)
                merged.put("saldo", singleObj.optString("saldoFormateado", "${rawSaldo.toInt()} viajes"))
                return merged
            } else {
                val msg = dataObj.optString("mensaje", "No se encontraron títulos cargados para la tarjeta $card.")
                throw Exception(msg)
            }
        } else if (dataObj != null && !dataObj.optBoolean("encontrada", true)) {
            val msg = dataObj.optString("mensaje", "No se encontraron títulos cargados para la tarjeta $card.")
            throw Exception(msg)
        }

        throw Exception("No se pudo obtener información de la tarjeta $card desde el servidor.")
    }

    private fun extractCardTitleAndClass(obj: JSONObject): Pair<String, String> {
        val rawTitle = obj.optString("titulo", obj.optString("nombre", obj.optString("nombreTitulo", obj.optString("descripcion", "")))).trim()
        val rawTipo = obj.optString("tipo", obj.optString("clase", obj.optString("modalidad", ""))).trim()
        val rawPerfil = obj.optString("perfil", obj.optString("perfil_usuario", obj.optString("colectivo", ""))).trim()

        val combinedStr = "$rawTitle $rawTipo $rawPerfil".trim()
        val lower = combinedStr.lowercase()

        val detectedTitle = when {
            lower.contains("suma") && lower.contains("mensual") && lower.contains("jove") -> "SUMA Mensual Jove"
            lower.contains("suma") && lower.contains("mensual") -> "SUMA Mensual"
            lower.contains("suma") && (lower.contains("10") || lower.contains("sencillo") || lower.contains("multiviaje")) && lower.contains("jove") -> "SUMA 10 Jove"
            lower.contains("suma") && (lower.contains("10") || lower.contains("sencillo") || lower.contains("multiviaje")) -> "SUMA 10"
            lower.contains("suma") -> if (rawTitle.isNotBlank()) rawTitle else "SUMA"
            lower.contains("tuin") && lower.contains("jove") -> "TuiN Jove"
            lower.contains("tuin") || lower.contains("tui n") -> "TuiN"
            lower.contains("mobilis") || lower.contains("móbilis") -> if (rawTitle.isNotBlank()) rawTitle else "Móbilis"
            rawTitle.isNotBlank() -> rawTitle
            else -> "Tarjeta Móbilis / SUMA"
        }

        val detectedClass = when {
            rawTipo.isNotBlank() && rawPerfil.isNotBlank() && !rawTipo.lowercase().contains(rawPerfil.lowercase()) -> "$rawTipo ($rawPerfil)"
            rawTipo.isNotBlank() -> rawTipo
            lower.contains("jove") -> "Jove"
            lower.contains("mensual") -> "Abono Mensual"
            lower.contains("monedero") -> "Monedero"
            else -> "MULTIVIAJE"
        }

        return Pair(detectedTitle, detectedClass)
    }
}
