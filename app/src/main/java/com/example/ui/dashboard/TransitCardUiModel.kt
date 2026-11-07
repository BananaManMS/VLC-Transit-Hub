package com.example.ui.dashboard

import androidx.compose.runtime.Immutable
import com.example.data.database.TransitCardEntity

@Immutable
data class TransitCardUiModel(
    val entity: TransitCardEntity,
    val cardNumber: String,
    val assignedName: String,
    val defaultName: String,
    val cardType: String,
    val remainingValue: String,
    val detailsJson: String,
    val isFaded: Boolean,
    val isManuallyInactive: Boolean,
    val showOnHome: Boolean = true,
    val customOrder: Int = 0,
    val category: String,
    val title: String,
    val clase: String,
    val operador: String,
    val zonas: String,
    val ampliado: String,
    val fechaCaducidad: String,
    val fechaRecarga: String,
    val isCurrentlyActive: Boolean,
    val viajesList: List<TransitTripUiModel>
)

@Immutable
data class TransitTripUiModel(
    val estacion: String,
    val fecha: String,
    val tipoValidacion: String,
    val zona: String
)
