package com.truelock.enigma.license

import android.util.Base64
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

class LicenseVerifier {
    companion object {
        private const val PUBLIC_KEY_BASE64 = "bM+yM/7rQUFywh1rZRF+0/NlceAdlmE33UOT91K+ygQ="
    }

    fun verify(license: SignedLicense): Boolean {
        val publicKey = Base64.decode(PUBLIC_KEY_BASE64, Base64.NO_WRAP)
        if (publicKey.size != 32) return false
        val signature = Base64.decode(license.signatureBase64, Base64.NO_WRAP)
        val payloadBytes = license.payloadJson.toByteArray(Charsets.UTF_8)

        val signer = Ed25519Signer()
        signer.init(false, Ed25519PublicKeyParameters(publicKey, 0))
        signer.update(payloadBytes, 0, payloadBytes.size)
        return signer.verifySignature(signature)
    }
}
