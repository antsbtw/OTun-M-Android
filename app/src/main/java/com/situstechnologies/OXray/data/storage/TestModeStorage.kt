package com.situstechnologies.OXray.data.storage

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Test account record
 *
 * Ported from iOS TestModeRecord.swift
 */
@Serializable
data class TestModeRecord(
    val shareId: String,              // Share ID (unique identifier)
    val activatedAt: Long,            // Activation time (epoch millis)
    val expiresAt: Long,              // Expiration time (epoch millis)
    val configName: String,           // Configuration name
    val testDurationMinutes: Int      // Test duration in minutes
) {
    /**
     * Remaining minutes until expiration
     */
    val remainingMinutes: Int
        get() {
            val remaining = expiresAt - System.currentTimeMillis()
            return maxOf(0, TimeUnit.MILLISECONDS.toMinutes(remaining).toInt())
        }

    /**
     * Check if expired
     */
    val isExpired: Boolean
        get() = System.currentTimeMillis() > expiresAt
}

/**
 * Test account storage manager
 * Uses SharedPreferences for persistence
 *
 * Ported from iOS TestModeStorage.swift
 */
class TestModeStorage private constructor(context: Context) {

    companion object {
        private const val TAG = "TestModeStorage"
        private const val PREFS_NAME = "test_mode_storage"
        private const val KEY_RECORDS = "test_mode_records"
        private const val KEY_MAPPING = "test_mode_config_to_share_mapping"

        @Volatile
        private var instance: TestModeStorage? = null

        fun getInstance(context: Context): TestModeStorage {
            return instance ?: synchronized(this) {
                instance ?: TestModeStorage(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Save test account record (using shareId as primary key)
     */
    fun saveRecord(record: TestModeRecord) {
        // 1. Save main record (shareId -> TestModeRecord)
        val records = getAllRecordsByShareId().toMutableMap()
        records[record.shareId] = record

        val recordsJson = json.encodeToString(records)
        prefs.edit().putString(KEY_RECORDS, recordsJson).apply()

        // 2. Save mapping (configName -> shareId)
        val mapping = getConfigNameMapping().toMutableMap()
        mapping[record.configName] = record.shareId

        val mappingJson = json.encodeToString(mapping)
        prefs.edit().putString(KEY_MAPPING, mappingJson).apply()

        Log.i(TAG, "✅ Saved test record - ShareID: ${record.shareId}, Config: ${record.configName}")
    }

    /**
     * Get record by config name (used when connecting)
     */
    fun getRecord(configName: String): TestModeRecord? {
        // First, find shareId from mapping
        val shareId = getConfigNameMapping()[configName] ?: return null

        // Then, get record by shareId
        return getAllRecordsByShareId()[shareId]
    }

    /**
     * Get record by shareId (used when importing)
     */
    fun getRecordByShareId(shareId: String): TestModeRecord? {
        return getAllRecordsByShareId()[shareId]
    }

    /**
     * Delete config mapping (but keep main record to prevent re-activation)
     */
    fun deleteConfigMapping(configName: String) {
        val mapping = getConfigNameMapping().toMutableMap()
        mapping.remove(configName)

        val mappingJson = json.encodeToString(mapping)
        prefs.edit().putString(KEY_MAPPING, mappingJson).apply()

        Log.i(TAG, "🗑️ Deleted config mapping: $configName")
    }

    /**
     * Clean up expired records (can truly delete shareId records)
     */
    fun cleanupExpiredRecords() {
        val records = getAllRecordsByShareId().toMutableMap()
        val mapping = getConfigNameMapping().toMutableMap()
        val now = System.currentTimeMillis()

        val expiredShareIds = records.filter { it.value.expiresAt < now }.keys

        for (shareId in expiredShareIds) {
            val record = records[shareId]
            if (record != null) {
                // Delete main record
                records.remove(shareId)
                // Delete mapping
                mapping.remove(record.configName)
                Log.i(TAG, "🧹 Cleaned up expired record: ${record.configName}")
            }
        }

        if (expiredShareIds.isNotEmpty()) {
            val recordsJson = json.encodeToString(records)
            val mappingJson = json.encodeToString(mapping)

            prefs.edit()
                .putString(KEY_RECORDS, recordsJson)
                .putString(KEY_MAPPING, mappingJson)
                .apply()
        }
    }

    /**
     * Get all records indexed by shareId
     */
    private fun getAllRecordsByShareId(): Map<String, TestModeRecord> {
        val recordsJson = prefs.getString(KEY_RECORDS, null) ?: return emptyMap()

        return try {
            json.decodeFromString<Map<String, TestModeRecord>>(recordsJson)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse records: ${e.message}")
            emptyMap()
        }
    }

    /**
     * Get config name to shareId mapping
     */
    private fun getConfigNameMapping(): Map<String, String> {
        val mappingJson = prefs.getString(KEY_MAPPING, null) ?: return emptyMap()

        return try {
            json.decodeFromString<Map<String, String>>(mappingJson)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse mapping: ${e.message}")
            emptyMap()
        }
    }
}
