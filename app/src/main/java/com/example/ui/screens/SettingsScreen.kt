package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import android.os.Build
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.clickable
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.rememberCoroutineScope
import com.example.data.model.GenerationParameters
import com.example.engine.EngineLogger
import com.example.ui.components.BackupRestoreCard
import com.example.ui.components.DebugLogDialog
import com.example.ui.components.OpenSourceLicensesDialog
import com.example.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val params by viewModel.parameters.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val selectedProfileId by viewModel.selectedProfileId.collectAsStateWithLifecycle()
    val memory by viewModel.memorySnapshot.collectAsStateWithLifecycle()
    val hardwareHealth by viewModel.hardwareHealth.collectAsStateWithLifecycle()
    val loadedModel by viewModel.loadedModel.collectAsStateWithLifecycle()
    val isBenchmarking by viewModel.isBenchmarking.collectAsStateWithLifecycle()
    val benchmarkResult by viewModel.benchmarkResult.collectAsStateWithLifecycle()
    val engineLogs by EngineLogger.logs.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showLicensesDialog by remember { mutableStateOf(false) }
    var showDebugLogDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshMemory()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // General Settings: Theme
            SettingsSectionCard(title = "Appearance", icon = Icons.Filled.Palette) {
                Text(
                    text = "App Theme",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("SYSTEM", "DARK", "LIGHT").forEach { mode ->
                        FilterChip(
                            selected = themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = { Text(mode) },
                            modifier = Modifier.testTag("theme_chip_$mode")
                        )
                    }
                }
            }

            // System Prompt & Persona Section
            SettingsSectionCard(title = "System Prompt & Persona", icon = Icons.Filled.Tune) {
                Text(
                    text = "Configure the base instruction set given to the model for every conversation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Curated Presets",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GenerationParameters.PRESETS.forEach { preset ->
                        val isSelected = params.systemPrompt == preset.prompt
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.updateSystemPrompt(preset.prompt) },
                            label = { Text(preset.title, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("preset_chip_${preset.id}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                var localPromptText by remember(params.systemPrompt) { mutableStateOf(params.systemPrompt) }

                OutlinedTextField(
                    value = localPromptText,
                    onValueChange = {
                        localPromptText = it
                        viewModel.updateSystemPrompt(it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("system_prompt_input"),
                    minLines = 2,
                    maxLines = 5,
                    textStyle = MaterialTheme.typography.bodySmall,
                    label = { Text("Active System Prompt") },
                    supportingText = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${localPromptText.length} chars")
                            if (localPromptText != GenerationParameters.DEFAULT_SYSTEM_PROMPT) {
                                Text(
                                    text = "Reset to Default",
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable {
                                        localPromptText = GenerationParameters.DEFAULT_SYSTEM_PROMPT
                                        viewModel.updateSystemPrompt(GenerationParameters.DEFAULT_SYSTEM_PROMPT)
                                    }
                                )
                            }
                        }
                    }
                )
            }

            // Inference & Power Profiles Section
            SettingsSectionCard(title = "Inference & Power Profiles", icon = Icons.Filled.Bolt) {
                Text(
                    text = "Preset configurations balanced for throughput, battery life, reasoning, or creativity.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(GenerationParameters.PROFILES) { profile ->
                        FilterChip(
                            selected = selectedProfileId == profile.id,
                            onClick = { viewModel.selectProfile(profile.id) },
                            label = { Text(profile.title) },
                            modifier = Modifier.testTag("profile_chip_${profile.id}")
                        )
                    }
                    if (selectedProfileId == "custom") {
                        item {
                            FilterChip(
                                selected = true,
                                onClick = {},
                                label = { Text("Custom") },
                                modifier = Modifier.testTag("profile_chip_custom")
                            )
                        }
                    }
                }

                val currentProfile = GenerationParameters.PROFILES.firstOrNull { it.id == selectedProfileId }
                if (currentProfile != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = currentProfile.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Threads: ${params.threads} · Context: ${currentProfile.contextSize} · Temp: ${String.format(Locale.US, "%.2f", currentProfile.temperature)} · Max: ${currentProfile.maxTokens}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Generation Parameters: Temperature & Max Tokens
            SettingsSectionCard(title = "Generation Parameters", icon = Icons.Filled.Tune) {
                // Temperature
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Temperature",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = String.format(Locale.US, "%.2f", params.temperature),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "Lower values are more focused and deterministic; higher values increase creativity.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Slider(
                        value = params.temperature,
                        onValueChange = { viewModel.updateTemperature(it) },
                        valueRange = 0.0f..2.0f,
                        steps = 19,
                        modifier = Modifier.testTag("temperature_slider")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Max Output Tokens
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Max Output Tokens",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${params.maxTokens}",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "Maximum token limit for single generation response.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Slider(
                        value = params.maxTokens.toFloat(),
                        onValueChange = { viewModel.updateMaxTokens(it.toInt()) },
                        valueRange = 64f..2048f,
                        steps = 30,
                        modifier = Modifier.testTag("max_tokens_slider")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Context Size
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Context Window Size",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${params.contextSize}",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (params.contextSize > 4096) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Filled.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Large context (>4K) requires significant additional RAM.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    Slider(
                        value = params.contextSize.toFloat(),
                        onValueChange = { viewModel.updateContextSize(it.toInt()) },
                        valueRange = 512f..8192f,
                        steps = 14,
                        modifier = Modifier.testTag("context_size_slider")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Top-P & Top-K in row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Top-P", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = String.format(Locale.US, "%.2f", params.topP),
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = params.topP,
                            onValueChange = { viewModel.updateTopP(it) },
                            valueRange = 0.1f..1.0f,
                            steps = 8
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Top-K", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "${params.topK}",
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = params.topK.toFloat(),
                            onValueChange = { viewModel.updateTopK(it.toInt()) },
                            valueRange = 1f..100f,
                            steps = 19
                        )
                    }
                }
            }

            // Performance & CPU Threads
            SettingsSectionCard(title = "Hardware & Performance", icon = Icons.Filled.Speed) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "CPU Threads",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${params.threads} threads",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "Optimal thread count is typically the number of performance cores.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    val maxCores = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
                    Slider(
                        value = params.threads.toFloat(),
                        onValueChange = { viewModel.updateThreads(it.toInt()) },
                        valueRange = 1f..maxCores.toFloat(),
                        steps = maxCores - 2,
                        modifier = Modifier.testTag("threads_slider")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // RAM Snapshot & Hardware Info
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Device Memory (RAM)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${memory.availMemFormatted} free of ${memory.totalMemFormatted}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        OutlinedButton(
                            onClick = { viewModel.refreshMemory() },
                            modifier = Modifier.testTag("refresh_memory_button")
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Refresh")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "Generic"
                    val totalCores = Runtime.getRuntime().availableProcessors()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Architecture / Cores",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = "$primaryAbi · $totalCores cores",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    val recommendedModel = when {
                        memory.totalMemBytes < 4L * 1024 * 1024 * 1024 -> "≤ 1.5B Q4_K_M (Lightweight)"
                        memory.totalMemBytes < 8L * 1024 * 1024 * 1024 -> "1.5B – 3B Q4_K_M (Balanced)"
                        else -> "Up to 7B – 8B Q4_K_M (High Capacity)"
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Recommended Model Size",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = recommendedModel,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Battery Health & Temperature
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Battery & Temperature",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (hardwareHealth.isCharging) Icons.Filled.BatteryChargingFull else Icons.Filled.BatteryFull,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${hardwareHealth.batteryLevel}% · ${String.format(Locale.US, "%.1f°C", hardwareHealth.batteryTempCelsius)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Thermal Status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Thermal State",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Thermostat,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (hardwareHealth.isThrottling) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = hardwareHealth.thermalStatusText,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (hardwareHealth.isThrottling) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }

                    if (hardwareHealth.isThrottling) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Filled.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Device is warm/throttling. Consider switching to the Battery Saver profile.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // Inference Benchmark Section
            SettingsSectionCard(title = "Inference Benchmark", icon = Icons.Filled.Speed) {
                Text(
                    text = "Measure prompt evaluation prefill speed, token sampling throughput, and latency on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (loadedModel != null) {
                    Text(
                        text = "Active Model: ${loadedModel!!.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (isBenchmarking) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Running standard 64-token benchmark...",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    } else if (benchmarkResult != null) {
                        val res = benchmarkResult!!
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Generation Speed", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = String.format(Locale.US, "%.1f tok/s", res.generationSpeedTokPerSec),
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Prompt Prefill Speed", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = String.format(Locale.US, "%.1f tok/s", res.promptSpeedTokPerSec),
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Time to First Token (TTFT)", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = "${res.timeToFirstTokenMs} ms",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Total Evaluated Tokens", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = "${res.promptTokens} in / ${res.generatedTokens} out",
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { viewModel.runBenchmark() },
                        enabled = !isBenchmarking,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("run_benchmark_button")
                    ) {
                        Icon(Icons.Filled.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (benchmarkResult != null) "Re-run Benchmark (64 tokens)" else "Run Inference Benchmark")
                    }
                } else {
                    Text(
                        text = "No model is currently loaded. Load a model in the Models tab to benchmark inference performance.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            // About & Diagnostics Section
            SettingsSectionCard(title = "About & Diagnostics", icon = Icons.Filled.Info) {
                Text(
                    text = "Minimal Local GGUF LLM Client v1.0",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Powered by llama.cpp. Runs completely offline on-device without cloud connectivity or server dependencies.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Privacy & Architecture pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "100% Offline · Zero Internet Permissions · Local Storage Only",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Engine Diagnostic Logs Button
                OutlinedButton(
                    onClick = { showDebugLogDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_debug_logs_button")
                ) {
                    Icon(Icons.Filled.BugReport, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Engine Diagnostic Logs (${engineLogs.size})")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Open-Source Licenses Button
                OutlinedButton(
                    onClick = { showLicensesDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_licenses_button")
                ) {
                    Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open-Source Licenses (llama.cpp, GGML)")
                }
            }

            // Backup & Restore
            BackupRestoreCard(
                onExportBackup = { viewModel.exportBackupJson() },
                onImportBackup = { json -> viewModel.importBackupJson(json) },
                onShowSnackbar = { msg ->
                    scope.launch { snackbarHostState.showSnackbar(msg) }
                }
            )

            // Reset defaults button
            OutlinedButton(
                onClick = { viewModel.resetToDefaults() },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reset_defaults_button")
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reset All Parameters to Defaults")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showLicensesDialog) {
        OpenSourceLicensesDialog(
            onDismiss = { showLicensesDialog = false }
        )
    }

    if (showDebugLogDialog) {
        DebugLogDialog(
            onDismiss = { showDebugLogDialog = false }
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}
