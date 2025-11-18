package com.situstechnologies.OXray.data.Oimport

import android.content.Context
import android.net.Uri
import android.util.Log
import com.situstechnologies.OXray.data.config.CompactShareableConfig
import com.situstechnologies.OXray.data.config.ConfigTemplateManager
import com.situstechnologies.OXray.data.crypto.ConfigurationCrypto
import com.situstechnologies.OXray.data.crypto.ConfigurationCryptoError
import com.situstechnologies.OXray.data.crypto.EncryptedSharePayload
import com.situstechnologies.OXray.data.storage.TestModeStorage
import com.situstechnologies.OXray.data.storage.TestModeRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Configuration import handler
 * Handles oxray:// URL scheme and configuration decryption/import process
 *
 * Ported from iOS ImportHandler.swift
 */
class ImportHandler(private val context: Context) {

    companion object {
        private const val TAG = "ImportHandler"

        /**
         * Quick validation of import URL format
         */
        fun isValidImportURL(urlString: String): Boolean {
            val uri = try {
                Uri.parse(urlString)
            } catch (e: Exception) {
                return false
            }

            if (uri.scheme != "oxray" && uri.scheme != "sing-box") {
                return false
            }

            if (uri.host != "import") {
                return false
            }

            val encryptedParam = uri.getQueryParameter("encrypted")
            return !encryptedParam.isNullOrEmpty()
        }
    }

    /**
     * Import result
     */
    sealed class ImportResult {
        data class Success(val profileName: String) : ImportResult()
        data class RequiresPassword(val payload: EncryptedSharePayload) : ImportResult()
        data class Failure(val error: ImportError) : ImportResult()
    }

    /**
     * Import errors
     */
    sealed class ImportError : Exception() {
        object InvalidURL : ImportError() {
            override val message = "Invalid import URL"
        }

        object InvalidScheme : ImportError() {
            override val message = "URL scheme must be oxray:// or sing-box://"
        }

        object MissingParameter : ImportError() {
            override val message = "Missing required parameter in URL"
        }

        object DecryptionFailed : ImportError() {
            override val message = "Failed to decrypt configuration. Check your password."
        }

        object ConfigurationExpired : ImportError() {
            override val message = "Configuration has expired"
        }

        object InvalidTemplate : ImportError() {
            override val message = "Invalid or unsupported configuration template"
        }

        object SaveFailed : ImportError() {
            override val message = "Failed to save configuration file"
        }

        object DatabaseError : ImportError() {
            override val message = "Failed to save configuration to database"
        }
        // 👇 添加这个
        object TestAccountAlreadyActivated : ImportError() {
            override val message = "This test account has already been activated and cannot be used again."
        }
    }

    /**
     * Parse import URL and extract encrypted payload
     */
    fun parseImportURL(urlString: String): ImportResult {
        Log.i(TAG, "Parsing import URL")

        // 1. Validate URL
        val uri = try {
            Uri.parse(urlString)
        } catch (e: Exception) {
            Log.e(TAG, "Invalid URL format")
            return ImportResult.Failure(ImportError.InvalidURL)
        }

        // 2. Check scheme
        if (uri.scheme != "oxray" && uri.scheme != "sing-box") {
            Log.e(TAG, "Invalid URL scheme: ${uri.scheme}")
            return ImportResult.Failure(ImportError.InvalidScheme)
        }

        // 3. Check host (should be "import")
        if (uri.host != "import") {
            Log.e(TAG, "Invalid URL host: ${uri.host}")
            return ImportResult.Failure(ImportError.InvalidURL)
        }

        // 4. Get encrypted parameter
        val encryptedParam = uri.getQueryParameter("encrypted")
        if (encryptedParam.isNullOrEmpty()) {
            Log.e(TAG, "Missing 'encrypted' parameter")
            return ImportResult.Failure(ImportError.MissingParameter)
        }

        Log.d(TAG, "Encrypted parameter extracted, length: ${encryptedParam.length}")

        // 5. Parse encrypted payload
        return try {
            val payload = EncryptedSharePayload.fromUrlSafeString(encryptedParam)
            Log.i(TAG, "Encrypted payload parsed successfully")
            ImportResult.RequiresPassword(payload)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse encrypted payload: ${e.message}")
            ImportResult.Failure(ImportError.InvalidURL)
        }
    }

    /**
     * Import configuration with password
     */
    suspend fun importConfiguration(
        payload: EncryptedSharePayload,
        password: String,
        onProfileCreated: suspend (String, String) -> Long
    ): ImportResult = withContext(Dispatchers.IO) {
        Log.i(TAG, "Starting configuration import process")

        try {
            // 1. Decrypt payload to get compact config
            Log.d(TAG, "Decrypting configuration...")
            val compactConfig = ConfigurationCrypto.decryptConfig(payload, password)
            Log.i(TAG, "Configuration decrypted successfully")

            // 🔍 添加调试：打印解密后的配置详情
            Log.d(TAG, "=== Compact Config Details ===")
            Log.d(TAG, "Template ID: ${compactConfig.templateId}")
            Log.d(TAG, "Display Name: ${compactConfig.displayName}")
            Log.d(TAG, "Share ID: ${compactConfig.shareId}")
            Log.d(TAG, "Created At: ${compactConfig.createdAt}")
            Log.d(TAG, "Expiration: ${compactConfig.expirationDate}")
            Log.d(TAG, "Is Expired: ${compactConfig.isExpired}")
            Log.d(TAG, "Server Params: ${compactConfig.serverParams}")
       //     Log.d(TAG, "Test Config: ${compactConfig.testConfig}")
            Log.d(TAG, "=============================")

            // 2. Check expiration
            if (compactConfig.isExpired) {
                Log.w(TAG, "Configuration has expired")
                return@withContext ImportResult.Failure(ImportError.ConfigurationExpired)
            }

        // 👇 新增：2.5. 检查测试账号防重复激活
            val testModeStorage = TestModeStorage.getInstance(context)

            if (compactConfig.testConfig != null) {
                Log.i(TAG, "🧪 Detected test account (Duration: ${compactConfig.testConfig.testDurationMinutes} minutes)")

                // 检查此 shareId 是否已激活过
                val existingRecord = testModeStorage.getRecordByShareId(compactConfig.shareId)

                if (existingRecord != null) {
                    Log.w(TAG, "❌ Test account already activated!")
                    Log.w(TAG, "   ShareID: ${compactConfig.shareId}")
                    Log.w(TAG, "   Previously activated at: ${java.util.Date(existingRecord.activatedAt)}")

                    // 👇 改为使用定义好的错误类型
                    return@withContext ImportResult.Failure(ImportError.TestAccountAlreadyActivated)
                }

                Log.i(TAG, "✅ First-time activation for this test account")
            }

            // 3. Generate full sing-box configuration from template
            Log.d(TAG, "Generating full configuration from template: ${compactConfig.templateId}")

            // 🔍 添加调试：检查模板是否存在
            try {
                val fullConfigJSON = ConfigTemplateManager.generateFullConfig(compactConfig)
                Log.i(TAG, "✅ Full configuration generated successfully")
                Log.d(TAG, "Config size: ${fullConfigJSON.length} bytes")
                Log.d(TAG, "Config preview (first 200 chars): ${fullConfigJSON.take(200)}")

                // 4. Save configuration to file
                Log.d(TAG, "Saving configuration to file...")
                val configPath = saveConfigurationToFile(fullConfigJSON, compactConfig.displayName)
                Log.i(TAG, "Configuration saved to: $configPath")

                // 5. Create Profile record
                val profileId = onProfileCreated(compactConfig.displayName, configPath)
                Log.i(TAG, "Profile created successfully with ID: $profileId")

                // 👇 修改：保存测试账号记录
                if (compactConfig.testConfig != null) {
                    val testConfig = compactConfig.testConfig
                    val now = System.currentTimeMillis()

                    val testRecord = TestModeRecord(
                        shareId = compactConfig.shareId,
                        activatedAt = now,
                        expiresAt = now + (testConfig.testDurationMinutes * 60 * 1000L),
                        configName = compactConfig.displayName,
                        testDurationMinutes = testConfig.testDurationMinutes
                    )

                    testModeStorage.saveRecord(testRecord)

                    Log.i(TAG, "🧪 Test account record saved")
                    Log.i(TAG, "   Duration: ${testConfig.testDurationMinutes} minutes")
                    Log.i(TAG, "   Expires at: ${java.util.Date(testRecord.expiresAt)}")
                    Log.i(TAG, "   ShareID locked: ${compactConfig.shareId}")
                } else {
                    Log.i(TAG, "ℹ️ Regular account (no expiration)")
                }

                return@withContext ImportResult.Success(compactConfig.displayName)

            } catch (templateError: Exception) {
                // 🔍 添加调试：捕获模板生成的具体错误
                Log.e(TAG, "❌ Template generation failed!")
                Log.e(TAG, "Error type: ${templateError::class.simpleName}")
                Log.e(TAG, "Error message: ${templateError.message}")
                Log.e(TAG, "Stack trace:", templateError)

                // 检查是否是找不到模板
                if (templateError.message?.contains("not found", ignoreCase = true) == true ||
                    templateError.message?.contains("unknown template", ignoreCase = true) == true) {
                    Log.e(TAG, "⚠️ Template '${compactConfig.templateId}' not found in ConfigTemplateManager")
                }

                return@withContext ImportResult.Failure(ImportError.InvalidTemplate)
            }

        } catch (e: ConfigurationCryptoError) {
            Log.e(TAG, "Crypto error: ${e.message}")
            val error = when (e) {
                is ConfigurationCryptoError.InvalidPassword -> ImportError.DecryptionFailed
                is ConfigurationCryptoError.ExpiredConfig -> ImportError.ConfigurationExpired
                is ConfigurationCryptoError.InvalidFormat -> ImportError.InvalidTemplate
                else -> ImportError.DecryptionFailed
            }
            return@withContext ImportResult.Failure(error)

        } catch (e: Exception) {
            Log.e(TAG, "Unknown error during import", e)
            Log.e(TAG, "Error type: ${e::class.simpleName}")
            Log.e(TAG, "Error message: ${e.message}")
            return@withContext ImportResult.Failure(ImportError.DatabaseError)
        }
    }

    /**
     * Save configuration JSON to file
     */
    private fun saveConfigurationToFile(configJSON: String, name: String): String {
        Log.d(TAG, "Preparing to save configuration file")

        // Generate unique filename
        val timestamp = System.currentTimeMillis() / 1000
        val fileName = "config_$timestamp.json"

        // Use app's files directory
        val configsDir = File(context.filesDir, "configs")
        if (!configsDir.exists()) {
            configsDir.mkdirs()
            Log.d(TAG, "Created configs directory")
        }

        // Write config file
        val configFile = File(configsDir, fileName)
        configFile.writeText(configJSON)
        Log.i(TAG, "Configuration file saved: $fileName")

        // Return absolute path
        return configFile.absolutePath
    }
}
