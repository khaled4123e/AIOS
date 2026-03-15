// Copyright 2026 AIOS Contributors
// SPDX-License-Identifier: Apache-2.0

package dev.aios.shell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aios.shell.ai.BackendType
import kotlin.math.roundToInt

// Colors (shared with AIOSApp)
private val BgDark = Color(0xFF0A0E1A)
private val BgGradientTop = Color(0xFF0F1629)
private val BgGradientBottom = Color(0xFF060A14)
private val AvatarBlue = Color(0xFF3B82F6)
private val AvatarGreen = Color(0xFF10B981)
private val AvatarAmber = Color(0xFFF59E0B)
private val AvatarCyan = Color(0xFF06B6D4)
private val CardBg = Color(0xFF1E293B)
private val CardBgLight = Color(0xFF334155)
private val TextPrimary = Color(0xFFF1F5F9)
private val TextSecondary = Color(0xFF94A3B8)
private val TextMuted = Color(0xFF64748B)

private fun BackendType.label(): String = when (this) {
    BackendType.LOCAL -> "Lokales Modell (On-Device)"
    BackendType.EXTERNAL_API -> "Externe API"
    BackendType.REMOTE_SERVER -> "Remote Server"
}

private data class LocalModel(
    val id: String,
    val name: String,
    val size: String,
)

private val availableModels = listOf(
    LocalModel("qwen2.5-3b", "Qwen 2.5 3B (Empfohlen)", "~2 GB"),
    LocalModel("gemma2-2b", "Gemma 2 2B", "~1.5 GB"),
    LocalModel("phi3.5-mini", "Phi 3.5 Mini 3.8B", "~2.2 GB"),
    LocalModel("tinyllama-1.1b", "TinyLlama 1.1B (Schnell)", "~600 MB"),
)

private enum class ApiProvider(val label: String, val defaultModel: String) {
    CLAUDE("Claude (Anthropic)", "claude-sonnet-4-20250514"),
    OPENAI("OpenAI", "gpt-4o-mini"),
    CUSTOM("Benutzerdefiniert", ""),
}

private val contextSizeOptions = listOf(512, 1024, 2048, 4096)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    initialBackendType: BackendType = BackendType.LOCAL,
    onBackendChanged: (type: BackendType) -> Unit = {},
    onApiConfigured: (type: String, key: String, model: String, url: String?) -> Unit = { _, _, _, _ -> },
    onLocalModelSelected: (modelName: String) -> Unit = {},
    onSettingChanged: (key: String, value: Any) -> Unit = { _, _ -> },
    onBack: () -> Unit = {},
) {
    var selectedBackend by remember { mutableStateOf(initialBackendType) }

    // Local model state
    var loadedModelName by remember { mutableStateOf("Kein Modell geladen") }
    var loadedModelSize by remember { mutableStateOf("--") }
    var selectedDownloadModel by remember { mutableStateOf<LocalModel?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var threadCount by remember { mutableFloatStateOf(4f) }
    var selectedContextSize by remember { mutableIntStateOf(2048) }
    var showModelDropdown by remember { mutableStateOf(false) }
    var showContextDropdown by remember { mutableStateOf(false) }

    // API state
    var selectedApiProvider by remember { mutableStateOf(ApiProvider.CLAUDE) }
    var apiKey by remember { mutableStateOf("") }
    var apiModel by remember { mutableStateOf(ApiProvider.CLAUDE.defaultModel) }
    var customUrl by remember { mutableStateOf("") }
    var showApiDropdown by remember { mutableStateOf(false) }

    // Remote state
    var remoteUrl by remember { mutableStateOf("http://10.0.2.2:8085") }

    // Generation state
    var temperature by remember { mutableFloatStateOf(0.3f) }
    var maxTokens by remember { mutableFloatStateOf(500f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgGradientTop, BgGradientBottom)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Zurueck",
                        tint = TextPrimary,
                    )
                }
                Text(
                    text = "KI-Einstellungen",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                // -- AI Backend Section --
                item {
                    SectionHeader("KI-Backend")
                }

                item {
                    SettingsCard {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            BackendType.entries.forEach { backend ->
                                val status = when (backend) {
                                    BackendType.LOCAL -> if (loadedModelName != "Kein Modell geladen")
                                        "verfuegbar" else "nicht verfuegbar"
                                    BackendType.EXTERNAL_API -> if (apiKey.isNotBlank())
                                        "verfuegbar" else "nicht verfuegbar"
                                    BackendType.REMOTE_SERVER -> "nicht verfuegbar"
                                }
                                val statusColor = if (status == "verfuegbar") AvatarGreen else AvatarAmber

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            selectedBackend = backend
                                            onBackendChanged(backend)
                                        }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = selectedBackend == backend,
                                        onClick = {
                                            selectedBackend = backend
                                            onBackendChanged(backend)
                                        },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = AvatarBlue,
                                            unselectedColor = TextMuted,
                                        ),
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = backend.label(),
                                            color = TextPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Text(
                                            text = status,
                                            color = statusColor,
                                            fontSize = 11.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // -- Lokales Modell Section --
                if (selectedBackend == BackendType.LOCAL) {
                    item {
                        SectionHeader("Lokales Modell")
                    }

                    item {
                        SettingsCard {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                // Current model info
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column {
                                        Text(
                                            text = "Geladenes Modell",
                                            color = TextMuted,
                                            fontSize = 11.sp,
                                        )
                                        Text(
                                            text = loadedModelName,
                                            color = TextPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "Groesse",
                                            color = TextMuted,
                                            fontSize = 11.sp,
                                        )
                                        Text(
                                            text = loadedModelSize,
                                            color = TextSecondary,
                                            fontSize = 14.sp,
                                        )
                                    }
                                }

                                HorizontalDivider(color = CardBgLight, thickness = 1.dp)

                                // Model selection dropdown
                                Text(
                                    text = "Modell herunterladen",
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                )

                                Box {
                                    OutlinedButton(
                                        onClick = { showModelDropdown = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = TextPrimary,
                                        ),
                                        border = ButtonDefaults.outlinedButtonBorder(enabled = true),
                                    ) {
                                        Text(
                                            text = selectedDownloadModel?.let {
                                                "${it.name} (${it.size})"
                                            } ?: "Modell auswaehlen...",
                                            modifier = Modifier.weight(1f),
                                            fontSize = 13.sp,
                                            color = if (selectedDownloadModel != null) TextPrimary else TextMuted,
                                        )
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = TextMuted,
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = showModelDropdown,
                                        onDismissRequest = { showModelDropdown = false },
                                        modifier = Modifier.background(CardBg),
                                    ) {
                                        availableModels.forEach { model ->
                                            DropdownMenuItem(
                                                text = {
                                                    Column {
                                                        Text(
                                                            text = model.name,
                                                            color = TextPrimary,
                                                            fontSize = 13.sp,
                                                        )
                                                        Text(
                                                            text = model.size,
                                                            color = TextMuted,
                                                            fontSize = 11.sp,
                                                        )
                                                    }
                                                },
                                                onClick = {
                                                    selectedDownloadModel = model
                                                    showModelDropdown = false
                                                },
                                            )
                                        }
                                    }
                                }

                                // Download button + progress
                                Button(
                                    onClick = {
                                        selectedDownloadModel?.let { model ->
                                            isDownloading = true
                                            downloadProgress = 0f
                                            onLocalModelSelected(model.id)
                                        }
                                    },
                                    enabled = selectedDownloadModel != null && !isDownloading,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AvatarBlue,
                                        disabledContainerColor = AvatarBlue.copy(alpha = 0.3f),
                                    ),
                                ) {
                                    Text(
                                        text = if (isDownloading) "Wird heruntergeladen..." else "Modell herunterladen",
                                        fontSize = 14.sp,
                                    )
                                }

                                if (isDownloading) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        LinearProgressIndicator(
                                            progress = { downloadProgress },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp)),
                                            color = AvatarBlue,
                                            trackColor = CardBgLight,
                                        )
                                        Text(
                                            text = "${(downloadProgress * 100).roundToInt()}%",
                                            color = TextMuted,
                                            fontSize = 11.sp,
                                        )
                                    }
                                }

                                HorizontalDivider(color = CardBgLight, thickness = 1.dp)

                                // Thread count
                                Text(
                                    text = "Threads: ${threadCount.roundToInt()}",
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                Slider(
                                    value = threadCount,
                                    onValueChange = { threadCount = it },
                                    onValueChangeFinished = {
                                        onSettingChanged("threadCount", threadCount.roundToInt())
                                    },
                                    valueRange = 1f..8f,
                                    steps = 6,
                                    colors = SliderDefaults.colors(
                                        thumbColor = AvatarBlue,
                                        activeTrackColor = AvatarBlue,
                                        inactiveTrackColor = CardBgLight,
                                    ),
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("1", color = TextMuted, fontSize = 11.sp)
                                    Text("8", color = TextMuted, fontSize = 11.sp)
                                }

                                HorizontalDivider(color = CardBgLight, thickness = 1.dp)

                                // Context size
                                Text(
                                    text = "Kontextgroesse",
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                )

                                Box {
                                    OutlinedButton(
                                        onClick = { showContextDropdown = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = TextPrimary,
                                        ),
                                    ) {
                                        Text(
                                            text = "$selectedContextSize Tokens",
                                            modifier = Modifier.weight(1f),
                                            fontSize = 13.sp,
                                            color = TextPrimary,
                                        )
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = TextMuted,
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = showContextDropdown,
                                        onDismissRequest = { showContextDropdown = false },
                                        modifier = Modifier.background(CardBg),
                                    ) {
                                        contextSizeOptions.forEach { size ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = "$size Tokens",
                                                        color = if (size == selectedContextSize) AvatarBlue else TextPrimary,
                                                        fontSize = 13.sp,
                                                    )
                                                },
                                                onClick = {
                                                    selectedContextSize = size
                                                    showContextDropdown = false
                                                    onSettingChanged("contextSize", size)
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // -- Externe API Section --
                if (selectedBackend == BackendType.EXTERNAL_API) {
                    item {
                        SectionHeader("Externe API")
                    }

                    item {
                        SettingsCard {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                // Provider dropdown
                                Text(
                                    text = "Anbieter",
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                )

                                Box {
                                    OutlinedButton(
                                        onClick = { showApiDropdown = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = TextPrimary,
                                        ),
                                    ) {
                                        Text(
                                            text = selectedApiProvider.label,
                                            modifier = Modifier.weight(1f),
                                            fontSize = 13.sp,
                                            color = TextPrimary,
                                        )
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = TextMuted,
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = showApiDropdown,
                                        onDismissRequest = { showApiDropdown = false },
                                        modifier = Modifier.background(CardBg),
                                    ) {
                                        ApiProvider.entries.forEach { provider ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = provider.label,
                                                        color = if (provider == selectedApiProvider) AvatarBlue else TextPrimary,
                                                        fontSize = 13.sp,
                                                    )
                                                },
                                                onClick = {
                                                    selectedApiProvider = provider
                                                    apiModel = provider.defaultModel
                                                    showApiDropdown = false
                                                },
                                            )
                                        }
                                    }
                                }

                                // API Key
                                Text(
                                    text = "API-Schluessel",
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                OutlinedTextField(
                                    value = apiKey,
                                    onValueChange = { apiKey = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = {
                                        Text("Schluessel eingeben...", color = TextMuted)
                                    },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Password,
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = AvatarBlue,
                                        unfocusedBorderColor = CardBgLight,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary,
                                        cursorColor = AvatarBlue,
                                    ),
                                )

                                // Model name
                                Text(
                                    text = "Modellname",
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                OutlinedTextField(
                                    value = apiModel,
                                    onValueChange = { apiModel = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = {
                                        Text("Modellname eingeben...", color = TextMuted)
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = AvatarBlue,
                                        unfocusedBorderColor = CardBgLight,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary,
                                        cursorColor = AvatarBlue,
                                    ),
                                )

                                // Custom URL (only for Benutzerdefiniert)
                                if (selectedApiProvider == ApiProvider.CUSTOM) {
                                    Text(
                                        text = "Benutzerdefinierte URL",
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    OutlinedTextField(
                                        value = customUrl,
                                        onValueChange = { customUrl = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = {
                                            Text("https://api.example.com/v1", color = TextMuted)
                                        },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = AvatarBlue,
                                            unfocusedBorderColor = CardBgLight,
                                            focusedTextColor = TextPrimary,
                                            unfocusedTextColor = TextPrimary,
                                            cursorColor = AvatarBlue,
                                        ),
                                    )
                                }

                                // Test connection button
                                Button(
                                    onClick = {
                                        onApiConfigured(
                                            selectedApiProvider.name,
                                            apiKey,
                                            apiModel,
                                            if (selectedApiProvider == ApiProvider.CUSTOM) customUrl else null,
                                        )
                                    },
                                    enabled = apiKey.isNotBlank() && apiModel.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AvatarGreen,
                                        disabledContainerColor = AvatarGreen.copy(alpha = 0.3f),
                                    ),
                                ) {
                                    Text("Verbindung testen", fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }

                // -- Remote Server Section --
                if (selectedBackend == BackendType.REMOTE_SERVER) {
                    item {
                        SectionHeader("Remote Server")
                    }

                    item {
                        SettingsCard {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = "Server-URL",
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                OutlinedTextField(
                                    value = remoteUrl,
                                    onValueChange = { remoteUrl = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = {
                                        Text("http://10.0.2.2:8085", color = TextMuted)
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = AvatarBlue,
                                        unfocusedBorderColor = CardBgLight,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary,
                                        cursorColor = AvatarBlue,
                                    ),
                                )

                                Button(
                                    onClick = {
                                        onApiConfigured("REMOTE", "", "", remoteUrl)
                                    },
                                    enabled = remoteUrl.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AvatarGreen,
                                        disabledContainerColor = AvatarGreen.copy(alpha = 0.3f),
                                    ),
                                ) {
                                    Text("Verbindung testen", fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }

                // -- Generierung Section (always shown) --
                item {
                    SectionHeader("Generierung")
                }

                item {
                    SettingsCard {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Temperature
                            Text(
                                text = "Temperatur: ${"%.1f".format(temperature)}",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Slider(
                                value = temperature,
                                onValueChange = { temperature = it },
                                onValueChangeFinished = {
                                    onSettingChanged("temperature", temperature)
                                },
                                valueRange = 0f..1f,
                                colors = SliderDefaults.colors(
                                    thumbColor = AvatarCyan,
                                    activeTrackColor = AvatarCyan,
                                    inactiveTrackColor = CardBgLight,
                                ),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("0.0 (Praezise)", color = TextMuted, fontSize = 11.sp)
                                Text("1.0 (Kreativ)", color = TextMuted, fontSize = 11.sp)
                            }

                            HorizontalDivider(color = CardBgLight, thickness = 1.dp)

                            // Max Tokens
                            Text(
                                text = "Max Tokens: ${maxTokens.roundToInt()}",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Slider(
                                value = maxTokens,
                                onValueChange = { maxTokens = it },
                                onValueChangeFinished = {
                                    onSettingChanged("maxTokens", maxTokens.roundToInt())
                                },
                                valueRange = 100f..2000f,
                                colors = SliderDefaults.colors(
                                    thumbColor = AvatarCyan,
                                    activeTrackColor = AvatarCyan,
                                    inactiveTrackColor = CardBgLight,
                                ),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("100", color = TextMuted, fontSize = 11.sp)
                                Text("2000", color = TextMuted, fontSize = 11.sp)
                            }
                        }
                    }
                }

                // Bottom spacer
                item {
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = AvatarBlue,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardBg,
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}
