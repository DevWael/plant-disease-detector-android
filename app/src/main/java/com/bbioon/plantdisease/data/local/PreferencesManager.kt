package com.bbioon.plantdisease.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesManager(private val context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    companion object {
        private val KEY_MODEL = stringPreferencesKey("model")
        private val KEY_LANGUAGE = stringPreferencesKey("language")
        private val KEY_FIRST_LAUNCH = booleanPreferencesKey("first_launch_done")
        private const val KEY_API_KEY = "api_key"
        const val DEFAULT_MODEL = "gemma-3-27b-it"
    }

    // --- API Key (encrypted) ---

    fun getApiKey(): String? = securePrefs.getString(KEY_API_KEY, null)

    fun setApiKey(key: String) {
        securePrefs.edit().putString(KEY_API_KEY, key).apply()
    }

    fun deleteApiKey() {
        securePrefs.edit().remove(KEY_API_KEY).apply()
    }

    // --- Model ---

    suspend fun getModel(): String {
        return context.dataStore.data.map { prefs ->
            prefs[KEY_MODEL] ?: DEFAULT_MODEL
        }.first()
    }

    suspend fun setModel(model: String) {
        context.dataStore.edit { prefs -> prefs[KEY_MODEL] = model }
    }

    // --- Language ---

    suspend fun getLanguage(): String {
        return context.dataStore.data.map { prefs ->
            prefs[KEY_LANGUAGE] ?: "en"
        }.first()
    }

    suspend fun setLanguage(language: String) {
        context.dataStore.edit { prefs -> prefs[KEY_LANGUAGE] = language }
    }

    // --- First Launch ---

    suspend fun isFirstLaunch(): Boolean {
        return context.dataStore.data.map { prefs ->
            prefs[KEY_FIRST_LAUNCH] != true
        }.first()
    }

    suspend fun setFirstLaunchComplete() {
        context.dataStore.edit { prefs -> prefs[KEY_FIRST_LAUNCH] = true }
    }
}
