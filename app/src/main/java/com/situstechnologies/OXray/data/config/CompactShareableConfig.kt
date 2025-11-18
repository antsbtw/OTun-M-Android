package com.situstechnologies.OXray.data.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.text.SimpleDateFormat
import java.util.*

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

    /** Creation timestamp (ISO 8601 format from iOS) */
    @SerialName("created_at")
    val createdAt: String,

    /** Expiration timestamp (ISO 8601 format, null = never expires) */
    @SerialName("expiration_date")
    val expirationDate: String? = null,

    /** Test configuration (for time-limited test accounts) */
    @SerialName("test_config")
    val testConfig: TestConfig? = null,

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
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val expiryDate = sdf.parse(it)
                expiryDate != null && Date().after(expiryDate)
            } catch (e: Exception) {
                false
            }
        } ?: false

    /**
     * Get display name or generate default
     */
    @Transient
    val displayName: String
        get() = configName ?: "${serverParams.server}:${serverParams.serverPort}"
}

/**
 * Test configuration for time-limited accounts
 */
@Serializable
data class TestConfig(
    /** Test type (e.g., "test") */
    val type: String,

    /** Test duration in minutes */
    @SerialName("test_duration_minutes")
    val testDurationMinutes: Int
)
