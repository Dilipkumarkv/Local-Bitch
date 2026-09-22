package com.dilipkumarkv.localgguf.ui.screens

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dilipkumarkv.localgguf.data.model.ModelEntity
import com.dilipkumarkv.localgguf.engine.MemorySafetyHelper
import com.dilipkumarkv.localgguf.ui.components.GgufInspectorSheet
import com.dilipkumarkv.localgguf.ui.components.MemoryWarningDialog
import com.dilipkumarkv.localgguf.ui.components.ModelCard
import com.dilipkumarkv.localgguf.ui.components.StorageAnalyzerCard
import com.dilipkumarkv.localgguf.ui.viewmodel.ModelViewModel
import java.util.Locale

enum class ModelSortOption(val title: String) {
    DATE_DESC("Recent"),
    NAME_ASC("A-Z"),
    SIZE_DESC("Largest"),
    SIZE_ASC("Smallest")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(
    viewModel: ModelViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val models by viewModel.allModels.collectAsStateWithLifecycle()
    val loadedModel by viewModel.loadedModel.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val memoryWarningModel by viewModel.memoryWarningModel.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.importModel(it, context.contentResolver)
        }
    }

    var modelToRename by remember { mutableStateOf<ModelEntity?>(null) }
    var renameInputText by remember { mutableStateOf("") }
    var inspectedModel by remember { mutableStateOf<ModelEntity?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedArchFilter by remember { mutableStateOf<String?>(null) }
    var sortOption by remember { mutableStateOf(ModelSortOption.DATE_DESC) }

    val availableArchitectures = remember(models) {
        models.map { it.architecture.lowercase(Locale.US) }.filter { it.isNotBlank() }.distinct().sorted()
    }

    val filteredAndSortedModels = remember(models, searchQuery, selectedArchFilter, sortOption) {
        models
            .filter { model ->
                val matchesSearch = searchQuery.isBlank() ||
                    model.displayName.contains(searchQuery, ignoreCase = true) ||
                    model.fileName.contains(searchQuery, ignoreCase = true) ||
                    model.architecture.contains(searchQuery, ignoreCase = true) ||
                    model.quantization.contains(searchQuery, ignoreCase = true)

                val matchesArch = selectedArchFilter == null ||
                    model.architecture.equals(selectedArchFilter, ignoreCase = true)

                matchesSearch && matchesArch
            }
            .let { list ->
                when (sortOption) {
                    ModelSortOption.DATE_DESC -> list.sortedByDescending { it.dateAdded }
                    ModelSortOption.NAME_ASC -> list.sortedBy { it.displayName.lowercase(Locale.US) }
                    ModelSortOption.SIZE_DESC -> list.sortedByDescending { it.fileSize }
                    ModelSortOption.SIZE_ASC -> list.sortedBy { it.fileSize }
                }
            }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Model Library", fontWeight = FontWeight.Bold)
                        val memory = MemorySafetyHelper.getMemorySnapshot(context)
                        Text(
                            text = "RAM Available: ${memory.availMemFormatted} / ${memory.totalMemFormatted}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = { launcher.launch(arrayOf("*/*")) },
                        enabled = !isImporting,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("add_gguf_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add GGUF")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isImporting) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.testTag("importing_indicator"))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Validating & importing GGUF model...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else if (models.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No GGUF Models Found",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Import downloaded .gguf models from your device storage to start local offline inference.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { launcher.launch(arrayOf("*/*")) },
                        modifier = Modifier.testTag("empty_state_add_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select GGUF File")
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Storage breakdown card
                    item {
                        StorageAnalyzerCard(models = models)
                    }

                    // Search and filter controls
                    item {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search by name, arch, quant...") },
                            leadingIcon = {
                                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Filled.Clear, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("model_search_input")
                        )
                    }

                    // Filter & Sort chips
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Arch filter chips
                            item {
                                FilterChip(
                                    selected = selectedArchFilter == null,
                                    onClick = { selectedArchFilter = null },
                                    label = { Text("All Arch") }
                                )
                            }
                            items(availableArchitectures) { arch ->
                                FilterChip(
                                    selected = selectedArchFilter == arch,
                                    onClick = {
                                        selectedArchFilter = if (selectedArchFilter == arch) null else arch
                                    },
                                    label = { Text(arch.uppercase(Locale.US)) }
                                )
                            }

                            // Sort option chips
                            items(ModelSortOption.values()) { option ->
                                FilterChip(
                                    selected = sortOption == option,
                                    onClick = { sortOption = option },
                                    leadingIcon = {
                                        if (sortOption == option) {
                                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, modifier = Modifier.size(14.dp))
                                        }
                                    },
                                    label = { Text(option.title) }
                                )
                            }
                        }
                    }

                    if (filteredAndSortedModels.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No models match your search filter",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(
                                    onClick = {
                                        searchQuery = ""
                                        selectedArchFilter = null
                                    }
                                ) {
                                    Text("Reset Filters")
                                }
                            }
                        }
                    } else {
                        items(filteredAndSortedModels, key = { it.id }) { model ->
                            val isLoaded = loadedModel?.id == model.id
                            ModelCard(
                                model = model,
                                isLoaded = isLoaded,
                                onLoad = { viewModel.requestLoadModel(model) },
                                onUnload = { viewModel.unloadModel() },
                                onDelete = { viewModel.deleteModel(model) },
                                onRename = {
                                    modelToRename = model
                                    renameInputText = model.displayName
                                },
                                onInspect = {
                                    inspectedModel = model
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // GGUF Metadata Inspector Sheet
    inspectedModel?.let { model ->
        val isLoaded = loadedModel?.id == model.id
        GgufInspectorSheet(
            model = model,
            sheetState = sheetState,
            isLoaded = isLoaded,
            onDismiss = { inspectedModel = null },
            onLoadModel = { viewModel.requestLoadModel(model) }
        )
    }

    // Rename Dialog
    modelToRename?.let { model ->
        AlertDialog(
            onDismissRequest = { modelToRename = null },
            title = { Text("Rename Model") },
            text = {
                OutlinedTextField(
                    value = renameInputText,
                    onValueChange = { renameInputText = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rename_input_field")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.renameModel(model.id, renameInputText)
                        modelToRename = null
                    },
                    modifier = Modifier.testTag("confirm_rename_button")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { modelToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Memory Warning Dialog
    memoryWarningModel?.let { model ->
        MemoryWarningDialog(
            model = model,
            onConfirm = { viewModel.confirmLoadRiskyModel() },
            onDismiss = { viewModel.dismissMemoryWarning() }
        )
    }
}

