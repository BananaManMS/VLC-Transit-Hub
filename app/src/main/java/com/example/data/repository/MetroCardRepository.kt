package com.example.data.repository

import android.content.Context
import com.example.data.database.AppDatabase
import com.example.data.database.TransitCardEntity
import com.example.data.database.DatabaseBackupManager
import com.example.ui.metro.MetroCardNetworkSource
import com.example.ui.metro.MetroMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject

class MetroCardRepository(
    private val context: Context,
    private val database: AppDatabase
) {
    private val client = com.example.data.network.NetworkModule.okHttpClient.newBuilder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    val transitCardsFlow: Flow<List<TransitCardEntity>> = database.transitCardDao().getAllCardsFlow()

    suspend fun getCardDetails(cardNumber: String, client: OkHttpClient): JSONObject = withContext(Dispatchers.IO) {
        val trimmedCard = cardNumber.filter { it.isDigit() }
        when (trimmedCard) {
            "111111111111" -> JSONObject().apply {
                put("tarjeta", "111111111111")
                put("nombre", "SUMA")
                put("clase", "Ordinaria")
                put("titulo", "SUMA 10")
                put("viajes_restantes", 7)
                put("viajes_realizados", 14)
                put("tipo", "viajes")
                put("operador", "Metrovalencia")
                put("zonas", "A, B")
            }
            "222222222222" -> JSONObject().apply {
                put("tarjeta", "222222222222")
                put("nombre", "Móbilis")
                put("clase", "Jove")
                put("titulo", "TuiN")
                put("saldo_restante", 298)
                put("viajes_realizados", 45)
                put("tipo", "saldo")
                put("operador", "Metrovalencia")
                put("zonas", "A, B, C, D")
            }
            "333333333333" -> JSONObject().apply {
                put("tarjeta", "333333333333")
                put("nombre", "SUMA")
                put("clase", "Adulto")
                put("titulo", "SUMA Mensual")
                put("fecha", "17/07/2026")
                put("recargado", 1)
                put("fecha_renovacion", "15/08/2026")
                put("viajes_realizados", 32)
                put("tipo", "mensual")
                put("operador", "Metrovalencia")
                put("zonas", "A")
                put("ampliado", "S")
            }
            else -> {
                MetroCardNetworkSource.fetchCardFromNetwork(trimmedCard, client)
            }
        }
    }

    suspend fun updateTransitCardName(cardNumber: String, newName: String) = withContext(Dispatchers.IO) {
        val dao = database.transitCardDao()
        val existing = dao.getCardByNumber(cardNumber)
        if (existing != null) {
            dao.insertCard(existing.copy(assignedName = newName.trim(), lastUpdated = System.currentTimeMillis()))
            DatabaseBackupManager.backupTransitCards(context, database)
        }
    }

    suspend fun updateTransitCardManualStatus(cardNumber: String, isManuallyInactive: Boolean) = withContext(Dispatchers.IO) {
        val dao = database.transitCardDao()
        val existing = dao.getCardByNumber(cardNumber)
        if (existing != null) {
            try {
                val obj = JSONObject(existing.detailsJson)
                obj.put("manually_inactive", isManuallyInactive)
                dao.insertCard(existing.copy(detailsJson = obj.toString(), lastUpdated = System.currentTimeMillis()))
                DatabaseBackupManager.backupTransitCards(context, database)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    suspend fun deleteTransitCard(cardNumber: String) = withContext(Dispatchers.IO) {
        database.transitCardDao().deleteCardByNumber(cardNumber)
        DatabaseBackupManager.backupTransitCards(context, database)
    }

    suspend fun addTransitCard(
        cardNumber: String,
        customName: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val trimmedCard = cardNumber.filter { it.isDigit() }
        if (trimmedCard.length != 10 && trimmedCard.length != 12) {
            return@withContext Result.failure(IllegalArgumentException("Por favor, introduce un número de tarjeta válido de 10 o 12 dígitos."))
        }

        try {
            val json = getCardDetails(trimmedCard, client)

            val defaultName = json.optString("nombre", "").ifEmpty {
                json.optString("titulo", "").ifEmpty { "Móbilis" }
            }

            val defaultNameLower = defaultName.lowercase()
            val classLower = json.optString("clase", "").lowercase()
            val titleLower = json.optString("titulo", "").lowercase()

            val isTuiN = defaultNameLower.contains("tuin") || defaultNameLower.contains("monedero") ||
                        classLower.contains("tuin") || classLower.contains("monedero") ||
                        titleLower.contains("tuin") || titleLower.contains("monedero")

            val isMonthly = (defaultNameLower.contains("mensual") || defaultNameLower.contains("men.") || defaultNameLower.contains("abono") || defaultNameLower.contains("jove") || defaultNameLower.contains("limitado") ||
                            classLower.contains("mensual") || classLower.contains("men.") || classLower.contains("abono") || classLower.contains("jove") || classLower.contains("limitado") ||
                            titleLower.contains("mensual") || titleLower.contains("men.") || titleLower.contains("abono") || titleLower.contains("jove") || titleLower.contains("limitado")) && !isTuiN

            val cardType = if (isTuiN) {
                "saldo"
            } else if (isMonthly) {
                "mensual"
            } else {
                "viajes"
            }

            val remainingValue = MetroMapper.getRemainingValueForCard(defaultName, json, cardType)

            val entity = TransitCardEntity(
                cardNumber = trimmedCard,
                assignedName = if (!customName.isNullOrBlank()) customName.trim() else defaultName,
                defaultName = defaultName,
                cardType = cardType,
                remainingValue = remainingValue,
                detailsJson = json.toString(),
                lastUpdated = System.currentTimeMillis()
            )

            database.transitCardDao().insertCard(entity)
            DatabaseBackupManager.backupTransitCards(context, database)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("La tarjeta no existe o ha ocurrido un error. Por favor, verifica el número e inténtalo de nuevo.", e))
        }
    }

    suspend fun refreshTransitCards() = withContext(Dispatchers.IO) {
        try {
            val cards = database.transitCardDao().getAllCards()
            for (card in cards) {
                val trimmedCard = card.cardNumber
                try {
                    val merged = getCardDetails(trimmedCard, client)
                    val defaultName = merged.optString("nombre", "").ifEmpty {
                        merged.optString("titulo", "").ifEmpty { "Móbilis" }
                    }

                    val defaultNameLower = defaultName.lowercase()
                    val classLower = merged.optString("clase", "").lowercase()
                    val titleLower = merged.optString("titulo", "").lowercase()

                    val isTuiN = defaultNameLower.contains("tuin") || defaultNameLower.contains("monedero") ||
                                classLower.contains("tuin") || classLower.contains("monedero") ||
                                titleLower.contains("tuin") || titleLower.contains("monedero")

                    val isMonthly = (defaultNameLower.contains("mensual") || defaultNameLower.contains("men.") || defaultNameLower.contains("abono") || defaultNameLower.contains("jove") || defaultNameLower.contains("limitado") ||
                                    classLower.contains("mensual") || classLower.contains("men.") || classLower.contains("abono") || classLower.contains("jove") || classLower.contains("limitado") ||
                                    titleLower.contains("mensual") || titleLower.contains("men.") || titleLower.contains("abono") || titleLower.contains("jove") || titleLower.contains("limitado")) && !isTuiN

                    val cardType = if (isTuiN) {
                        "saldo"
                    } else if (isMonthly) {
                        "mensual"
                    } else {
                        "viajes"
                    }

                    val remainingValue = MetroMapper.getRemainingValueForCard(defaultName, merged, cardType)

                    val updatedEntity = card.copy(
                        defaultName = defaultName,
                        cardType = cardType,
                        remainingValue = remainingValue,
                        detailsJson = merged.toString(),
                        lastUpdated = System.currentTimeMillis()
                    )
                    database.transitCardDao().insertCard(updatedEntity)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            DatabaseBackupManager.backupTransitCards(context, database)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
