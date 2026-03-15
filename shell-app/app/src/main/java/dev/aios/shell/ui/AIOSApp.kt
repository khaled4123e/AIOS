// Copyright 2026 AIOS Contributors
// SPDX-License-Identifier: Apache-2.0

package dev.aios.shell.ui

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.aios.shell.policy.Decision
import dev.aios.shell.viewmodel.*
import java.text.SimpleDateFormat
import java.util.*

// Colors
private val BgDark = Color(0xFF0A0E1A)
private val BgGradientTop = Color(0xFF0F1629)
private val BgGradientBottom = Color(0xFF060A14)
private val AvatarBlue = Color(0xFF3B82F6)
private val AvatarGreen = Color(0xFF10B981)
private val AvatarAmber = Color(0xFFF59E0B)
private val AvatarRed = Color(0xFFEF4444)
private val AvatarPurple = Color(0xFF8B5CF6)
private val AvatarCyan = Color(0xFF06B6D4)
private val CardBg = Color(0xFF1E293B)
private val CardBgLight = Color(0xFF334155)
private val TextPrimary = Color(0xFFF1F5F9)
private val TextSecondary = Color(0xFF94A3B8)
private val TextMuted = Color(0xFF64748B)

private enum class Screen {
    LAUNCHER,
    SETTINGS,
}

@Composable
fun AIOSApp(
    context: Context,
    viewModel: ShellViewModel = viewModel(factory = ShellViewModel.Factory(context)),
) {
    val uiState by viewModel.uiState.collectAsState()
    var currentScreen by remember { mutableStateOf(Screen.LAUNCHER) }

    when (currentScreen) {
        Screen.LAUNCHER -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(BgGradientTop, BgGradientBottom)))
            ) {
                LauncherScreen(
                    uiState = uiState,
                    onAvatarTap = { viewModel.onAvatarTapped() },
                    onSettingsTap = { currentScreen = Screen.SETTINGS },
                )

                if (uiState.isChatOpen) {
                    ChatOverlay(
                        uiState = uiState,
                        onSend = { viewModel.onUserInput(it) },
                        onDismiss = { viewModel.onChatDismiss() },
                        onApprove = { viewModel.onApprove() },
                        onDeny = { viewModel.onDeny() },
                        onTyping = { viewModel.onUserTyping() },
                    )
                }
            }
        }
        Screen.SETTINGS -> {
            SettingsScreen(
                onBackendChanged = { type ->
                    viewModel.setPreferredBackend(type)
                },
                onApiConfigured = { type, key, model, url ->
                    viewModel.configureExternalAPI(type, key, model, url)
                },
                onLocalModelSelected = { modelName ->
                    viewModel.configureLocalModel(modelName)
                },
                onSettingChanged = { key, value ->
                    viewModel.updateSetting(key, value)
                },
                onBack = { currentScreen = Screen.LAUNCHER },
            )
        }
    }
}

@Composable
fun LauncherScreen(uiState: ShellUiState, onAvatarTap: () -> Unit, onSettingsTap: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Settings button at top-right
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(onClick = onSettingsTap) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Einstellungen",
                    tint = TextMuted,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        ClockWidget()
        Spacer(Modifier.height(48.dp))
        AIOSAvatar(
            mood = uiState.avatarMood,
            statusText = uiState.statusText,
            onTap = onAvatarTap,
        )
        Spacer(Modifier.height(32.dp))
        StatusCards(
            toolCount = uiState.toolCount,
            auditCount = uiState.auditCount,
            aiAvailable = uiState.aiAvailable,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "Tippe den Avatar an um zu sprechen",
            color = TextMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 32.dp),
        )
    }
}

@Composable
fun ClockWidget() {
    val currentTime = remember { mutableStateOf(getCurrentTime()) }
    val currentDate = remember { mutableStateOf(getCurrentDate()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime.value = getCurrentTime()
            currentDate.value = getCurrentDate()
            kotlinx.coroutines.delay(1000)
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = currentTime.value,
            color = TextPrimary,
            fontSize = 64.sp,
            fontWeight = FontWeight.Thin,
            fontFamily = FontFamily.Default,
            letterSpacing = 4.sp,
        )
        Text(
            text = currentDate.value,
            color = TextSecondary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Light,
        )
    }
}

private fun getCurrentTime(): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
}

private fun getCurrentDate(): String {
    return SimpleDateFormat("EEEE, d. MMMM", Locale.GERMAN).format(Date())
}

@Composable
fun AIOSAvatar(mood: AvatarMood, statusText: String, onTap: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "avatar")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = when (mood) {
            AvatarMood.IDLE -> 1.05f
            AvatarMood.THINKING -> 1.1f
            AvatarMood.EXECUTING -> 1.08f
            AvatarMood.ALERT -> 1.12f
            else -> 1.03f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (mood) {
                    AvatarMood.THINKING -> 600
                    AvatarMood.EXECUTING -> 400
                    AvatarMood.ALERT -> 500
                    else -> 2000
                },
                easing = EaseInOutSine,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    val glowColor by animateColorAsState(
        targetValue = when (mood) {
            AvatarMood.IDLE -> AvatarBlue.copy(alpha = 0.3f)
            AvatarMood.LISTENING -> AvatarPurple.copy(alpha = 0.4f)
            AvatarMood.THINKING -> AvatarCyan.copy(alpha = 0.5f)
            AvatarMood.EXECUTING -> AvatarBlue.copy(alpha = 0.6f)
            AvatarMood.SUCCESS -> AvatarGreen.copy(alpha = 0.5f)
            AvatarMood.BLOCKED -> AvatarRed.copy(alpha = 0.4f)
            AvatarMood.ALERT -> AvatarAmber.copy(alpha = 0.5f)
        },
        animationSpec = tween(500),
        label = "glow",
    )

    val coreColor by animateColorAsState(
        targetValue = when (mood) {
            AvatarMood.IDLE -> AvatarBlue
            AvatarMood.LISTENING -> AvatarPurple
            AvatarMood.THINKING -> AvatarCyan
            AvatarMood.EXECUTING -> AvatarBlue
            AvatarMood.SUCCESS -> AvatarGreen
            AvatarMood.BLOCKED -> AvatarRed
            AvatarMood.ALERT -> AvatarAmber
        },
        animationSpec = tween(500),
        label = "core",
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .scale(pulseScale)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onTap,
                ),
            contentAlignment = Alignment.Center,
        ) {
            // Outer glow
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .shadow(24.dp, CircleShape, ambientColor = glowColor, spotColor = glowColor)
                    .clip(CircleShape)
                    .background(glowColor.copy(alpha = 0.1f))
            )
            // Middle ring
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(coreColor.copy(alpha = 0.2f), Color.Transparent)
                        )
                    )
            )
            // Core orb
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .shadow(16.dp, CircleShape, ambientColor = coreColor, spotColor = coreColor)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(coreColor, coreColor.copy(alpha = 0.7f))
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "AI",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = statusText,
            color = TextSecondary,
            fontSize = 14.sp,
        )

        Text(
            text = when (mood) {
                AvatarMood.IDLE -> "AIOS"
                AvatarMood.LISTENING -> "Hoere zu..."
                AvatarMood.THINKING -> "Denke nach..."
                AvatarMood.EXECUTING -> "Fuehre aus..."
                AvatarMood.SUCCESS -> "Erledigt"
                AvatarMood.BLOCKED -> "Blockiert"
                AvatarMood.ALERT -> "Warte auf Freigabe"
            },
            color = TextMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
fun StatusCards(toolCount: Int, auditCount: Int, aiAvailable: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusCard(
            title = "System",
            value = "Aktiv",
            detail = "$toolCount Tools bereit",
            color = AvatarGreen,
            modifier = Modifier.weight(1f),
        )
        StatusCard(
            title = "KI",
            value = if (aiAvailable) "Online" else "Offline",
            detail = if (aiAvailable) "LLM aktiv" else "Keyword-Modus",
            color = if (aiAvailable) AvatarCyan else AvatarAmber,
            modifier = Modifier.weight(1f),
        )
        StatusCard(
            title = "Audit",
            value = "$auditCount",
            detail = "Aktionen",
            color = AvatarPurple,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun StatusCard(
    title: String,
    value: String,
    detail: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = CardBg,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = title, color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text(text = value, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(text = detail, color = TextSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
fun ChatOverlay(
    uiState: ShellUiState,
    onSend: (String) -> Unit,
    onDismiss: () -> Unit,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    onTyping: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
                .align(Alignment.BottomCenter)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { },
                ),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = BgDark,
        ) {
            Column {
                // Handle bar
                Box(
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(TextMuted)
                        .align(Alignment.CenterHorizontally)
                )

                // Messages
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

                LaunchedEffect(uiState.messages.size) {
                    if (uiState.messages.isNotEmpty()) {
                        listState.animateScrollToItem(uiState.messages.size - 1)
                    }
                }

                if (uiState.pendingAction != null) {
                    ApprovalBanner(
                        consentRequired = uiState.pendingAction?.consentRequired,
                        onApprove = onApprove,
                        onDeny = onDeny,
                    )
                }

                ChatInput(
                    isProcessing = uiState.isProcessing,
                    onSend = onSend,
                    onTyping = onTyping,
                )
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val isUser = message.isUser
    val bubbleColor = when {
        isUser -> AvatarBlue
        message.type == MessageType.PLAN -> CardBg
        message.type == MessageType.MULTI_STEP -> Color(0xFF1E3A5F)
        message.type == MessageType.AI_THINKING -> Color(0xFF164E63)
        message.type == MessageType.POLICY_RESULT -> when (message.decision) {
            Decision.ALLOW, Decision.ALLOW_WITH_LOG -> Color(0xFF064E3B)
            Decision.REQUIRE_CONFIRMATION -> Color(0xFF78350F)
            Decision.DENY, Decision.QUARANTINE -> Color(0xFF7F1D1D)
            else -> CardBg
        }
        message.type == MessageType.TOOL_RESULT -> Color(0xFF064E3B)
        message.type == MessageType.ERROR -> Color(0xFF7F1D1D)
        else -> CardBg
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp,
                ))
                .background(bubbleColor)
                .padding(12.dp),
        ) {
            val useMonospace = message.type == MessageType.PLAN ||
                message.type == MessageType.POLICY_RESULT ||
                message.type == MessageType.MULTI_STEP ||
                message.type == MessageType.AI_THINKING

            Text(
                text = message.text,
                color = if (isUser) Color.White
                    else if (useMonospace) TextSecondary
                    else TextPrimary,
                fontSize = if (useMonospace) 12.sp else 14.sp,
                fontFamily = if (useMonospace) FontFamily.Monospace else FontFamily.Default,
                lineHeight = if (useMonospace) 17.sp else 20.sp,
            )
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
        color = Color(0xFF78350F),
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Bestaetigung erforderlich",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.White,
                )
                Text(
                    "Stufe: ${consentRequired ?: "standard"}",
                    fontSize = 11.sp,
                    color = Color(0xFFFCD34D),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onDeny,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                ) {
                    Text("Nein", fontSize = 12.sp)
                }
                Button(
                    onClick = onApprove,
                    colors = ButtonDefaults.buttonColors(containerColor = AvatarGreen),
                ) {
                    Text("Freigeben", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun ChatInput(
    isProcessing: Boolean,
    onSend: (String) -> Unit,
    onTyping: () -> Unit,
) {
    var text by remember { mutableStateOf("") }

    Surface(color = CardBg) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth()
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    onTyping()
                },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Was soll ich tun?", color = TextMuted) },
                enabled = !isProcessing,
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AvatarBlue,
                    unfocusedBorderColor = CardBgLight,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = AvatarBlue,
                ),
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
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = AvatarBlue),
                contentPadding = PaddingValues(12.dp),
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                } else {
                    Text(">", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
