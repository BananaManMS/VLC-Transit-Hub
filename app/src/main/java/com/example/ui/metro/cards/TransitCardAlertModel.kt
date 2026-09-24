package com.example.ui.metro.cards

enum class TransitCardAlertType {
    LOW_TRIPS,
    EXPIRING_SOON,
    EXPIRED,
    LOW_BALANCE
}

data class TransitCardAlert(
    val cardNumber: String,
    val cardName: String,
    val alertType: TransitCardAlertType,
    val titleText: String,
    val subtitleText: String,
    val stateFingerprint: String,
    val isCritical: Boolean = false
)
