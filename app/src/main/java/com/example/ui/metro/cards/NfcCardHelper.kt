package com.example.ui.metro.cards

import android.app.Activity
import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV
import android.nfc.tech.IsoDep
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

object NfcCardHelper {

    private const val TAG = "NfcCardHelper"

    private var activeCallback: ((String) -> Unit)? = null
    private var activeActivity: Activity? = null

    fun isNfcSupported(context: Context): Boolean {
        val adapter = NfcAdapter.getDefaultAdapter(context)
        return adapter != null
    }

    fun isNfcEnabled(context: Context): Boolean {
        val adapter = NfcAdapter.getDefaultAdapter(context)
        return adapter != null && adapter.isEnabled
    }

    /**
     * Activa el modo lector NFC en primer plano
     */
    fun startListening(
        activity: Activity,
        onTagDetected: (rawNumber: String) -> Unit,
        onError: (String) -> Unit
    ): Boolean {
        val adapter = NfcAdapter.getDefaultAdapter(activity) ?: run {
            onError("NFC no disponible en este dispositivo")
            return false
        }

        if (!adapter.isEnabled) {
            onError("El NFC está desactivado. Actívalo en Ajustes.")
            return false
        }

        activeCallback = onTagDetected
        activeActivity = activity

        val flags = NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK

        adapter.enableReaderMode(activity, { tag ->
            val cardNumber = extractCardNumberFromTag(tag)
            activity.runOnUiThread {
                if (cardNumber != null && cardNumber.isNotBlank()) {
                    triggerHapticSuccess(activity)
                    onTagDetected(cardNumber)
                } else {
                    onError("No se pudo leer el número de la tarjeta Móbilis/SUMA. Prueba de nuevo o introdúcelo manualmente.")
                }
            }
        }, flags, null)

        return true
    }

    fun stopListening(activity: Activity) {
        try {
            activeCallback = null
            activeActivity = null
            val adapter = NfcAdapter.getDefaultAdapter(activity)
            adapter?.disableReaderMode(activity)
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling NFC reader mode", e)
        }
    }

    /**
     * Extrae el número de serie de la tarjeta Móbilis / SUMA / Valencia
     * compatible con formatos de 10 y 12 dígitos.
     */
    fun extractCardNumberFromTag(tag: Tag): String? {
        try {
            val idBytes = tag.id ?: return null
            val derived = deriveCardNumberFromUid(idBytes)
            if (derived.isNotBlank()) {
                return derived
            }
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting card number from tag", e)
            val idBytes = tag.id ?: return null
            return deriveCardNumberFromUid(idBytes)
        }
    }

    /**
     * Convierte los bytes del UID a número decimal comercial de la tarjeta Móbilis / SUMA.
     * En las tarjetas de transporte de Valencia (Mifare Classic 1k / Ultralight / ISO 14443-3A),
     * el UID (ej: CF:E2:5A:C9) se convierte en Little-Endian (C9 5A E2 CF) a decimal unsigned 32-bit:
     * 0xC95AE2CF = 3378176719 (10 dígitos impresos en la tarjeta).
     */
    fun deriveCardNumberFromUid(idBytes: ByteArray): String {
        if (idBytes.size == 4) {
            // Little-Endian (Estándar MIFARE Classic / Móbilis Valencia)
            val littleEndianLong = ((idBytes[3].toLong() and 0xFF) shl 24) or
                    ((idBytes[2].toLong() and 0xFF) shl 16) or
                    ((idBytes[1].toLong() and 0xFF) shl 8) or
                    (idBytes[0].toLong() and 0xFF)
            return String.format(java.util.Locale.US, "%010d", littleEndianLong)
        }

        // Si tiene 7 bytes (MIFARE Ultralight / DESFire) u otra longitud
        var resultLong = 0L
        for (i in idBytes.indices.reversed()) {
            resultLong = (resultLong shl 8) or (idBytes[i].toLong() and 0xFF)
            if (resultLong > 999999999999L) break
        }
        val decimalStr = resultLong.toString()
        return if (decimalStr.length in 10..12) {
            decimalStr
        } else if (decimalStr.length > 12) {
            decimalStr.take(10)
        } else {
            decimalStr.padStart(10, '0')
        }
    }

    private fun triggerHapticSuccess(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vibratorManager?.defaultVibrator
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(50)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing haptic feedback", e)
        }
    }
}
