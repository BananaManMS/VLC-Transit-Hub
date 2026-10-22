package com.example.ui.metro.cards

import android.content.Context
import com.example.ui.dashboard.AppLanguage
import com.example.ui.dashboard.TransitCardUiModel
import com.example.ui.metro.MetroMapper
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object TransitCardAlertManager {

    private const val PREFS_NAME = "transit_card_alerts_prefs"
    private const val KEY_MUTED_CARDS = "muted_cards_set"
    private const val KEY_LAST_POPUP_TIME = "last_popup_time"
    private const val PREFIX_LAST_FINGERPRINT = "last_fp_"
    private const val COOLDOWN_MILLIS = 24 * 60 * 60 * 1000L // 24 horas

    fun isCardMuted(context: Context, cardNumber: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val mutedSet = prefs.getStringSet(KEY_MUTED_CARDS, emptySet()) ?: emptySet()
        return mutedSet.contains(cardNumber)
    }

    fun setCardMuted(context: Context, cardNumber: String, isMuted: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_MUTED_CARDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (isMuted) {
            current.add(cardNumber)
        } else {
            current.remove(cardNumber)
        }
        prefs.edit().putStringSet(KEY_MUTED_CARDS, current).apply()
    }

    fun evaluateCardAlert(card: TransitCardUiModel, appLanguage: AppLanguage): TransitCardAlert? {
        if (card.isManuallyInactive) return null

        val detailsObj = try { JSONObject(card.detailsJson) } catch (e: Exception) { JSONObject() }
        val meta = CardMetadata.create(card.defaultName, detailsObj, card.cardType)
        val isCa = appLanguage == AppLanguage.CA
        val displayName = card.assignedName.ifBlank { card.defaultName }

        // 1. Títulos Monedero (TuiN) con menos de 2,00 €
        if (meta.isTuiN || card.cardType.equals("tuin", ignoreCase = true)) {
            val cleanStr = card.remainingValue.replace("€", "").replace(" ", "").replace(",", ".").trim()
            val balance = cleanStr.toDoubleOrNull() ?: 0.0
            if (balance < 2.00) {
                val formattedBalance = String.format(Locale.US, "%.2f", balance).replace('.', ',') + " €"
                val subtitle = if (isCa) {
                    "Saldo disponible: $formattedBalance (< 2,00 €)"
                } else {
                    "Saldo disponible: $formattedBalance (< 2,00 €)"
                }
                val fp = "${card.cardNumber}_tuin_${(balance * 100).toInt()}"
                return TransitCardAlert(
                    cardNumber = card.cardNumber,
                    cardName = displayName,
                    alertType = TransitCardAlertType.LOW_BALANCE,
                    titleText = if (isCa) "Saldo baix" else "Saldo bajo",
                    subtitleText = subtitle,
                    stateFingerprint = fp,
                    isCritical = balance <= 0.0
                )
            }
            return null
        }

        // 2. Títulos Mensuales / Temporales (SUMA Mensual, Jove, etc.) con menos de 3 días o caducados
        if (meta.isMonthly || card.cardType.equals("mensual", ignoreCase = true)) {
            if (card.fechaCaducidad.isBlank() || card.fechaCaducidad.equals("Sin recarga activa", ignoreCase = true)) {
                return null
            }
            val parsedCadDate = MetroMapper.parseDateDefensively(card.fechaCaducidad)
            if (parsedCadDate != null) {
                val today = Calendar.getInstance()
                today.set(Calendar.HOUR_OF_DAY, 0)
                today.set(Calendar.MINUTE, 0)
                today.set(Calendar.SECOND, 0)
                today.set(Calendar.MILLISECOND, 0)

                val expCal = Calendar.getInstance()
                expCal.time = parsedCadDate
                expCal.set(Calendar.HOUR_OF_DAY, 23)
                expCal.set(Calendar.MINUTE, 59)
                expCal.set(Calendar.SECOND, 59)
                expCal.set(Calendar.MILLISECOND, 999)

                val diffMillis = expCal.timeInMillis - today.timeInMillis
                val diffDays = (diffMillis / (1000 * 60 * 60 * 24)).toInt()

                if (diffDays < 0 || today.after(expCal)) {
                    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    val formatted = sdf.format(parsedCadDate)
                    return TransitCardAlert(
                        cardNumber = card.cardNumber,
                        cardName = displayName,
                        alertType = TransitCardAlertType.EXPIRED,
                        titleText = if (isCa) "Títol caducat" else "Título caducado",
                        subtitleText = if (isCa) "Va caducar el $formatted" else "Caducó el $formatted",
                        stateFingerprint = "${card.cardNumber}_monthly_expired",
                        isCritical = true
                    )
                } else if (diffDays in 0..3) {
                    val subtitle = when (diffDays) {
                        0 -> if (isCa) "Caduca hui" else "Caduca hoy"
                        1 -> if (isCa) "Caduca demà" else "Caduca mañana"
                        else -> if (isCa) "Caduca en $diffDays dies" else "Caduca en $diffDays días"
                    }
                    return TransitCardAlert(
                        cardNumber = card.cardNumber,
                        cardName = displayName,
                        alertType = TransitCardAlertType.EXPIRING_SOON,
                        titleText = if (isCa) "Pròxima caducitat" else "Próxima caducidad",
                        subtitleText = subtitle,
                        stateFingerprint = "${card.cardNumber}_monthly_${diffDays}d",
                        isCritical = diffDays <= 1
                    )
                }
            }
            return null
        }

        // 3. Títulos Multi-viaje (SUMA 10, Bonometro, etc.) con menos de 2 validaciones
        val digits = card.remainingValue.filter { it.isDigit() }.toIntOrNull()
        if (digits != null && digits <= 2) {
            val title = when (digits) {
                0 -> if (isCa) "Sense viatges" else "Sin viajes"
                1 -> if (isCa) "Últim viatge" else "Último viaje"
                else -> if (isCa) "Pocs viatges" else "Pocos viajes"
            }
            val subtitle = when (digits) {
                0 -> if (isCa) "No queden viatges a la targeta" else "No quedan viajes en la tarjeta"
                1 -> if (isCa) "Queda només 1 viatge restant" else "Queda solo 1 viaje restante"
                else -> if (isCa) "Queden 2 viatges restants" else "Quedan 2 viajes restantes"
            }
            return TransitCardAlert(
                cardNumber = card.cardNumber,
                cardName = displayName,
                alertType = TransitCardAlertType.LOW_TRIPS,
                titleText = title,
                subtitleText = subtitle,
                stateFingerprint = "${card.cardNumber}_trips_$digits",
                isCritical = digits == 0
            )
        }

        return null
    }

    fun getAlertsForPopup(
        context: Context,
        cards: List<TransitCardUiModel>,
        appLanguage: AppLanguage
    ): List<TransitCardAlert> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val mutedSet = prefs.getStringSet(KEY_MUTED_CARDS, emptySet()) ?: emptySet()

        val allCurrentAlerts = cards.mapNotNull { evaluateCardAlert(it, appLanguage) }
            .filter { !mutedSet.contains(it.cardNumber) }

        if (allCurrentAlerts.isEmpty()) {
            return emptyList()
        }

        // Check fingerprints: is there any alert whose state has changed or hasn't been acknowledged yet?
        val unseenAlerts = allCurrentAlerts.filter { alert ->
            val lastFp = prefs.getString(PREFIX_LAST_FINGERPRINT + alert.cardNumber, null)
            lastFp != alert.stateFingerprint
        }

        if (unseenAlerts.isEmpty()) {
            // All active card alerts have already been shown for their exact current state/balance.
            return emptyList()
        }

        val lastPopupTime = prefs.getLong(KEY_LAST_POPUP_TIME, 0L)
        val now = System.currentTimeMillis()
        val isCooldownActive = (now - lastPopupTime) < COOLDOWN_MILLIS

        // If cooldown is active, only show if there is a brand new card entering an alert state for the first time ever
        if (isCooldownActive) {
            val hasBrandNewAlert = unseenAlerts.any { alert ->
                !prefs.contains(PREFIX_LAST_FINGERPRINT + alert.cardNumber)
            }
            if (!hasBrandNewAlert) {
                return emptyList()
            }
        }

        return unseenAlerts
    }

    fun markAlertsAsSeen(context: Context, alerts: List<TransitCardAlert>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putLong(KEY_LAST_POPUP_TIME, System.currentTimeMillis())
        for (alert in alerts) {
            editor.putString(PREFIX_LAST_FINGERPRINT + alert.cardNumber, alert.stateFingerprint)
        }
        editor.apply()
    }
}
