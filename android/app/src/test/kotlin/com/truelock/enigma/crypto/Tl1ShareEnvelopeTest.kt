package com.truelock.enigma.crypto

import com.truelock.enigma.sharing.ShareInviteMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Tl1ShareEnvelopeTest {
    @Test
    fun wrap_formatsAggressiveInviteAboveCiphertext() {
        val wrapped = Tl1ShareEnvelope.wrap(
            encodedMessage = "TL1:abc123_xyz",
            mode = ShareInviteMode.VIRAL,
            strings = Tl1ShareEnvelope.Strings(
                lockedLine = "\uD83D\uDD10 This message is locked with VeilType",
                installLine = "Install to open: https://veiltype.app",
                balancedLine = "\uD83D\uDD10 Open in VeilType: https://veiltype.app",
            ),
        )

        assertEquals(
            "\uD83D\uDD10 This message is locked with VeilType\n" +
                "Install to open: https://veiltype.app\n\n" +
                "TL1:abc123_xyz",
            wrapped,
        )
    }

    @Test
    fun extractTl1Message_findsCiphertextInsideInviteWrapper() {
        val extracted = Tl1ShareEnvelope.extractTl1Message(
            "\uD83D\uDD10 This message is locked with VeilType\n" +
                "Install to open: https://veiltype.app\n\n" +
                "TL1:abc123_xyz",
        )

        assertEquals("TL1:abc123_xyz", extracted)
    }

    @Test
    fun extractTl1Message_returnsNullWhenNoCiphertextPresent() {
        assertNull(
            Tl1ShareEnvelope.extractTl1Message(
                "Install VeilType to open this private message.",
            ),
        )
    }

    @Test
    fun wrap_balancedUsesSingleInviteLine() {
        val wrapped = Tl1ShareEnvelope.wrap(
            encodedMessage = "TL1:abc123_xyz",
            mode = ShareInviteMode.BALANCED,
            strings = Tl1ShareEnvelope.Strings(
                lockedLine = "\uD83D\uDD10 This message is locked with VeilType",
                installLine = "Install to open: https://veiltype.app",
                balancedLine = "\uD83D\uDD10 Open in VeilType: https://veiltype.app",
            ),
        )

        assertEquals(
            "\uD83D\uDD10 Open in VeilType: https://veiltype.app\n\nTL1:abc123_xyz",
            wrapped,
        )
    }

    @Test
    fun wrap_minimalReturnsRawCiphertext() {
        val wrapped = Tl1ShareEnvelope.wrap(
            encodedMessage = "TL1:abc123_xyz",
            mode = ShareInviteMode.MINIMAL,
            strings = Tl1ShareEnvelope.Strings(
                lockedLine = "\uD83D\uDD10 This message is locked with VeilType",
                installLine = "Install to open: https://veiltype.app",
                balancedLine = "\uD83D\uDD10 Open in VeilType: https://veiltype.app",
            ),
        )

        assertEquals("TL1:abc123_xyz", wrapped)
    }
}
