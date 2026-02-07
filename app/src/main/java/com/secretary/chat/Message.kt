package com.secretary.chat

import org.json.JSONObject

data class Message(
    val id: String = "",
    val sender: String = "",
    val content: String = "",
    val msgType: String = "text",
    val fileUrl: String = "",
    val fileName: String = "",
    val timestamp: String = "",
    val isFromMe: Boolean = false
) {
    companion object {
        fun fromJson(json: JSONObject, myId: String): Message {
            return Message(
                id = json.optString("id", ""),
                sender = json.optString("sender", ""),
                content = json.optString("content", ""),
                msgType = json.optString("msg_type", "text"),
                fileUrl = json.optString("file_url", ""),
                fileName = json.optString("file_name", ""),
                timestamp = json.optString("timestamp", ""),
                isFromMe = json.optString("sender", "") == myId
            )
        }
    }
    
    val isText: Boolean get() = msgType == "text"
    val isImage: Boolean get() = msgType == "image"
    val isVideo: Boolean get() = msgType == "video"
    val isFile: Boolean get() = msgType == "file"
    val isVoice: Boolean get() = msgType == "voice"
}
