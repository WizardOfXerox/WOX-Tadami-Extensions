package eu.kanade.tachiyomi.animeextension.en.hanime

import android.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Serializable
data class HandshakePayload(
    val v: Int = 1,
    val alg: String = "AES-256-GCM",
    val iv: String,
    val tag: String,
    val data: String,
)

@Serializable
data class CsrfTokenResponse(
    @SerialName("csrf_token")
    val csrfToken: String? = null,
)

@Serializable
data class HandshakeDecrypted(
    val sources: List<HandshakeSource> = emptyList(),
)

@Serializable
data class HandshakeSource(
    val src: String? = null,
    val type: String? = null,
    val height: Int? = null,
    val width: Int? = null,
    val label: String? = null,
    val kind: String? = null,
)

object HanimeHandshakeCrypto {
    private const val KEY_STRING = "htv-insecure-handshake-v1"
    private const val AAD_STRING = "htv-insecure-v1"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val KEY_BYTES by lazy {
        MessageDigest.getInstance("SHA-256").digest(KEY_STRING.toByteArray(Charsets.UTF_8))
    }

    private val AAD_BYTES by lazy {
        AAD_STRING.toByteArray(Charsets.UTF_8)
    }

    fun encryptHandshake(slug: String): String {
        val now = System.currentTimeMillis() / 1000L
        val plaintext = """{"timestamp_unix":$now,"directive":"htv_player_handshake","slug":"$slug"}"""

        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(KEY_BYTES, "AES")
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        cipher.updateAAD(AAD_BYTES)
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val data = encrypted.copyOfRange(0, encrypted.size - 16)
        val tag = encrypted.copyOfRange(encrypted.size - 16, encrypted.size)

        val envelope = HandshakePayload(
            v = 1,
            alg = "AES-256-GCM",
            iv = Base64.encodeToString(iv, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING),
            tag = Base64.encodeToString(tag, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING),
            data = Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING),
        )
        val envelopeJson = json.encodeToString(envelope)
        return Base64.encodeToString(envelopeJson.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun decryptToken(token: String): HandshakeDecrypted? {
        return try {
            val envelopeJsonBytes = Base64.decode(token, Base64.URL_SAFE)
            val envelope = json.decodeFromString<HandshakePayload>(String(envelopeJsonBytes, Charsets.UTF_8))

            val iv = Base64.decode(envelope.iv, Base64.URL_SAFE)
            val tag = Base64.decode(envelope.tag, Base64.URL_SAFE)
            val data = Base64.decode(envelope.data, Base64.URL_SAFE)

            val ciphertextWithTag = ByteArray(data.size + tag.size)
            System.arraycopy(data, 0, ciphertextWithTag, 0, data.size)
            System.arraycopy(tag, 0, ciphertextWithTag, data.size, tag.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(KEY_BYTES, "AES")
            val gcmSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
            cipher.updateAAD(AAD_BYTES)
            val decryptedBytes = cipher.doFinal(ciphertextWithTag)
            json.decodeFromString<HandshakeDecrypted>(String(decryptedBytes, Charsets.UTF_8))
        } catch (_: Exception) {
            null
        }
    }
}
