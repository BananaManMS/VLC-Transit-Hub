package com.example.data.repository

import android.content.Context
import com.example.data.database.AppDatabase
import com.example.data.database.TransitCardEntity
import kotlinx.coroutines.flow.Flow

class MetroCardRepository(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getDatabase(context)
) {
    private val cardDao = database.transitCardDao()

    val transitCardsFlow: Flow<List<TransitCardEntity>> = cardDao.getAllCardsFlow()

    suspend fun getCards(): List<TransitCardEntity> {
        return cardDao.getAllCards()
    }

    suspend fun saveCard(card: TransitCardEntity) {
        cardDao.insertCard(card)
    }

    suspend fun updateTransitCardName(cardNumber: String, newName: String) {
        val card = cardDao.getCardByUid(cardNumber) ?: return
        cardDao.insertCard(card.copy(alias = newName))
    }

    suspend fun updateTransitCardManualStatus(cardNumber: String, isManuallyInactive: Boolean) {
        val card = cardDao.getCardByUid(cardNumber) ?: return
        val details = try { org.json.JSONObject(card.detailsJson) } catch (_: Exception) { org.json.JSONObject() }
        details.put("manually_inactive", isManuallyInactive)
        cardDao.insertCard(card.copy(detailsJson = details.toString()))
    }

    suspend fun updateCardHomeVisibility(cardNumber: String, showOnHome: Boolean) {
        val card = cardDao.getCardByUid(cardNumber) ?: return
        val details = try { org.json.JSONObject(card.detailsJson) } catch (_: Exception) { org.json.JSONObject() }
        details.put("show_on_home", showOnHome)
        cardDao.insertCard(card.copy(detailsJson = details.toString()))
    }

    suspend fun updateCardsOrder(cardNumbersInOrder: List<String>) {
        cardNumbersInOrder.forEachIndexed { index, num ->
            val card = cardDao.getCardByUid(num) ?: return@forEachIndexed
            val details = try { org.json.JSONObject(card.detailsJson) } catch (_: Exception) { org.json.JSONObject() }
            details.put("custom_order", index)
            cardDao.insertCard(card.copy(detailsJson = details.toString()))
        }
    }

    suspend fun deleteTransitCard(cardNumber: String) {
        cardDao.deleteCard(cardNumber)
    }

    suspend fun addTransitCard(cardNumber: String, customName: String?): Result<TransitCardEntity> {
        return try {
            val entity = TransitCardEntity(
                cardUid = cardNumber,
                alias = customName ?: "",
                cardType = "SUMA",
                balance = 0.0,
                remainingTrips = 10,
                expiryDate = "",
                lastSyncTimestamp = System.currentTimeMillis(),
                defaultName = "Tarjeta $cardNumber",
                remainingValue = "10 viajes",
                detailsJson = "{}"
            )
            cardDao.insertCard(entity)
            Result.success(entity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshTransitCards() {
        // Refresh cards
    }

    suspend fun refreshTransitCardsIfNeeded() {
        // Refresh if needed
    }
}
