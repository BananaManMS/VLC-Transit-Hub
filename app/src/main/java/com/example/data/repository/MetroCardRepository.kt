package com.example.data.repository

import android.content.Context
import com.example.data.database.AppDatabase
import com.example.data.database.TransitCardEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject

class MetroCardRepository(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getDatabase(context)
) {
    private val client: OkHttpClient = com.example.data.network.NetworkModule.okHttpClient
    private val cardDao = database.transitCardDao()

    @get:JvmName("getTransitCardsPropertyFlow")
    val transitCardsFlow: Flow<List<TransitCardEntity>> = cardDao.getAllCardsFlow()

    fun getTransitCardsFlow(): Flow<List<TransitCardEntity>> = transitCardsFlow

    suspend fun getCards(): List<TransitCardEntity> = withContext(Dispatchers.IO) {
        cardDao.getAllCards()
    }

    suspend fun saveCard(card: TransitCardEntity) = withContext(Dispatchers.IO) {
        cardDao.insertCard(card)
    }

    suspend fun getCardDetails(cardNumber: String, client: OkHttpClient = this.client): JSONObject? = withContext(Dispatchers.IO) {
        val card = cardDao.getCardByNumber(cardNumber) ?: return@withContext null
        try {
            JSONObject(card.detailsJson)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun updateTransitCardName(cardNumber: String, newName: String) = withContext(Dispatchers.IO) {
        val card = cardDao.getCardByNumber(cardNumber) ?: return@withContext
        cardDao.insertCard(card.copy(assignedName = newName))
    }

    suspend fun updateTransitCardManualStatus(cardNumber: String, isManuallyInactive: Boolean) = withContext(Dispatchers.IO) {
        val card = cardDao.getCardByNumber(cardNumber) ?: return@withContext
        val details = try { JSONObject(card.detailsJson) } catch (_: Exception) { JSONObject() }
        details.put("manually_inactive", isManuallyInactive)
        cardDao.insertCard(card.copy(detailsJson = details.toString()))
    }

    suspend fun updateCardHomeVisibility(cardNumber: String, showOnHome: Boolean) = withContext(Dispatchers.IO) {
        val card = cardDao.getCardByNumber(cardNumber) ?: return@withContext
        val details = try { JSONObject(card.detailsJson) } catch (_: Exception) { JSONObject() }
        details.put("show_on_home", showOnHome)
        cardDao.insertCard(card.copy(detailsJson = details.toString()))
    }

    suspend fun updateCardsOrder(cardNumbersInOrder: List<String>) = withContext(Dispatchers.IO) {
        cardNumbersInOrder.forEachIndexed { index, num ->
            val card = cardDao.getCardByNumber(num) ?: return@forEachIndexed
            val details = try { JSONObject(card.detailsJson) } catch (_: Exception) { JSONObject() }
            details.put("custom_order", index)
            cardDao.insertCard(card.copy(detailsJson = details.toString()))
        }
    }

    suspend fun deleteTransitCard(cardNumber: String) = withContext(Dispatchers.IO) {
        cardDao.deleteCardByNumber(cardNumber)
    }

    suspend fun addTransitCard(cardNumber: String, customName: String?): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val cleanCard = cardNumber.filter { it.isDigit() }
            val cardJson = com.example.ui.metro.MetroCardNetworkSource.fetchCardFromNetwork(cleanCard)
            val title = cardJson.optString("titulo", cardJson.optString("nombre", "Tarjeta $cleanCard"))
            val cardType = cardJson.optString("clase", cardJson.optString("tipo", "SUMA"))
            val remainingValue = cardJson.optString("saldo", cardJson.optString("saldoFormateado", "0 viajes"))

            val entity = TransitCardEntity(
                cardNumber = cleanCard,
                assignedName = customName ?: "",
                defaultName = title,
                cardType = cardType,
                remainingValue = remainingValue,
                lastUpdated = System.currentTimeMillis(),
                detailsJson = cardJson.toString()
            )
            cardDao.insertCard(entity)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshTransitCards() = withContext(Dispatchers.IO) {
        val cards = cardDao.getAllCards()
        for (card in cards) {
            try {
                val cardJson = com.example.ui.metro.MetroCardNetworkSource.fetchCardFromNetwork(card.cardNumber)
                val title = cardJson.optString("titulo", cardJson.optString("nombre", card.defaultName))
                val cardType = cardJson.optString("clase", cardJson.optString("tipo", card.cardType))
                val remainingValue = cardJson.optString("saldo", cardJson.optString("saldoFormateado", card.remainingValue))

                val updated = card.copy(
                    defaultName = title,
                    cardType = cardType,
                    remainingValue = remainingValue,
                    detailsJson = cardJson.toString(),
                    lastUpdated = System.currentTimeMillis()
                )
                cardDao.insertCard(updated)
            } catch (e: Exception) {
                android.util.Log.w("MetroCardRepository", "Error al actualizar tarjeta ${card.cardNumber}: ${e.message}")
            }
        }
    }

    suspend fun refreshTransitCardsIfNeeded(maxAgeMillis: Long = 3600000L): Boolean = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cards = cardDao.getAllCards()
        var refreshedAny = false
        for (card in cards) {
            if (now - card.lastUpdated > maxAgeMillis) {
                try {
                    val cardJson = com.example.ui.metro.MetroCardNetworkSource.fetchCardFromNetwork(card.cardNumber)
                    val title = cardJson.optString("titulo", cardJson.optString("nombre", card.defaultName))
                    val cardType = cardJson.optString("clase", cardJson.optString("tipo", card.cardType))
                    val remainingValue = cardJson.optString("saldo", cardJson.optString("saldoFormateado", card.remainingValue))

                    val updated = card.copy(
                        defaultName = title,
                        cardType = cardType,
                        remainingValue = remainingValue,
                        detailsJson = cardJson.toString(),
                        lastUpdated = now
                    )
                    cardDao.insertCard(updated)
                    refreshedAny = true
                } catch (e: Exception) {
                    android.util.Log.w("MetroCardRepository", "Error al actualizar tarjeta ${card.cardNumber}: ${e.message}")
                }
            }
        }
        refreshedAny
    }
}
