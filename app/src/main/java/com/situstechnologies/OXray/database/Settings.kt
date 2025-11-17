package com.situstechnologies.OXray.database

import androidx.room.Room
import com.situstechnologies.OXray.Application
import com.situstechnologies.OXray.bg.ProxyService
import com.situstechnologies.OXray.bg.VPNService
import com.situstechnologies.OXray.constant.Path
import com.situstechnologies.OXray.constant.ServiceMode
import com.situstechnologies.OXray.constant.SettingsKey
import com.situstechnologies.OXray.database.preference.KeyValueDatabase
import com.situstechnologies.OXray.database.preference.RoomPreferenceDataStore
import com.situstechnologies.OXray.ktx.boolean
import com.situstechnologies.OXray.ktx.int
import com.situstechnologies.OXray.ktx.long
import com.situstechnologies.OXray.ktx.string
import com.situstechnologies.OXray.ktx.stringSet
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

object Settings {

    @OptIn(DelicateCoroutinesApi::class)
    private val instance by lazy {
        Application.application.getDatabasePath(Path.SETTINGS_DATABASE_PATH).parentFile?.mkdirs()
        Room.databaseBuilder(
            Application.application,
            KeyValueDatabase::class.java,
            Path.SETTINGS_DATABASE_PATH
        ).allowMainThreadQueries()
            .fallbackToDestructiveMigration()
            .enableMultiInstanceInvalidation()
            .setQueryExecutor { GlobalScope.launch { it.run() } }
            .build()
    }
    val dataStore = RoomPreferenceDataStore(instance.keyValuePairDao())
    var selectedProfile by dataStore.long(SettingsKey.SELECTED_PROFILE) { -1L }
    var serviceMode by dataStore.string(SettingsKey.SERVICE_MODE) { ServiceMode.NORMAL }
    var startedByUser by dataStore.boolean(SettingsKey.STARTED_BY_USER)

    var checkUpdateEnabled by dataStore.boolean(SettingsKey.CHECK_UPDATE_ENABLED) { true }
    var disableMemoryLimit by dataStore.boolean(SettingsKey.DISABLE_MEMORY_LIMIT)
    var dynamicNotification by dataStore.boolean(SettingsKey.DYNAMIC_NOTIFICATION) { true }


    const val PER_APP_PROXY_DISABLED = 0
    const val PER_APP_PROXY_EXCLUDE = 1
    const val PER_APP_PROXY_INCLUDE = 2

    var perAppProxyEnabled by dataStore.boolean(SettingsKey.PER_APP_PROXY_ENABLED) { false }
    var perAppProxyMode by dataStore.int(SettingsKey.PER_APP_PROXY_MODE) { PER_APP_PROXY_EXCLUDE }
    var perAppProxyList by dataStore.stringSet(SettingsKey.PER_APP_PROXY_LIST) { emptySet() }
    var perAppProxyUpdateOnChange by dataStore.int(SettingsKey.PER_APP_PROXY_UPDATE_ON_CHANGE) { PER_APP_PROXY_DISABLED }

    var systemProxyEnabled by dataStore.boolean(SettingsKey.SYSTEM_PROXY_ENABLED) { true }

    fun serviceClass(): Class<*> {
        return when (serviceMode) {
            ServiceMode.VPN -> VPNService::class.java
            else -> ProxyService::class.java
        }
    }

    suspend fun rebuildServiceMode(): Boolean {
        var newMode = ServiceMode.NORMAL
        try {
            if (needVPNService()) {
                newMode = ServiceMode.VPN
            }
        } catch (_: Exception) {
        }
        if (serviceMode == newMode) {
            return false
        }
        serviceMode = newMode
        return true
    }

    private suspend fun needVPNService(): Boolean {
        val selectedProfileId = selectedProfile
        if (selectedProfileId == -1L) return false
        val profile = ProfileManager.get(selectedProfile) ?: return false
        val content = JSONObject(File(profile.typed.path).readText())
        val inbounds = content.getJSONArray("inbounds")
        for (index in 0 until inbounds.length()) {
            val inbound = inbounds.getJSONObject(index)
            if (inbound.getString("type") == "tun") {
                return true
            }
        }
        return false
    }

}