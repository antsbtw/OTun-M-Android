package com.situstechnologies.OXray.data.config

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import com.situstechnologies.OXray.data.crypto.ConfigurationCryptoError

/**
 * Configuration template system for sing-box
 * Provides predefined templates that can be filled with server parameters
 *
 * Ported from iOS ConfigTemplate.swift
 */
enum class ConfigTemplate(val templateId: String) {
    DEFAULT_SINGBOX("default-singbox"),
    CHINA_OPTIMIZED("china-optimized"),
    GLOBAL_OPTIMIZED("global-optimized"),
    MINIMAL("minimal");

    companion object {
        private const val TAG = "ConfigTemplate"

        fun fromId(id: String): ConfigTemplate = when (id) {
            "default-singbox" -> DEFAULT_SINGBOX
            "china-optimized" -> CHINA_OPTIMIZED
            "global-optimized" -> GLOBAL_OPTIMIZED
            "minimal" -> MINIMAL
            else -> DEFAULT_SINGBOX
        }
    }

    /**
     * Apply server parameters to template and generate full configuration
     */
    fun applyParameters(params: ServerParameters): Map<String, Any> {
        Log.i(TAG, "Applying parameters to template: $templateId")

        // 1. Get base template
        val config = getTemplateConfig().toMutableMap()

        // 2. Create outbound with server parameters
        val proxyTag = params.outboundTag
        val outbound = createOutbound(params, proxyTag)

        // 3. Set outbounds array
        config["outbounds"] = listOf(
            outbound,
            mapOf("type" to "direct", "tag" to "direct")
        )

        // 4. Replace placeholders in the template
        return replacePlaceholders(config, proxyTag)
    }

    /**
     * Get the base template configuration
     */
    private fun getTemplateConfig(): Map<String, Any> = when (this) {
        DEFAULT_SINGBOX -> getDefaultTemplate()
        CHINA_OPTIMIZED -> getChinaOptimizedTemplate()
        GLOBAL_OPTIMIZED -> getGlobalOptimizedTemplate()
        MINIMAL -> getMinimalTemplate()
    }

    /**
     * Default sing-box template with balanced settings
     */
    private fun getDefaultTemplate(): Map<String, Any> {
        return mapOf(
            "inbounds" to listOf(
                mapOf(
                    "type" to "tun",
                    "address" to listOf("198.18.0.1/16"),
                    "auto_route" to true,
                    "strict_route" to true,
                    "sniff" to true,
                    "stack" to "gvisor"
                )
            ),
            "dns" to mapOf(
                "servers" to listOf(
                    mapOf(
                        "type" to "local",
                        "tag" to "dns_resolver"
                    ),
                    mapOf(
                        "type" to "udp",
                        "tag" to "dns_proxy",
                        "server" to "8.8.8.8"
                    ),
                    mapOf(
                        "type" to "udp",
                        "tag" to "dns_direct",
                        "server" to "223.5.5.5"
                    )
                ),
                "rules" to listOf(
                    mapOf(
                        "rule_set" to "geosite-cn",
                        "server" to "dns_direct"
                    )
                ),
                "final" to "dns_proxy"
            ),
            "outbounds" to emptyList<Map<String, Any>>(),
            "route" to mapOf(
                "rules" to listOf(
                    mapOf(
                        "protocol" to "dns",
                        "action" to "hijack-dns"
                    ),
                    mapOf(
                        "rule_set" to "geosite-cn",
                        "outbound" to "direct"
                    ),
                    mapOf(
                        "rule_set" to "geoip-cn",
                        "outbound" to "direct"
                    ),
                    mapOf(
                        "ip_is_private" to true,
                        "outbound" to "direct"
                    )
                ),
                "rule_set" to listOf(
                    mapOf(
                        "tag" to "geosite-cn",
                        "type" to "remote",
                        "format" to "binary",
                        "url" to "https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/geosite-cn.srs",
                        "download_detour" to "direct"
                    ),
                    mapOf(
                        "tag" to "geoip-cn",
                        "type" to "remote",
                        "format" to "binary",
                        "url" to "https://raw.githubusercontent.com/SagerNet/sing-geoip/rule-set/geoip-cn.srs",
                        "download_detour" to "direct"
                    )
                ),
                "auto_detect_interface" to true,
                "default_domain_resolver" to "dns_resolver",
                "final" to "PROXY_OUTBOUND"
            ),
            "experimental" to mapOf(
                "cache_file" to mapOf(
                    "enabled" to true
                )
            )
        )
    }

    /**
     * China-optimized template (CN routing)
     */
    private fun getChinaOptimizedTemplate(): Map<String, Any> {
        val config = getDefaultTemplate().toMutableMap()

        // Optimize for China users
        val route = (config["route"] as? Map<*, *>)?.toMutableMap() ?: mutableMapOf()
        route["final"] = "PROXY_OUTBOUND"
        config["route"] = route

        return config
    }

    /**
     * Global-optimized template (all traffic via proxy)
     */
    private fun getGlobalOptimizedTemplate(): Map<String, Any> {
        val config = getDefaultTemplate().toMutableMap()

        // Route all traffic through proxy
        val route = (config["route"] as? Map<*, *>)?.toMutableMap() ?: mutableMapOf()
        route["rules"] = listOf(
            mapOf(
                "protocol" to "dns",
                "action" to "hijack-dns"
            )
        )
        route["final"] = "PROXY_OUTBOUND"
        config["route"] = route

        return config
    }

    /**
     * Minimal template (simplest configuration)
     */
    private fun getMinimalTemplate(): Map<String, Any> {
        return mapOf(
            "inbounds" to listOf(
                mapOf(
                    "type" to "tun",
                    "address" to listOf("198.18.0.1/16"),
                    "auto_route" to true,
                    "strict_route" to true,
                    "stack" to "gvisor"
                )
            ),
            "route" to mapOf(
                "auto_detect_interface" to true,
                "final" to "PROXY_OUTBOUND"
            )
        )
    }

    /**
     * Create outbound configuration from server parameters
     */
    private fun createOutbound(params: ServerParameters, tag: String): Map<String, Any> {
        val outbound = mutableMapOf<String, Any>(
            "type" to params.protocolType,
            "tag" to tag,
            "server" to params.server,
            "server_port" to params.serverPort,
            "method" to params.method,
            "password" to params.password
        )

        // Add optional parameters
        params.network?.let { outbound["network"] = it }

        if (params.tls == true) {
            val tlsConfig = mutableMapOf<String, Any>(
                "enabled" to true,
                "server_name" to (params.sni ?: params.server)
            )
            params.alpn?.let { alpn ->
                tlsConfig["alpn"] = alpn
            }
            outbound["tls"] = tlsConfig
        }

        // WebSocket transport
        if (params.network == "ws" || params.network == "websocket") {
            outbound["transport"] = mapOf(
                "type" to "ws",
                "path" to (params.path ?: "/")
            )
        }

        // gRPC transport
        if (params.network == "grpc") {
            outbound["transport"] = mapOf(
                "type" to "grpc",
                "service_name" to (params.path ?: "GunService")
            )
        }

        // Plugin support
        params.plugin?.let { plugin ->
            outbound["plugin"] = plugin
            params.pluginOpts?.let { outbound["plugin_opts"] = it }
        }

        return outbound
    }

    /**
     * Replace PROXY_OUTBOUND placeholder with actual tag
     */
    private fun replacePlaceholders(config: Map<String, Any>, proxyTag: String): Map<String, Any> {
        val result = config.toMutableMap()

        // Replace in DNS
        (result["dns"] as? Map<*, *>)?.toMutableMap()?.let { dns ->
            // Replace in servers
            (dns["servers"] as? List<*>)?.let { servers ->
                dns["servers"] = servers.map { server ->
                    (server as? Map<*, *>)?.toMutableMap()?.apply {
                        if (this["detour"] == "PROXY_OUTBOUND") {
                            this["detour"] = proxyTag
                        }
                    } ?: server
                }
            }

            // Replace in final
            if (dns["final"] == "PROXY_OUTBOUND") {
                dns["final"] = proxyTag
            }

            result["dns"] = dns
        }

        // Replace in Route
        (result["route"] as? Map<*, *>)?.toMutableMap()?.let { route ->
            if (route["final"] == "PROXY_OUTBOUND") {
                route["final"] = proxyTag
            }
            result["route"] = route
        }

        return result
    }
}

/**
 * Manager for configuration templates
 */
object ConfigTemplateManager {
    private const val TAG = "ConfigTemplateManager"
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    /**
     * Generate full sing-box configuration from compact config
     */
    fun generateFullConfig(compactConfig: CompactShareableConfig): String {
        Log.i(TAG, "Generating full configuration from compact config")

        // 1. Check expiration
        if (compactConfig.isExpired) {
            Log.w(TAG, "Configuration has expired")
            throw ConfigurationCryptoError.ExpiredConfig
        }

        // 2. Get template
        val template = ConfigTemplate.fromId(compactConfig.templateId)

        // 3. Apply parameters
        var config = template.applyParameters(compactConfig.serverParams)

        // 4. Apply overrides if present
        compactConfig.dnsOverride?.let { dnsJson ->
            try {
                val dnsOverride = json.parseToJsonElement(dnsJson)
                config = config.toMutableMap().apply {
                    this["dns"] = dnsOverride
                }
                Log.d(TAG, "Applied DNS override")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse DNS override: ${e.message}")
            }
        }

        compactConfig.routeOverride?.let { routeJson ->
            try {
                val routeOverride = json.parseToJsonElement(routeJson)
                config = config.toMutableMap().apply {
                    this["route"] = routeOverride
                }
                Log.d(TAG, "Applied route override")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse route override: ${e.message}")
            }
        }

        // 5. Convert to JSON string
        val configJson = json.encodeToString(
            kotlinx.serialization.serializer(),
            config
        )

        Log.i(TAG, "Full configuration generated successfully, size: ${configJson.length} bytes")
        return configJson
    }

    /**
     * Extract server parameters from existing sing-box configuration
     */
    fun extractServerParameters(configJSON: String): ServerParameters? {
        Log.i(TAG, "Extracting server parameters from configuration")

        try {
            val config = json.parseToJsonElement(configJSON) as? JsonObject
                ?: return null

            // Find the first proxy outbound
            val outbounds = config["outbounds"] as? List<*> ?: return null

            // Find first non-direct outbound
            val proxyOutbound = outbounds
                .filterIsInstance<Map<*, *>>()
                .firstOrNull { it["type"] != "direct" }
                ?: return null

            // Extract parameters
            val server = proxyOutbound["server"] as? String ?: return null
            val serverPort = (proxyOutbound["server_port"] as? Number)?.toInt() ?: return null
            val password = proxyOutbound["password"] as? String ?: return null
            val method = proxyOutbound["method"] as? String ?: return null

            return ServerParameters(
                server = server,
                serverPort = serverPort,
                password = password,
                method = method,
                tag = proxyOutbound["tag"] as? String,
                type = proxyOutbound["type"] as? String,
                network = proxyOutbound["network"] as? String,
                tls = ((proxyOutbound["tls"] as? Map<*, *>)?.get("enabled") as? Boolean)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract server parameters: ${e.message}")
            return null
        }
    }
}
