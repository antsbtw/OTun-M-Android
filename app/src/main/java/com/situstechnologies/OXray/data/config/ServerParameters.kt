package com.situstechnologies.OXray.data.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Core server parameters for VPN configuration
 * Minimal data required to reconstruct a full sing-box configuration
 *
 * Ported from iOS CompactConfig.swift
 */
@Serializable
data class ServerParameters(
    // MARK: - Required Parameters

    /** Server address (IP or domain) */
    val server: String,

    /** Server port */
    @SerialName("server_port")
    val serverPort: Int,

    /** Connection password/key */
    val password: String,

    /** Encryption method (e.g., "chacha20-ietf-poly1305", "aes-256-gcm") */
    val method: String,

    // MARK: - Optional Parameters

    /** Configuration tag/name (auto-generated if null) */
    val tag: String? = null,

    /** Protocol type (default: "shadowsocks") */
    val type: String? = null,

    /** Transport protocol ("tcp", "udp", "ws", "grpc") */
    val network: String? = null,

    /** Enable TLS */
    val tls: Boolean? = null,

    // MARK: - Advanced Parameters

    /** TLS Server Name Indication */
    val sni: String? = null,

    /** ALPN protocols */
    val alpn: List<String>? = null,

    /** WebSocket path */
    val path: String? = null,

    /** Plugin name (e.g., "obfs-local", "v2ray-plugin") */
    val plugin: String? = null,

    /** Plugin options */
    @SerialName("plugin_opts")
    val pluginOpts: String? = null
) {
    /**
     * Get outbound tag (use provided or generate UUID-based tag)
     */
    val outboundTag: String
        get() = tag ?: "proxy-${java.util.UUID.randomUUID().toString().take(8)}"

    /**
     * Get protocol type (default to shadowsocks)
     */
    val protocolType: String
        get() = type ?: "shadowsocks"
}
