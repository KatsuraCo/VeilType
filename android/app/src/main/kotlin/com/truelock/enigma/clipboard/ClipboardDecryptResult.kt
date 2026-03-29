package com.truelock.enigma.clipboard

sealed class ClipboardDecryptResult {
    data object ClipboardEmpty : ClipboardDecryptResult()
    data object MessageNotRecognized : ClipboardDecryptResult()
    data object WrongKeyOrInvalidMessage : ClipboardDecryptResult()
    data class Success(
        val plaintext: String,
        val profileTitle: String,
    ) : ClipboardDecryptResult()
}
