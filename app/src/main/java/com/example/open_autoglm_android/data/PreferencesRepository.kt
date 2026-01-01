package com.example.open_autoglm_android.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object PreferenceKeys {
    val API_KEY = stringPreferencesKey("api_key")
    val BASE_URL = stringPreferencesKey("base_url")
    val MODEL_NAME = stringPreferencesKey("model_name")
    val FLOATING_WINDOW_ENABLED = booleanPreferencesKey("floating_window_enabled")
    val INPUT_MODE = intPreferencesKey("input_mode")
    val IMAGE_COMPRESSION_ENABLED = booleanPreferencesKey("image_compression_enabled")
    val IMAGE_COMPRESSION_LEVEL = intPreferencesKey("image_compression_level")
    val ENABLED_APPS = stringSetPreferencesKey("enabled_apps")  // 存储已启用应用的包名集合
    val BOUND_PHONE_NUMBER = stringPreferencesKey("bound_phone_number")  // 存储绑定的手机号
}

enum class InputMode(val value: Int) {
    SET_TEXT(0),    // 直接设置文本
    PASTE(1),       // 复制粘贴
    IME(2);         // 输入法模拟

    companion object {
        fun fromInt(value: Int) = InputMode.entries.firstOrNull { it.value == value } ?: SET_TEXT
    }
}

class PreferencesRepository(private val context: Context) {

    val apiKey: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.API_KEY]
    }

    val baseUrl: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.BASE_URL] ?: "https://open.bigmodel.cn/api/paas/v4"
    }

    val modelName: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.MODEL_NAME] ?: "autoglm-phone"
    }

    val floatingWindowEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.FLOATING_WINDOW_ENABLED] ?: false
    }

    val inputMode: Flow<InputMode> = context.dataStore.data.map { preferences ->
        InputMode.fromInt(preferences[PreferenceKeys.INPUT_MODE] ?: 0)
    }

    val imageCompressionEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.IMAGE_COMPRESSION_ENABLED] ?: false
    }

    val imageCompressionLevel: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.IMAGE_COMPRESSION_LEVEL] ?: 50
    }

    val enabledApps: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.ENABLED_APPS] ?: emptySet()
    }

    val boundPhoneNumber: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferenceKeys.BOUND_PHONE_NUMBER]
    }

    suspend fun saveApiKey(apiKey: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.API_KEY] = apiKey
        }
    }

    suspend fun saveBaseUrl(baseUrl: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.BASE_URL] = baseUrl
        }
    }

    suspend fun saveModelName(modelName: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.MODEL_NAME] = modelName
        }
    }

    suspend fun saveFloatingWindowEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.FLOATING_WINDOW_ENABLED] = enabled
        }
    }

    suspend fun saveInputMode(mode: InputMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.INPUT_MODE] = mode.value
        }
    }

    suspend fun saveImageCompressionEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.IMAGE_COMPRESSION_ENABLED] = enabled
        }
    }

    suspend fun saveImageCompressionLevel(level: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.IMAGE_COMPRESSION_LEVEL] = level
        }
    }

    suspend fun saveEnabledApps(enabledApps: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.ENABLED_APPS] = enabledApps
        }
    }

    suspend fun toggleAppEnabled(packageName: String, enabled: Boolean) {
        context.dataStore.edit { preferences ->
            val currentApps = 
                preferences[PreferenceKeys.ENABLED_APPS]?.toMutableSet() ?: mutableSetOf()
            if (enabled) {
                currentApps.add(packageName)
            } else {
                currentApps.remove(packageName)
            }
            preferences[PreferenceKeys.ENABLED_APPS] = currentApps
        }
    }

    suspend fun saveBoundPhoneNumber(phoneNumber: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.BOUND_PHONE_NUMBER] = phoneNumber
        }
    }

    suspend fun getBoundPhoneNumberSync(): String? {
        return context.dataStore.data.map { it[PreferenceKeys.BOUND_PHONE_NUMBER] }.firstOrNull()
    }

    suspend fun getApiKeySync(): String? {
        return context.dataStore.data.map { it[PreferenceKeys.API_KEY] }.firstOrNull()
    }

    suspend fun getBaseUrlSync(): String {
        return context.dataStore.data.map {
            it[PreferenceKeys.BASE_URL] ?: "https://open.bigmodel.cn/api/paas/v4"
        }.firstOrNull() ?: "https://open.bigmodel.cn/api/paas/v4"
    }

    suspend fun getModelNameSync(): String {
        return context.dataStore.data.map {
            it[PreferenceKeys.MODEL_NAME] ?: "autoglm-phone"
        }.firstOrNull() ?: "autoglm-phone"
    }

    suspend fun getFloatingWindowEnabledSync(): Boolean {
        return context.dataStore.data.map {
            it[PreferenceKeys.FLOATING_WINDOW_ENABLED] ?: false
        }.firstOrNull() ?: false
    }

    suspend fun getInputModeSync(): InputMode {
        return context.dataStore.data.map {
            InputMode.fromInt(it[PreferenceKeys.INPUT_MODE] ?: 0)
        }.firstOrNull() ?: InputMode.SET_TEXT
    }

    suspend fun getImageCompressionEnabledSync(): Boolean {
        return context.dataStore.data.map {
            it[PreferenceKeys.IMAGE_COMPRESSION_ENABLED] ?: false
        }.firstOrNull() ?: false
    }

    suspend fun getImageCompressionLevelSync(): Int {
        return context.dataStore.data.map {
            it[PreferenceKeys.IMAGE_COMPRESSION_LEVEL] ?: 50
        }.firstOrNull() ?: 50
    }

    suspend fun getEnabledAppsSync(): Set<String> {
        return context.dataStore.data.map {
            it[PreferenceKeys.ENABLED_APPS] ?: emptySet()
        }.firstOrNull() ?: emptySet()
    }

    suspend fun isAppEnabled(packageName: String): Boolean {
        return context.dataStore.data.map {
            it[PreferenceKeys.ENABLED_APPS]?.contains(packageName) ?: false
        }.firstOrNull() ?: false
    }
}
