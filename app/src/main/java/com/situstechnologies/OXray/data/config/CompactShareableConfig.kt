package com.situstechnologies.OXray.data.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.util.Date
import java.util.UUID

/**
 * Compact shareable configuration for efficient QR code sharing
 * Uses template system to reduce data size by 93%
 *
 * Ported from iOS CompactConfig.swift
 */
@Serializable
data class CompactShareableConfig(
    // MARK: - Core Data

    /** Template version (e.g., "1.0") */
    val version: String = "1.0",

    /** Template ID to use (e.g., "default-singbox", "china-optimized") */
    @SerialName("template_id")
    val templateId: String = "default-singbox",

    /** Server parameters (the only data that changes between configs) */
    @SerialName("server_params")
    val serverParams: ServerParameters,

    // MARK: - Metadata

    /** Display name for the configuration */
    @SerialName("config_name")
    val configName: String? = null,

    /** Unique share identifier (for tracking) */
    @SerialName("share_id")
    val shareId: String = UUID.randomUUID().toString(),

    /** Creation timestamp (epoch seconds) */
    @SerialName("created_at")
    val createdAt: Long = System.currentTimeMillis() / 1000,

    /** Expiration timestamp (null = never expires) */
    @SerialName("expiration_date")
    val expirationDate: Long? = null,

    // MARK: - Advanced Overrides (Optional)

    /** Custom DNS configuration (JSON string) */
    @SerialName("dns_override")
    val dnsOverride: String? = null,

    /** Custom route configuration (JSON string) */
    @SerialName("route_override")
    val routeOverride: String? = null
) {
    /**
     * Check if configuration has expired
     */
    @Transient
    val isExpired: Boolean
        get() = expirationDate?.let {
            (System.currentTimeMillis() / 1000) > it
        } ?: false

    /**
     * Get display name or generate default
     */
    @Transient
    val displayName: String
        get() = configName ?: "${serverParams.server}:${serverParams.serverPort}"
}
