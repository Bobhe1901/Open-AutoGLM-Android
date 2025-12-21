package com.example.open_autoglm_android.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private const val DATA_STORE_NAME = "auto_glm_preferences"

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = DATA_STORE_NAME)

class PreferencesRepository(private val context: Context) {
    // API Key
    private val API_KEY = stringPreferencesKey("api_key")
    
    val apiKey: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[API_KEY] ?: "2b55beee279d437ea8c7460e29bc12b0.X0JeFydsJjZjp4Rf"
        }
    
    suspend fun saveApiKey(apiKey: String) {
        context.dataStore.edit { preferences ->
            preferences[API_KEY] = apiKey
        }
    }
    
    fun getApiKeySync(): String {
        return runBlocking {
            context.dataStore.data.first()[API_KEY] ?: "2b55beee279d437ea8c7460e29bc12b0.X0JeFydsJjZjp4Rf"
        }
    }
    
    // Base URL
    private val BASE_URL = stringPreferencesKey("base_url")
    
    val baseUrl: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[BASE_URL] ?: "https://open.bigmodel.cn/api/paas/v4"
        }
    
    suspend fun saveBaseUrl(baseUrl: String) {
        context.dataStore.edit { preferences ->
            preferences[BASE_URL] = baseUrl
        }
    }
    
    fun getBaseUrlSync(): String {
        return runBlocking {
            context.dataStore.data.first()[BASE_URL] ?: "https://open.bigmodel.cn/api/paas/v4"
        }
    }
    
    // Model Name
    private val MODEL_NAME = stringPreferencesKey("model_name")
    
    val modelName: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[MODEL_NAME] ?: "autoglm-phone"
        }
    
    suspend fun saveModelName(modelName: String) {
        context.dataStore.edit { preferences ->
            preferences[MODEL_NAME] = modelName
        }
    }
    
    fun getModelNameSync(): String {
        return runBlocking {
            context.dataStore.data.first()[MODEL_NAME] ?: "autoglm-phone"
        }
    }
    
    // Temperature
    private val TEMPERATURE = floatPreferencesKey("temperature")
    
    val temperature: Flow<Float> = context.dataStore.data
        .map { preferences ->
            preferences[TEMPERATURE] ?: 0.0f
        }
    
    suspend fun saveTemperature(temperature: Float) {
        context.dataStore.edit { preferences ->
            preferences[TEMPERATURE] = temperature
        }
    }
    
    fun getTemperatureSync(): Float {
        return runBlocking {
            context.dataStore.data.first()[TEMPERATURE] ?: 0.0f
        }
    }
    
    // Top P
    private val TOP_P = floatPreferencesKey("top_p")
    
    val topP: Flow<Float> = context.dataStore.data
        .map { preferences ->
            preferences[TOP_P] ?: 0.85f
        }
    
    suspend fun saveTopP(topP: Float) {
        context.dataStore.edit { preferences ->
            preferences[TOP_P] = topP
        }
    }
    
    fun getTopPSync(): Float {
        return runBlocking {
            context.dataStore.data.first()[TOP_P] ?: 0.85f
        }
    }
    
    // Frequency Penalty
    private val FREQUENCY_PENALTY = floatPreferencesKey("frequency_penalty")
    
    val frequencyPenalty: Flow<Float> = context.dataStore.data
        .map { preferences ->
            preferences[FREQUENCY_PENALTY] ?: 0.2f
        }
    
    suspend fun saveFrequencyPenalty(frequencyPenalty: Float) {
        context.dataStore.edit { preferences ->
            preferences[FREQUENCY_PENALTY] = frequencyPenalty
        }
    }
    
    fun getFrequencyPenaltySync(): Float {
        return runBlocking {
            context.dataStore.data.first()[FREQUENCY_PENALTY] ?: 0.2f
        }
    }
}