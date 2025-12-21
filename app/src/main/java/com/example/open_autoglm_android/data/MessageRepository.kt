package com.example.open_autoglm_android.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.open_autoglm_android.ui.viewmodel.ChatMessage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "chat_messages")

class MessageRepository(private val context: Context) {
    
    private val gson = Gson()
    private val messagesKey = stringPreferencesKey("chat_messages")
    private val TAG = "MessageRepository"
    
    suspend fun saveMessages(messages: List<ChatMessage>) {
        Log.d(TAG, "开始保存消息，共 ${messages.size} 条")
        try {
            val messagesJson = gson.toJson(messages)
            Log.d(TAG, "消息序列化完成，JSON长度: ${messagesJson.length}")
            
            context.dataStore.edit { preferences ->
                preferences[messagesKey] = messagesJson
                Log.d(TAG, "消息保存到DataStore成功")
            }
        } catch (e: Exception) {
            Log.e(TAG, "保存消息失败", e)
            throw e
        }
    }
    
    suspend fun loadMessages(): List<ChatMessage> {
        Log.d(TAG, "开始加载保存的消息")
        try {
            val messagesJson = context.dataStore.data
                .map { preferences ->
                    val json = preferences[messagesKey] ?: "[]"
                    Log.d(TAG, "从DataStore读取到JSON，长度: ${json.length}")
                    json
                }
                .first()
            
            val type = object : TypeToken<List<ChatMessage>>() {}.type
            val messages = gson.fromJson<List<ChatMessage>>(messagesJson, type)
            Log.d(TAG, "消息反序列化完成，共 ${messages.size} 条")
            return messages
        } catch (e: Exception) {
            Log.e(TAG, "加载消息失败", e)
            return emptyList()
        }
    }
    
    suspend fun clearMessages() {
        Log.d(TAG, "开始清除保存的消息")
        try {
            context.dataStore.edit { preferences ->
                preferences.remove(messagesKey)
                Log.d(TAG, "消息从DataStore清除成功")
            }
        } catch (e: Exception) {
            Log.e(TAG, "清除消息失败", e)
            throw e
        }
    }
}