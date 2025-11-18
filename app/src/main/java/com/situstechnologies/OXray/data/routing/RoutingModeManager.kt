package com.situstechnologies.OXray.data.routing

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File

/**
 * Routing mode manager
 * Handles switching between smart routing and global proxy
 *
 * Ported from iOS RoutingModeManager.swift
 */
class RoutingModeManager(private val context: Context) {

    /**
     * Routing mode enumeration
     */
    enum class RoutingMode(val value: String) {
        GLOBAL("global"),
        SMART("smart");

        val displayName: String
            get() = when (this) {
                GLOBAL -> "Global Proxy"
                SMART -> "Smart Split"
            }

        val description: String
            get() = when (this) {
                GLOBAL -> "All traffic goes through the proxy"
                SMART -> "Automatically split domestic and international traffic"
            }

        companion object {
            fun fromValue(value: String): RoutingMode = when (value) {
                "global" -> GLOBAL
                "smart" -> SMART
                else -> SMART
            }
        }
    }

    companion object {
        private const val TAG = "RoutingModeManager"
        private const val PREFS_NAME = "routing_mode_prefs"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    /**
     * Get current routing mode for a profile
     */
    suspend fun getCurrentMode(profileId: Long): RoutingMode = withContext(Dispatchers.IO) {
        val key = "routing_mode_$profileId"
        val modeValue = prefs.getString(key, null)

        val mode = modeValue?.let { RoutingMode.fromValue(it) } ?: RoutingMode.SMART
        Log.i(TAG, "Current mode for profile $profileId: ${mode.value}")
        return@withContext mode
    }

    /**
     * Switch routing mode for a profile
     */
    suspend fun switchMode(
        profileId: Long,
        configPath: String,
        mode: RoutingMode
    ) = withContext(Dispatchers.IO) {
        Log.i(TAG, "Switching to ${mode.value} for profile $profileId")

        // 1. Check if config file exists
        val configFile = File(configPath)
        if (!configFile.exists()) {
            Log.e(TAG, "Config file not found at: $configPath")
            throw RoutingModeError.ProfileNotFound()
        }

        Log.i(TAG, "Config file found at: $configPath")

        // 2. Read configuration
        val configJson = configFile.readText()
        val config = json.parseToJsonElement(configJson).jsonObject

        // 3. Modify route configuration
        val modifiedConfig = applyRoutingMode(config, mode)

        // 4. Save configuration
        val modifiedJson = json.encodeToString(JsonObject.serializer(), modifiedConfig)
        configFile.writeText(modifiedJson)

        // 5. Save mode selection
        val key = "routing_mode_$profileId"
        prefs.edit().putString(key, mode.value).apply()

        Log.i(TAG, "Mode switched successfully to ${mode.value}")
    }

    /**
     * Apply routing mode to configuration
     */
    private fun applyRoutingMode(config: JsonObject, mode: RoutingMode): JsonObject {
        val route = config["route"]?.jsonObject ?: return config

        val newRules = when (mode) {
            RoutingMode.GLOBAL -> {
                Log.i(TAG, "Applying global proxy rules")
                buildJsonArray {
                    // 只保留 DNS 劫持，所有流量走代理
                    add(buildJsonObject {
                        put("protocol", "dns")
                        put("action", "hijack-dns")
                    })
                }
            }

            RoutingMode.SMART -> {
                Log.i(TAG, "Applying smart routing rules")
                buildJsonArray {
                    // DNS 劫持
                    add(buildJsonObject {
                        put("protocol", "dns")
                        put("action", "hijack-dns")
                    })
                    // 国内网站直连
                    add(buildJsonObject {
                        put("rule_set", "geosite-cn")
                        put("outbound", "direct")
                    })
                    // 国内 IP 直连
                    add(buildJsonObject {
                        put("rule_set", "geoip-cn")
                        put("outbound", "direct")
                    })
                    // 私有 IP 直连
                    add(buildJsonObject {
                        put("ip_is_private", true)
                        put("outbound", "direct")
                    })
                }
            }
        }

        // 👇 关键修改：保留 route 中的所有字段，只替换 rules
        val newRoute = buildJsonObject {
            route.entries.forEach { (key, value) ->
                if (key == "rules") {
                    put(key, newRules)  // 只替换 rules
                } else {
                    put(key, value)  // 保留其他字段（final, rule_set 等）
                }
            }
        }

        return buildJsonObject {
            config.entries.forEach { (key, value) ->
                if (key == "route") {
                    put(key, newRoute)
                } else {
                    put(key, value)
                }
            }
        }
    }

    /**
     * Routing mode errors
     */
    sealed class RoutingModeError : Exception() {
        class ProfileNotFound : RoutingModeError() {
            override val message = "Configuration file not found"
        }

        class InvalidConfig : RoutingModeError() {
            override val message = "Invalid configuration format"
        }
    }
}

// Helper functions for building JSON
private fun buildJsonArray(builder: kotlinx.serialization.json.JsonArrayBuilder.() -> Unit) =
    kotlinx.serialization.json.buildJsonArray(builder)

private fun buildJsonObject(builder: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) =
    kotlinx.serialization.json.buildJsonObject(builder)
