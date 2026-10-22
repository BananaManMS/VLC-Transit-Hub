package com.example.data.model.announcement

import java.io.Serializable

data class RemoteAnnouncement(
    val id: String,
    val enabled: Boolean = true,
    val type: String = "azul", // "rojo", "naranja", "azul", "verde"
    val endDate: String? = null, // "YYYY-MM-DD"
    val titleEs: String,
    val titleCa: String,
    val messageEs: String,
    val messageCa: String,
    val imageUrl: String? = null,
    val actionUrl: String? = null,
    val actionTextEs: String? = null,
    val actionTextCa: String? = null,
    val repetir: Boolean = true,
    val oculto: Boolean = false
) : Serializable
