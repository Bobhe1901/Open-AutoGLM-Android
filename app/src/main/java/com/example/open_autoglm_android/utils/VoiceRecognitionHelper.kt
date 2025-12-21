package com.example.open_autoglm_android.utils

import android.content.Context
import android.content.Intent
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

object VoiceRecognitionHelper {
    
    private var speechRecognizer: SpeechRecognizer? = null
    private var onListeningCallback: ((Boolean) -> Unit)? = null
    
    fun isVoiceRecognitionAvailable(context: Context): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }
    
    fun startVoiceRecognition(
        context: Context,
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        onListening: (Boolean) -> Unit
    ) {
        // 保存回调引用
        onListeningCallback = onListening
        
        // 先停止之前的识别
        stopVoiceRecognition()
        
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        
        val recognitionListener = object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) {
                Log.d("VoiceRecognition", "准备就绪，可以开始说话")
                onListening(true)
            }
            
            override fun onBeginningOfSpeech() {
                Log.d("VoiceRecognition", "开始说话")
            }
            
            override fun onRmsChanged(rmsdB: Float) {
                // 音量变化，可用于显示语音波形
            }
            
            override fun onBufferReceived(buffer: ByteArray?) {
                // 音频数据缓冲区
            }
            
            override fun onEndOfSpeech() {
                Log.d("VoiceRecognition", "结束说话")
                onListening(false)
            }
            
            override fun onError(error: Int) {
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络超时"
                    SpeechRecognizer.ERROR_NETWORK -> "网络错误"
                    SpeechRecognizer.ERROR_AUDIO -> "音频错误"
                    SpeechRecognizer.ERROR_SERVER -> "服务器错误"
                    SpeechRecognizer.ERROR_CLIENT -> "客户端错误"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "语音超时"
                    SpeechRecognizer.ERROR_NO_MATCH -> "没有匹配结果"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别器繁忙"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "权限不足"
                    else -> "未知错误: $error"
                }
                Log.e("VoiceRecognition", "识别错误: $errorMessage")
                onError(errorMessage)
                onListening(false)
                
                // 错误后销毁识别器
                destroyRecognizer()
            }
            
            override fun onResults(results: android.os.Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (matches != null && matches.isNotEmpty()) {
                    val recognizedText = matches[0]
                    Log.d("VoiceRecognition", "识别结果: $recognizedText")
                    onResult(recognizedText)
                } else {
                    Log.w("VoiceRecognition", "没有识别结果")
                    onError("没有识别到语音")
                }
                onListening(false)
                
                // 结果后销毁识别器
                destroyRecognizer()
            }
            
            override fun onPartialResults(partialResults: android.os.Bundle?) {
                // 部分识别结果
            }
            
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {
                // 其他事件
            }
        }
        
        speechRecognizer?.setRecognitionListener(recognitionListener)
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "请说话...")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            
            // 移除可能不兼容的超时参数，让系统默认处理
        }
        
        speechRecognizer?.startListening(intent)
    }
    
    fun stopVoiceRecognition() {
        speechRecognizer?.apply {
            // 停止监听并触发最终结果
            stopListening()
            
            // 立即通知UI录音已停止
            onListeningCallback?.invoke(false)
            
            // 注意：不要在这里调用destroy()，否则会错过识别结果
            // 识别结果会在onResults回调中处理，然后在回调中销毁识别器
        }
        // 不要立即设置为null，让它保持活动状态直到收到结果
    }
    
    private fun destroyRecognizer() {
        speechRecognizer?.apply {
            destroy()
        }
        speechRecognizer = null
        // 清理回调引用
        onListeningCallback = null
    }
}