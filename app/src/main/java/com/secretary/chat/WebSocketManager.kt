package com.secretary.chat

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

class WebSocketManager(private val scope: CoroutineScope) {
    private var webSocket: WebSocket? = null
    private var serverUrl = ""
    private var clientId = ""
    
    var onMessageReceived: ((JSONObject) -> Unit)? = null
    var onConnectionChanged: ((Boolean) -> Unit)? = null
    
    fun connect(serverIp: String, port: Int, clientId: String) {
        this.serverUrl = "ws://$serverIp:$port/ws/$clientId"
        this.clientId = clientId
        
        val client = OkHttpClient.Builder().build()
        val request = Request.Builder().url(serverUrl).build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("WebSocket", "Connected!")
                onConnectionChanged?.invoke(true)
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    onMessageReceived?.invoke(json)
                } catch (e: Exception) {
                    Log.e("WebSocket", "Parse error: ${e.message}")
                }
            }
            
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("WebSocket", "Closing: $reason")
                onConnectionChanged?.invoke(false)
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("WebSocket", "Error: ${t.message}")
                onConnectionChanged?.invoke(false)
            }
        })
    }
    
    fun sendText(content: String) {
        val json = JSONObject().apply {
            put("type", "message")
            put("content", content)
            put("msg_type", "text")
        }
        webSocket?.send(json.toString())
    }
    
    fun sendFile(fileData: String, fileName: String, fileType: String, caption: String = "") {
        val json = JSONObject().apply {
            put("type", "file")
            put("file_data", fileData)
            put("file_name", fileName)
            put("file_type", fileType)
            put("caption", caption)
        }
        webSocket?.send(json.toString())
    }
    
    fun sendVoice(fileData: String) {
        val json = JSONObject().apply {
            put("type", "voice")
            put("file_data", fileData)
        }
        webSocket?.send(json.toString())
    }
    
    fun disconnect() {
        webSocket?.close(1000, "Client closing")
        webSocket = null
        onConnectionChanged?.invoke(false)
    }
}
