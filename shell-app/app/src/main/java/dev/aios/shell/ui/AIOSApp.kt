// Copyright 2026 AIOS Contributors
// SPDX-License-Identifier: Apache-2.0

package dev.aios.shell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.aios.shell.policy.Decision
import dev.aios.shell.viewmodel.*

/// AIOSApp — the root composable. Like your ContentView in SwiftUI.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIOSApp(viewModel: ShellViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "AIOS Shell",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                        )
                        Text(
                            "AI-native Operating System",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Chat messages
            val listState = rememberLazyListState()

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(uiState.messages) { message ->
                    ChatBubble(message)
                }
            }

            // Auto-scroll to bottom
            LaunchedEffect(uiState.messages.size) {
                if (uiState.messages.isNotEmpty()) {
                    listState.animateScrollToItem(uiState.messages.size - 1)
                }
            }

            // Approval banner (if pending)
            if (uiState.pendingAction != null) {
                ApprovalBanner(
                    consentRequired = uiState.pendingAction?.consentRequired,
                    onApprove = { viewModel.onApprove() },
                    onDeny = { viewModel.onDeny() },
                )
            }

            // Input bar
            InputBar(
                isProcessing = uiState.isProcessing,
                onSend = { viewModel.onUserInput(it) },
            )
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val isUser = message.isUser
    val bubbleColor = when {
        isUser -> MaterialTheme.colorScheme.primary
        message.type == MessageType.PLAN -> Color(0xFFEEF2FF)
        message.type == MessageType.POLICY_RESULT -> when (message.decision) {
            Decision.ALLOW, Decision.ALLOW_WITH_LOG -> Color(0xFFECFDF5)
            Decision.REQUIRE_CONFIRMATION -> Color(0xFFFFFBEB)
            Decision.DENY, Decision.QUARANTINE -> Color(0xFFFEF2F2)
            else -> MaterialTheme.colorScheme.surfaceVariant
        }
        message.type == MessageType.TOOL_RESULT -> Color(0xFFECFDF5)
        message.type == MessageType.ERROR -> Color(0xFFFEF2F2)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (isUser) Color.White else Color(0xFF1E293B)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp,
                ))
                .background(bubbleColor)
                .padding(12.dp),
        ) {
            if (message.type == MessageType.PLAN || message.type == MessageType.POLICY_RESULT) {
                Text(
                    text = message.text,
                    color = textColor,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp,
                )
            } else {
                Text(
                    text = message.text,
                    color = textColor,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                )
            }
        }
    }
}

@Composable
fun ApprovalBanner(
    consentRequired: String?,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFFFFBEB),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "⚠️ Bestätigung erforderlich",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
                Text(
                    "Stufe: ${consentRequired ?: "standard"}",
                    fontSize = 12.sp,
                    color = Color(0xFF92400E),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDeny) {
                    Text("Ablehnen")
                }
                Button(onClick = onApprove) {
                    Text("Freigeben")
                }
            }
        }
    }
}

@Composable
fun InputBar(
    isProcessing: Boolean,
    onSend: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Was soll ich tun?") },
                enabled = !isProcessing,
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (text.isNotBlank()) {
                            onSend(text)
                            text = ""
                        }
                    }
                ),
            )
            Button(
                onClick = {
                    if (text.isNotBlank()) {
                        onSend(text)
                        text = ""
                    }
                },
                enabled = text.isNotBlank() && !isProcessing,
                shape = RoundedCornerShape(24.dp),
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                } else {
                    Text("Senden")
                }
            }
        }
    }
}
