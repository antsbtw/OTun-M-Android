package com.situstechnologies.OXray.data.crypto

import android.util.Base64
import android.util.Log
import com.situstechnologies.OXray.data.config.CompactShareableConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 与 iOS 端 EncryptedSharePayload 完全对齐的数据结构
 *
 * Swift:
 * struct EncryptedSharePayload: Codable {
 *   let encryptedData: String
 *   let salt: String
 *   let nonce: String
 *   let authTag: String
 *   let version: String
 *   let timestamp: TimeInterval
 * }
 */
@Serializable
data class EncryptedSharePayload(
    @SerialName("encryptedData")
    val encryptedData: String,   // Base64(ciphertext)

    @SerialName("salt")
    val salt: String,            // Base64(salt)

    @SerialName("nonce")
    val nonce: String,           // Base64(nonce / IV)

    @SerialName("authTag")
    val authTag: String,         // Base64(GCM tag)

    @SerialName("version")
    val version: String,         // "1.0"

    @SerialName("timestamp")
    val timestamp: Double        // Unix 秒
) {

    /**
     * 转为 URL-safe Base64 字符串，逻辑与 Swift 的 toURLSafeString 完全一致
     *
     * Swift:
     * jsonData.base64EncodedString()
     *   .replacingOccurrences(of: "+", with: "-")
     *   .replacingOccurrences(of: "/", with: "_")
     *   .replacingOccurrences(of: "=", with: "")
     */
    fun toUrlSafeString(): String {
        val jsonString = CryptoJson.encodeToString(this)
        val jsonData = jsonString.toByteArray(Charsets.UTF_8)
        val base64 = Base64.encodeToString(jsonData, Base64.NO_WRAP)

        return base64
            .replace("+", "-")
            .replace("/", "_")
            .replace("=", "")
    }

    companion object {
        private const val TAG = "EncryptedSharePayload"

        /**
         * 从 URL-safe 字符串恢复 payload
         *
         * 完全按照 Swift:
         *  1. -/_ -> +/
         *  2. 补齐 = padding
         *  3. Base64 解码
         *  4. JSON 解码
         *
         * 额外处理：
         *  - Android URL 把 '+' 变成 ' ' 的情况，先还原 ' ' -> '+'
         */
        fun fromUrlSafeString(urlSafeString: String): EncryptedSharePayload {
            Log.d(TAG, "Parsing encrypted payload from URL-safe string")

            // 1) 恢复 URL 中可能被转成空格的 '+'
            var fixed = urlSafeString.replace(" ", "+")

            // 2) URL-safe Base64 -> 标准 Base64
            fixed = fixed
                .replace("-", "+")
                .replace("_", "/")

            // 3) 补齐 Base64 padding
            val padLength = (4 - fixed.length % 4) % 4
            fixed += "=".repeat(padLength)

            // 4) 解码为 JSON
            val jsonData = Base64.decode(fixed, Base64.NO_WRAP)
            val jsonString = String(jsonData, Charsets.UTF_8)
            Log.d(TAG, "Decoded JSON from QR:\n$jsonString")

            return CryptoJson.decodeFromString(jsonString)
        }
    }
}

/**
 * 加解密错误，与 Swift ConfigurationCryptoError 对齐
 */
sealed class ConfigurationCryptoError : Exception() {
    object EncryptionFailed : ConfigurationCryptoError() {
        override val message = "Configuration encryption failed"
    }

    object DecryptionFailed : ConfigurationCryptoError() {
        override val message = "Configuration decryption failed"
    }

    object InvalidPassword : ConfigurationCryptoError() {
        override val message = "Invalid share password"
    }

    object ExpiredConfig : ConfigurationCryptoError() {
        override val message = "Configuration has expired"
    }

    object InvalidFormat : ConfigurationCryptoError() {
        override val message = "Invalid configuration format"
    }

    object CorruptedData : ConfigurationCryptoError() {
        override val message = "Configuration data is corrupted"
    }
}

/**
 * 统一 Json 配置，和 Swift 端 JSONEncoder/Decoder 的行为尽量一致
 */
internal val CryptoJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
}

/**
 * 配置加解密实现
 *
 * 对齐 Swift ConfigurationCrypto:
 * - AES-256-GCM
 * - HKDF<SHA256>(passwordData, salt, 32 bytes)
 * - nonce: 12 bytes
 * - tag: 16 bytes
 */
object ConfigurationCrypto {

    private const val TAG = "ConfigurationCrypto"

    private const val AES_KEY_SIZE = 32        // 256 bits
    private const val GCM_NONCE_SIZE = 12      // 96 bits
    private const val GCM_TAG_SIZE = 16        // 128 bits
    private const val SALT_SIZE = 32           // 256 bits
    private const val ENCRYPTION_VERSION = "1.0"

    // region 公共方法（与 Swift API 对齐）

    /**
     * 加密 CompactShareableConfig，用于二维码/链接分享
     * 等价于 Swift: ConfigurationCrypto.encryptConfig(_:password:)
     */
    fun encryptConfig(
        config: CompactShareableConfig,
        password: String
    ): EncryptedSharePayload {
        Log.i(TAG, "Starting configuration encryption process")

        try {
            // 1. 编码为 JSON
            val jsonString = CryptoJson.encodeToString(config)
            val jsonData = jsonString.toByteArray(Charsets.UTF_8)
            Log.d(TAG, "JSON encoding successful, size: ${jsonData.size} bytes")
            Log.d(TAG, "ORIGINAL Config JSON:\n$jsonString")

            // 2. 生成 salt & nonce
            val salt = randomBytes(SALT_SIZE)
            val nonce = randomBytes(GCM_NONCE_SIZE)

            // 3. HKDF 派生 AES-256 key
            val keyBytes = deriveKey(password, salt)
            val keySpec = SecretKeySpec(keyBytes, "AES")
            Log.d(TAG, "Key derivation successful")

            // 4. AES-GCM 加密
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(GCM_TAG_SIZE * 8, nonce)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)

            val encrypted = cipher.doFinal(jsonData)
            // Swift: ciphertext & tag 分开存
            val ciphertext = encrypted.copyOfRange(0, encrypted.size - GCM_TAG_SIZE)
            val tag = encrypted.copyOfRange(encrypted.size - GCM_TAG_SIZE, encrypted.size)

            Log.d(TAG, "AES-GCM encryption successful")

            // 5. 构造 payload
            val payload = EncryptedSharePayload(
                encryptedData = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
                salt = Base64.encodeToString(salt, Base64.NO_WRAP),
                nonce = Base64.encodeToString(nonce, Base64.NO_WRAP),
                authTag = Base64.encodeToString(tag, Base64.NO_WRAP),
                version = ENCRYPTION_VERSION,
                timestamp = System.currentTimeMillis() / 1000.0
            )

            Log.d(TAG, "FULL Encrypted Payload JSON:\n${CryptoJson.encodeToString(payload)}")
            Log.i(TAG, "Configuration encryption completed successfully")
            return payload

        } catch (e: Exception) {
            Log.e(TAG, "Encryption error: ${e.message}", e)
            throw ConfigurationCryptoError.EncryptionFailed
        }
    }

    /**
     * 解密配置，返回 CompactShareableConfig
     * 等价于 Swift: ConfigurationCrypto.decryptConfig(_:password:)
     */
    fun decryptConfig(
        payload: EncryptedSharePayload,
        password: String
    ): CompactShareableConfig {
        Log.i(TAG, "Starting configuration decryption process")
        Log.d(TAG, "Payload version: ${payload.version}")

        try {
            if (payload.version != ENCRYPTION_VERSION) {
                Log.w(TAG, "Unsupported encryption version: ${payload.version}")
                throw ConfigurationCryptoError.InvalidFormat
            }

            Log.d(TAG, "=== DECRYPTION DEBUG INFO ===")
            Log.d(TAG, "Password: '${password}' (length: ${password.length})")

            val saltBytes = Base64.decode(payload.salt, Base64.NO_WRAP)
            val nonceBytes = Base64.decode(payload.nonce, Base64.NO_WRAP)
            val cipherBytes = Base64.decode(payload.encryptedData, Base64.NO_WRAP)
            val tagBytes = Base64.decode(payload.authTag, Base64.NO_WRAP)

            Log.d(TAG, "Salt (hex): ${saltBytes.toHexString()}")
            Log.d(TAG, "Nonce (hex): ${nonceBytes.toHexString()}")
            Log.d(TAG, "EncryptedData length: ${cipherBytes.size} bytes")
            Log.d(TAG, "AuthTag (hex): ${tagBytes.toHexString()}")

            // 🔍 添加：在密钥派生前后都打印
            Log.d(TAG, ">>> About to derive key from password...")
            val keyBytes = deriveKey(password, saltBytes)
            Log.d(TAG, ">>> Key derivation completed")
            Log.d(TAG, "Derived Key (hex): ${keyBytes.toHexString()}")
            Log.d(TAG, "===========================")

            // 🔍 添加：验证参数长度
            Log.d(TAG, ">>> Preparing cipher...")
            Log.d(TAG, "Key length: ${keyBytes.size} bytes (expected: 32)")
            Log.d(TAG, "Nonce length: ${nonceBytes.size} bytes (expected: 12)")
            Log.d(TAG, "Tag length: ${tagBytes.size} bytes (expected: 16)")

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(GCM_TAG_SIZE * 8, nonceBytes)
            val keySpec = SecretKeySpec(keyBytes, "AES")

            Log.d(TAG, ">>> Initializing cipher...")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
            Log.d(TAG, ">>> Cipher initialized successfully")

            // Swift: open( nonce, ciphertext, tag )
            val combined = cipherBytes + tagBytes
            Log.d(TAG, ">>> About to decrypt, combined length: ${combined.size}")
            Log.d(TAG, ">>> Combined = Cipher(${cipherBytes.size}) + Tag(${tagBytes.size})")

            val decrypted = cipher.doFinal(combined)
            val decryptedString = String(decrypted, Charsets.UTF_8)
            Log.d(TAG, "✅ AES-GCM decryption successful (Size: ${decrypted.size} bytes)")
            Log.d(TAG, "RAW Decrypted String:\n$decryptedString")

            val config = CryptoJson.decodeFromString<CompactShareableConfig>(decryptedString)

            if (config.isExpired) {
                Log.w(TAG, "Configuration has expired")
                throw ConfigurationCryptoError.ExpiredConfig
            }

            Log.i(TAG, "Configuration decryption completed successfully")
            return config

        } catch (e: ConfigurationCryptoError) {
            throw e
        } catch (e: AEADBadTagException) {
            Log.e(TAG, "❌ AEADBadTagException: ${e.message}")
            Log.e(TAG, "This usually means: password is wrong OR crypto implementation mismatch")
            throw ConfigurationCryptoError.InvalidPassword
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "IllegalArgumentException: ${e.message}", e)
            throw ConfigurationCryptoError.InvalidFormat
        } catch (e: Exception) {
            Log.e(TAG, "Decryption error: ${e.message}", e)
            Log.e(TAG, "Exception type: ${e::class.simpleName}")
            throw ConfigurationCryptoError.DecryptionFailed
        }
    }

    /**
     * 纯字符串加解密，与 Swift encryptString/decryptString 对齐
     */
    /*
    fun encryptString(content: String, password: String): EncryptedSharePayload {
        val pseudoConfig = CompactShareableConfig.rawString(content)
        return encryptConfig(pseudoConfig, password)
    }

    fun decryptString(payload: EncryptedSharePayload, password: String): String {
        val config = decryptConfig(payload, password)
        return config.rawString ?: throw ConfigurationCryptoError.InvalidFormat
    }
*/
    // endregion

    // region HKDF & helpers

    /**
     * HKDF-SHA256，从 password + salt 派生 32 字节 AES key
     *
     * 对齐 Swift:
     * HKDF<SHA256>.deriveKey(inputKeyMaterial: SymmetricKey(data: passwordData),
     *                        salt: salt,
     *                        outputByteCount: 32)
     */
    private fun deriveKey(password: String, salt: ByteArray): ByteArray {
        Log.d(TAG, "Starting HKDF key derivation")

        val passwordBytes = password.toByteArray(Charsets.UTF_8)

        // HKDF-Extract: PRK = HMAC-SHA256(salt, IKM)
        val extractMac = Mac.getInstance("HmacSHA256")
        extractMac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = extractMac.doFinal(passwordBytes)

        // HKDF-Expand: OKM = HMAC-SHA256(PRK, info || 0x01) with empty info
        val expandMac = Mac.getInstance("HmacSHA256")
        expandMac.init(SecretKeySpec(prk, "HmacSHA256"))
        // info 为空，只追加 0x01
        expandMac.update(byteArrayOf(0x01))
        val okm = expandMac.doFinal()

        val key = okm.copyOf(AES_KEY_SIZE)
        Log.d(TAG, "HKDF key derivation successful")
        return key
    }

    private fun randomBytes(size: Int): ByteArray {
        val data = ByteArray(size)
        SecureRandom().nextBytes(data)
        return data
    }

    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02x".format(it) }

    // endregion
}
