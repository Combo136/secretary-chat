package com.secretary.chat

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SecretaryChatApp()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecretaryChatApp() {
    val context = LocalContext.current
    val viewModel: ChatViewModel = viewModel()
    
    var showConnectionDialog by remember { mutableStateOf(true) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    
    // 連線對話框
    if (showConnectionDialog) {
        ConnectionDialog(
            serverIp = viewModel.serverIp,
            port = viewModel.port,
            clientId = viewModel.clientId,
            onConnect = { ip, port, id ->
                viewModel.connect(ip, port, id)
                showConnectionDialog = false
            }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("💬 Secretary Chat")
                        Spacer(Modifier.width(8.dp))
                        ConnectionStatus(isConnected = viewModel.isConnected)
                    }
                },
                actions = {
                    IconButton(onClick = { showConnectionDialog = true }) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            MessageInput(
                message = viewModel.messageText,
                onMessageChange = { viewModel.messageText = it },
                onSend = { viewModel.sendText() },
                onAttach = { viewModel.pickFile() },
                onVoice = { viewModel.toggleVoiceRecording() },
                showEmojiPicker = showEmojiPicker,
                onEmojiClick = { 
                    viewModel.messageText += it
                    showEmojiPicker = false
                },
                onEmojiToggle = { showEmojiPicker = !showEmojiPicker },
                isRecording = viewModel.isRecording
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 訊息列表
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = rememberLazyListState(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(viewModel.messages) { msg ->
                    MessageBubble(
                        message = msg,
                        isFromMe = msg.isFromMe
                    )
                }
            }
            
            // 狀態列
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "📱 Secretary Chat v1.0",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "💬 ${viewModel.messages.size} 訊息",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
fun ConnectionDialog(
    serverIp: String,
    port: Int,
    clientId: String,
    onConnect: (String, Int, String) -> Unit
) {
    var ip by remember { mutableStateOf(serverIp) }
    var portText by remember { mutableStateOf(port.toString()) }
    var id by remember { mutableStateOf(clientId) }
    
    AlertDialog(
        onDismissRequest = {},
        title = { Text("🔗 連線到伺服器") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("伺服器 IP") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it },
                    label = { Text("Port") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = id,
                    onValueChange = { id = it },
                    label = { Text("你的 ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "💡 輸入本機 IP (例: 192.168.1.100)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConnect(ip, portText.toIntOrNull() ?: 8000, id) }) {
                Text("連線")
            }
        },
        dismissButton = {
            TextButton(onClick = {}) {
                Text("取消")
            }
        }
    )
}

@Composable
fun MessageBubble(message: Message, isFromMe: Boolean) {
    val alignment = if (isFromMe) Alignment.End else Alignment.Start
    val backgroundColor = if (isFromMe) 
        MaterialTheme.colorScheme.primary 
    else 
        MaterialTheme.colorScheme.secondaryContainer
    val textColor = if (isFromMe)
        MaterialTheme.colorScheme.onPrimary
    else
        MaterialTheme.colorScheme.onSecondaryContainer
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        if (!isFromMe) {
            Text(
                text = message.sender,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 12.dp, bottom = 2.dp)
            )
        }
        
        Card(
            modifier = Modifier.widthIn(max = 300.dp),
            shape = RoundedCornerShape(
                topStart = if (isFromMe) 16.dp else 4.dp,
                topEnd = if (isFromMe) 4.dp else 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            ),
            colors = CardDefaults.cardColors(containerColor = backgroundColor)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                when {
                    message.isText -> {
                        Text(
                            text = message.content,
                            color = textColor,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    message.isImage -> {
                        Text(
                            text = message.content.ifEmpty { "🖼️ 圖片" },
                            color = textColor
                        )
                    }
                    message.isVideo -> {
                        Text(
                            text = message.content.ifEmpty { "🎬 影片" },
                            color = textColor
                        )
                    }
                    message.isVoice -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = "Voice",
                                tint = textColor
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "🎤 語音訊息",
                                color = textColor
                            )
                        }
                    }
                    message.isFile -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AttachFile,
                                contentDescription = "File",
                                tint = textColor
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                message.fileName.ifEmpty { "📎 檔案" },
                                color = textColor
                            )
                        }
                    }
                }
                
                Spacer(Modifier.height(4.dp))
                Text(
                    text = formatTime(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageInput(
    message: String,
    onMessageChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    onVoice: () -> Unit,
    showEmojiPicker: Boolean,
    onEmojiClick: (String) -> Unit,
    onEmojiToggle: () -> Unit,
    isRecording: Boolean
) {
    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 8.dp
    ) {
        Column {
            // 表情符號選擇器
            if (showEmojiPicker) {
                EmojiPicker(onEmojiClick = onEmojiClick)
            }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onEmojiToggle) {
                    Icon(
                        if (showEmojiPicker) Icons.Default.Keyboard else Icons.Default.EmojiEmotions,
                        "Emoji"
                    )
                }
                
                IconButton(onClick = onAttach) {
                    Icon(Icons.Default.AttachFile, "Attach")
                }
                
                IconButton(onClick = onVoice) {
                    Icon(
                        if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                        "Voice",
                        tint = if (isRecording) Color.Red else LocalContentColor.current
                    )
                }
                
                OutlinedTextField(
                    value = message,
                    onValueChange = onMessageChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("輸入訊息...") },
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp)
                )
                
                Spacer(Modifier.width(8.dp))
                
                IconButton(
                    onClick = onSend,
                    enabled = message.isNotBlank()
                ) {
                    Icon(
                        Icons.Default.Send,
                        "Send",
                        tint = if (message.isNotBlank()) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            LocalContentColor.current.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
fun EmojiPicker(onEmojiClick: (String) -> Unit) {
    val emojis = listOf(
        "😀", "😂", "😊", "😍", "🤔", "😎", "🥺", "😴",
        "👍", "👎", "❤️", "🔥", "💯", "✨", "🎉", "🙏",
        "👋", "✅", "❌", "⚠️", "💡", "📌", "⭐", "💪"
    )
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        LazyColumn(
            modifier = Modifier.height(150.dp),
            contentPadding = PaddingValues(8.dp)
        ) {
            items(emojis.chunked(8)) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { emoji ->
                        TextButton(onClick = { onEmojiClick(emoji) }) {
                            Text(text = emoji, fontSize = 24.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectionStatus(isConnected: Boolean) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isConnected) Color(0xFF4CAF50) else Color(0xFFF44336)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isConnected) "● 連線" else "○ 離線",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

fun formatTime(timestamp: String): String {
    return try {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.format(Date(timestamp.toLongOrNull() ?: System.currentTimeMillis()))
    } catch (e: Exception) {
        ""
    }
}
