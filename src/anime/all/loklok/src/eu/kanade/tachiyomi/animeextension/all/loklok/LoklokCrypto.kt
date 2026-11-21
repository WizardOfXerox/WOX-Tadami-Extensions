package eu.kanade.tachiyomi.animeextension.all.loklok

import android.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

object LoklokCrypto {

    const val PUBLIC_KEY_B64 =
        "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCR0hi0Ah2EkHEL0bMsyQUPn8A1" +
            "0ZW42z9LtfSiUSMaf3lw/sfqRcMTmh4m8+sBnK2a5PWKLTG2CW/HWYydN5n1BU63" +
            "c9yYMAoUD+52usxxsMaELLOb+xEv6LRW5oquDck7ZWj0xkSfHc4UXUa1l1FwVXQS" +
            "+qFIRDJGXgPCUEGVmwIDAQAB"

    private val PUBLIC_KEY_PREFIX_16 = PUBLIC_KEY_B64.substring(0, 16)

    private val SECURE_RANDOM = SecureRandom()

    private val RSA_PUBLIC_KEY: PublicKey by lazy {
        val keyBytes = Base64.decode(PUBLIC_KEY_B64, Base64.DEFAULT)
        KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(keyBytes))
    }

    fun generateAesKey(): String {
        val bytes = ByteArray(8)
        SECURE_RANDOM.nextBytes(bytes)
        return bytes.toHexLower()
    }

    fun generateDeviceId(): String {
        return UUID.randomUUID().toString().replace("-", "").uppercase(Locale.US)
    }

    fun rsaEncryptBase64(plainText: String): String {
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, RSA_PUBLIC_KEY)
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    fun aesEncryptBase64(plainText: String, keyStr: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        val keySpec = SecretKeySpec(keyStr.toByteArray(Charsets.UTF_8), "AES")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec)
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    fun aesDecryptBase64(cipherB64: String, keyStr: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        val keySpec = SecretKeySpec(keyStr.toByteArray(Charsets.UTF_8), "AES")
        cipher.init(Cipher.DECRYPT_MODE, keySpec)
        val cipherBytes = Base64.decode(cipherB64, Base64.DEFAULT)
        return String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
    }

    fun hmacSha256HexLower(data: String, keyStr: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        val keySpec = SecretKeySpec(keyStr.toByteArray(Charsets.UTF_8), "HmacSHA256")
        mac.init(keySpec)
        return mac.doFinal(data.toByteArray(Charsets.UTF_8)).toHexLower()
    }

    fun md5HexLower(data: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(data.toByteArray(Charsets.UTF_8))
        return digest.toHexLower()
    }

    fun currentTimezoneHeader(): String {
        val offsetMs = TimeZone.getDefault().getOffset(System.currentTimeMillis())
        val sign = if (offsetMs >= 0) "+" else "-"
        val totalMinutes = abs(offsetMs) / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return String.format(Locale.US, "GMT%s%02d:%02d", sign, hours, minutes)
    }

    fun encodeComponentForSign(value: String): String {
        return URLEncoder.encode(value, "UTF-8")
            .replace("+", "%20")
            .replace("%7E", "~")
            .replace("!", "%21")
            .replace("'", "%27")
            .replace("(", "%28")
            .replace(")", "%29")
    }

    fun serializeQueryForSign(url: HttpUrl): String {
        if (url.querySize == 0) return ""
        val pairs = ArrayList<Pair<String, String>>(url.querySize)
        for (i in 0 until url.querySize) {
            val value = url.queryParameterValue(i)
            if (!value.isNullOrEmpty()) {
                pairs.add(url.queryParameterName(i) to value)
            }
        }
        return pairs
            .sortedWith(Comparator { a, b -> a.first.compareTo(b.first) })
            .joinToString("") { it.second }
    }

    fun serializeJsonBodyForSign(bodyText: String, json: Json): String {
        if (bodyText.isBlank()) return ""
        return try {
            val element = json.parseToJsonElement(bodyText)
            when (element) {
                is JsonObject -> serializeJsonObjectForSign(element)
                is JsonArray ->
                    element
                        .filterIsInstance<JsonObject>()
                        .joinToString("") { serializeJsonObjectForSign(it) }
                else -> ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun serializeJsonObjectForSign(obj: JsonObject): String {
        return obj.keys
            .sorted()
            .joinToString("") { key ->
                when (val value = obj[key]) {
                    null, JsonNull -> "null"
                    is JsonPrimitive -> {
                        if (value.isString) {
                            if (value.content.isEmpty()) "" else encodeComponentForSign(value.content)
                        } else {
                            value.toString()
                        }
                    }
                    is JsonArray -> value.joinToString("") { item ->
                        when (item) {
                            is JsonPrimitive -> if (item.isString) item.content else item.toString()
                            is JsonObject -> serializeJsonObjectForSign(item)
                            JsonNull -> "null"
                            else -> item.toString()
                        }
                    }
                    is JsonObject -> value.toString()
                }
            }
    }

    fun computeSign(payloadStr: String, currentTime: String, aesKeyRaw: String): String {
        val encrypted = aesEncryptBase64(payloadStr + currentTime, aesKeyRaw)
        return md5HexLower(encrypted)
    }

    fun computeUserType(deviceId: String, aesKeyRaw: String): String {
        val encryptedDeviceId = aesEncryptBase64(deviceId, PUBLIC_KEY_PREFIX_16)
        return hmacSha256HexLower(encryptedDeviceId, aesKeyRaw)
    }

    fun decryptResponseIfNeeded(
        rawBody: String,
        ecyHeader: String?,
        aesKeyRaw: String,
        json: Json,
    ): String {
        if (ecyHeader != "1" || rawBody.isBlank()) return rawBody
        return try {
            val root = json.parseToJsonElement(rawBody) as? JsonObject ?: return rawBody
            val dataPrimitive = root["data"] as? JsonPrimitive ?: return rawBody
            if (!dataPrimitive.isString) return rawBody

            val decryptedJson = aesDecryptBase64(dataPrimitive.jsonPrimitive.content, aesKeyRaw)
            val decryptedElement = json.parseToJsonElement(decryptedJson)
            val rebuilt = root.toMutableMap().apply {
                put("data", decryptedElement)
            }
            JsonObject(rebuilt).toString()
        } catch (_: Exception) {
            rawBody
        }
    }

    private fun ByteArray.toHexLower(): String {
        val sb = StringBuilder(size * 2)
        for (b in this) {
            val v = b.toInt() and 0xFF
            sb.append(HEX_CHARS[v ushr 4])
            sb.append(HEX_CHARS[v and 0x0F])
        }
        return sb.toString()
    }

    private val HEX_CHARS = "0123456789abcdef".toCharArray()
}
