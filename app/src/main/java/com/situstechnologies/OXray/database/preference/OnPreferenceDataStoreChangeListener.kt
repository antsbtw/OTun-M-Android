package com.situstechnologies.OXray.database.preference

import androidx.preference.PreferenceDataStore

interface OnPreferenceDataStoreChangeListener {
    fun onPreferenceDataStoreChanged(store: PreferenceDataStore, key: String)
}
