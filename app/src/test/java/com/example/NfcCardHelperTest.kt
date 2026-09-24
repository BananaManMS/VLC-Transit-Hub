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
}
