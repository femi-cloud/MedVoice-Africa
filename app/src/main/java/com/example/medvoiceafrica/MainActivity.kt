package com.example.medvoiceafrica

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {

    private lateinit var medVoiceEngine: MedVoiceEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        medVoiceEngine = MedVoiceEngine(this)

        setContent {
            MaterialTheme {
                // State to track if the engine is loading
                var isLoading by remember { mutableStateOf(true) }
                val scope = rememberCoroutineScope()

                // Initialize the engine in the background
                LaunchedEffect(Unit) {
                    medVoiceEngine.initialize()
                    isLoading = !medVoiceEngine.isReady
                }

                if (isLoading) {
                    // LOADING SCREEN
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center // Fixed Alignment
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.size(16.dp))
                            Text("Loading MedVoice Brain...")
                        }
                    }
                } else {
                    // MAIN CHAT SCREEN
                    ChatScreen(medVoiceEngine)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        medVoiceEngine.close()
    }
}

@Composable
fun ChatScreen(engine: MedVoiceEngine) {
    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<Pair<String, Boolean>>() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Chat Message List
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(messages) { msg ->
                Text(
                    text = if (msg.second) "You: ${msg.first}" else "MedVoice: ${msg.first}",
                    modifier = Modifier.padding(8.dp),
                    color = if (msg.second) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                )
            }
        }

        // Input Field and Send Button
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask something...") }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                if (inputText.isNotBlank()) {
                    val userMsg = inputText
                    messages.add(userMsg to true)
                    inputText = ""

                    scope.launch {
                        val response = engine.generateResponse(userMsg)
                        messages.add(response to false)
                    }
                }
            }) {
                Text("Send")
            }
        }
    }
}