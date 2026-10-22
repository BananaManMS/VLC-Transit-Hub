package com.example

import com.example.ui.metro.cards.NfcCardHelper
import org.junit.Assert.assertEquals
import org.junit.Test

class NfcCardHelperTest {

    @Test
    fun testDeriveCardNumberFromUid_LittleEndianMifareClassic() {
        // UID from NFC Tools: CF:E2:5A:C9
        val uidBytes = byteArrayOf(
            0xCF.toByte(),
            0xE2.toByte(),
            0x5A.toByte(),
            0xC9.toByte()
        )

        val derived = NfcCardHelper.deriveCardNumberFromUid(uidBytes)
        // 0xC95AE2CF in decimal is 3378176719 (the 10-digit base code of card 337817671990)
        assertEquals("3378176719", derived)
    }

    @Test
    fun testDeriveCardNumberFromUid_SevenBytes() {
        val uidBytes = byteArrayOf(
            0x04.toByte(), 0x12.toByte(), 0x34.toByte(), 0x56.toByte(),
            0x78.toByte(), 0x9A.toByte(), 0xBC.toByte()
        )
        val derived = NfcCardHelper.deriveCardNumberFromUid(uidBytes)
        org.junit.Assert.assertTrue(derived.length in 10..12)
        org.junit.Assert.assertTrue(derived.all { it.isDigit() })
    }
}
