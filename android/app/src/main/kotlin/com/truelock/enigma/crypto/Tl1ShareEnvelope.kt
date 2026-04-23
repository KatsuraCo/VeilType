package com.truelock.enigma.crypto

import com.truelock.enigma.sharing.ShareInviteMode

object Tl1ShareEnvelope {
    private val TL1_PATTERN = Regex("""TL1:[A-Za-z0-9_-]+""")

    data class Strings(
        val lockedLine: String,
        val installLine: String,
        val balancedLine: String,
    )

    fun wrap(
        encodedMessage: String,
        mode: ShareInviteMode,
        strings: Strings,
    ): String {
        require(encodedMessage.startsWith(Tl1MessageCodec.PREFIX)) { "Message prefix not recognized" }
        return when (mode) {
            ShareInviteMode.VIRAL -> buildString(encodedMessage.length + strings.lockedLine.length + strings.installLine.length + 4) {
                append(strings.lockedLine)
                append('\n')
                append(strings.installLine)
                append("\n\n")
                append(encodedMessage)
            }

            ShareInviteMode.BALANCED -> buildString(encodedMessage.length + strings.balancedLine.length + 2) {
                append(strings.balancedLine)
                append("\n\n")
                append(encodedMessage)
            }

            ShareInviteMode.MINIMAL -> encodedMessage
        }
    }

    fun extractTl1Message(text: String): String? = TL1_PATTERN.find(text)?.value
}
