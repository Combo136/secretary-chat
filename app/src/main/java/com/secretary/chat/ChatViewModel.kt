package com.secretary.chat

import android.app.Application
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.net.URL

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private var webSocketManager: WebSocketManager? = null
    
    var serverIp = "192.168.1.100"
    var port = 8000
    var clientId = "android_user"
    
    var messages = mutableStateListOf<Message>()
    var messageText = mutableStateOf("")
    var isConnected = mutableStateOf(false)
    var isRecording = mutableStateOf(false)
    
    private var voiceFile: File? = null
    private var mediaRecorder: android.media.MediaRecorder? = null
    
    init {
        // 載入歷史訊息
        loadHistory()
    }
    
    fun connect(ip: String, port: Int, id: String) {
        serverIp = ip
        this.port = port
        clientId = id
        
        webSocketManager = WebSocketManager(viewModelScope).apply {
            onConnectionChanged = { connected ->
                isConnected.value = connected
            }
            onMessageReceived = { json ->
                messages.add(Message.fromJson(json, clientId))
                saveMessage(json)
            }
        }
        
        webSocketManager?.connect(serverIp, port, clientId)
    }
    
    fun sendText() {
        if (messageText.value.isBlank()) return
        webSocketManager?.sendText(messageText.value)
        messageText.value = ""
    }
    
    fun pickFile() {
        // 檔案選擇邏輯由 Activity 處理
    }
    
    fun toggleVoiceRecording() {
        if (isRecording.value) {
            stopRecording()
        } else {
            startRecording()
        }
    }
    
    private fun startRecording() {
        try {
            val outputFile = File(context.cacheDir, "voice_${System.currentTimeMillis()}.3gp")
            voiceFile = outputFile
            
            mediaRecorder = android.media.MediaRecorder().apply {
                setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                setOutputFormat(android.media.MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
            isRecording.value = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording.value = false
            
            // 轉為 base64 發送
            voiceFile?.let { file ->
                if (file.exists()) {
                    val bytes = FileInputStream(file).use { it.readBytes() }
                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    webSocketManager?.sendVoice(base64)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun sendFile(base64Data: String, fileName: String, fileType: String) {
        webSocketManager?.sendFile(base64Data, fileName, fileType)
    }
    
    private fun loadHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = "http://$serverIp:$port/api/messages"
                val response = OkHttpClient().newCall(
                    Request.Builder().url(url).build()
                ).execute()
                
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "{}")
                    val msgArray = json.optJSONArray("messages") ?: JSONArray()
                    
                    for (i in 0 until msgArray.length()) {
                        val msg = msgArray.getJSONObject(i)
                        messages.add(Message.fromJson(msg, clientId))
                    }
                }
            } catch (e: Exception) {
                // 伺服器可能還沒啟動，忽略錯誤
            }
        }
    }
    
    private fun saveMessage(json: JSONObject) {
        // 可擴展：儲存到本地資料庫
    }
    
    override fun onCleared() {
        super.onCleared()
        webSocketManager?.disconnect()
    }
}
