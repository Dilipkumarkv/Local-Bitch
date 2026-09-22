package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.GbnfGrammarHelper

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StructuredOutputDialog(
    currentType: GbnfGrammarHelper.GrammarType,
    currentGbnf: String?,
    currentSchemaJson: String?,
    onApplyPreset: (GbnfGrammarHelper.GrammarPreset) -> Unit,
    onApplyCustom: (type: GbnfGrammarHelper.GrammarType, gbnf: String?, schemaJson: String?, choices: List<String>?) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(if (currentType != GbnfGrammarHelper.GrammarType.NONE) 0 else 0) }
    var activeType by remember { mutableStateOf(currentType) }
    var customGbnfText by remember { mutableStateOf(currentGbnf ?: GbnfGrammarHelper.GBNF_JSON_OBJECT) }
    var customSchemaText by remember {
        mutableStateOf(
            currentSchemaJson ?: """{
  "type": "object",
  "properties": {
    "summary": { "type": "string" },
    "key_points": { "type": "array" },
    "score": { "type": "integer" }
  },
  "required": ["summary", "score"]
}"""
        )
    }
    var enumChoicesText by remember { mutableStateOf("POSITIVE, NEGATIVE, NEUTRAL") }

    val validationResult = remember(customGbnfText, activeType) {
        if (activeType == GbnfGrammarHelper.GrammarType.CUSTOM) {
            GbnfGrammarHelper.validateGbnf(customGbnfText)
        } else {
            GbnfGrammarHelper.ValidationResult(true, "Valid grammar")
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
            .testTag("structured_output_dialog"),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Rule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Guided Generation & GBNF",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Constrain local LLM sampling using GGML BNF grammars or strict JSON schemas to guarantee valid, structured outputs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Presets", style = MaterialTheme.typography.labelMedium) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Schema / GBNF", style = MaterialTheme.typography.labelMedium) }
                    )
                }

                if (selectedTab == 0) {
                    // Presets List
                    Text(
                        text = "Curated Structured Output Presets",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    GbnfGrammarHelper.BUILT_IN_PRESETS.forEach { preset ->
                        val isCurrent = currentType == preset.type && (currentSchemaJson == preset.schemaJson || preset.schemaJson == null)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onApplyPreset(preset)
                                    onDismiss()
                                }
                                .testTag("preset_item_${preset.id}")
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = when (preset.type) {
                                                GbnfGrammarHelper.GrammarType.JSON_SCHEMA -> Icons.Filled.DataObject
                                                GbnfGrammarHelper.GrammarType.CHOICE_ENUM -> Icons.AutoMirrored.Filled.ListAlt
                                                GbnfGrammarHelper.GrammarType.BOOLEAN -> Icons.Filled.Check
                                                else -> Icons.Filled.Code
                                            },
                                            contentDescription = null,
                                            tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = preset.title,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    if (isCurrent) {
                                        Icon(
                                            Icons.Filled.CheckCircle,
                                            contentDescription = "Active",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = preset.type.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                } else {
                    // Custom Type Selector & Editor
                    Text(
                        text = "Constraint Mode",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        GbnfGrammarHelper.GrammarType.values().forEach { type ->
                            FilterChip(
                                selected = activeType == type,
                                onClick = { activeType = type },
                                label = { Text(type.displayName, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag("grammar_type_chip_${type.name}")
                            )
                        }
                    }

                    when (activeType) {
                        GbnfGrammarHelper.GrammarType.NONE -> {
                            Text(
                                text = "Standard natural language sampling without grammatical or syntactic restrictions.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        GbnfGrammarHelper.GrammarType.JSON_OBJECT -> {
                            Text(
                                text = "Ensures LLM sampling strictly adheres to RFC-8259 JSON object grammar root.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            GrammarCodePreview(GbnfGrammarHelper.GBNF_JSON_OBJECT.trim())
                        }
                        GbnfGrammarHelper.GrammarType.JSON_ARRAY -> {
                            Text(
                                text = "Forces output to be a valid JSON array of objects, strings, or numbers.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            GrammarCodePreview(GbnfGrammarHelper.GBNF_JSON_ARRAY.trim())
                        }
                        GbnfGrammarHelper.GrammarType.CHOICE_ENUM -> {
                            Text(
                                text = "Comma-separated list of allowed output strings:",
                                style = MaterialTheme.typography.bodySmall
                            )
                            OutlinedTextField(
                                value = enumChoicesText,
                                onValueChange = { enumChoicesText = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("enum_choices_input"),
                                textStyle = MaterialTheme.typography.bodySmall,
                                singleLine = true
                            )
                            val choices = enumChoicesText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            val compiledGbnf = GbnfGrammarHelper.createChoiceEnumGbnf(choices)
                            GrammarCodePreview(compiledGbnf)
                        }
                        GbnfGrammarHelper.GrammarType.JSON_SCHEMA -> {
                            Text(
                                text = "JSON Schema Definition:",
                                style = MaterialTheme.typography.bodySmall
                            )
                            OutlinedTextField(
                                value = customSchemaText,
                                onValueChange = { customSchemaText = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("schema_json_input"),
                                minLines = 4,
                                maxLines = 8,
                                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                            )
                            val compiledGbnf = GbnfGrammarHelper.convertJsonSchemaToGbnf(customSchemaText)
                            Text(
                                text = "Compiled GBNF Grammar:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            GrammarCodePreview(compiledGbnf)
                        }
                        GbnfGrammarHelper.GrammarType.BOOLEAN -> {
                            Text(
                                text = "Forces output to strictly equal 'true' or 'false'.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            GrammarCodePreview(GbnfGrammarHelper.GBNF_BOOLEAN.trim())
                        }
                        GbnfGrammarHelper.GrammarType.NUMERIC -> {
                            Text(
                                text = "Forces output to be purely numeric digits/floats.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            GrammarCodePreview(GbnfGrammarHelper.GBNF_NUMERIC.trim())
                        }
                        GbnfGrammarHelper.GrammarType.KEY_VALUE -> {
                            Text(
                                text = "Forces formatted Key: Value entries.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            GrammarCodePreview(GbnfGrammarHelper.GBNF_KEY_VALUE.trim())
                        }
                        GbnfGrammarHelper.GrammarType.CUSTOM -> {
                            Text(
                                text = "Custom GGML BNF Grammar:",
                                style = MaterialTheme.typography.bodySmall
                            )
                            OutlinedTextField(
                                value = customGbnfText,
                                onValueChange = { customGbnfText = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("custom_gbnf_input"),
                                minLines = 4,
                                maxLines = 8,
                                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                isError = !validationResult.isValid
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (validationResult.isValid) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                                    contentDescription = null,
                                    tint = if (validationResult.isValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = validationResult.message,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (validationResult.isValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedTab == 1) {
                        val choices = enumChoicesText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        onApplyCustom(activeType, customGbnfText, customSchemaText, choices)
                    }
                    onDismiss()
                },
                modifier = Modifier.testTag("apply_grammar_button")
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            Row {
                if (currentType != GbnfGrammarHelper.GrammarType.NONE) {
                    TextButton(
                        onClick = {
                            onClear()
                            onDismiss()
                        },
                        modifier = Modifier.testTag("clear_grammar_button")
                    ) {
                        Text("Reset / Disable", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
private fun GrammarCodePreview(code: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 120.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(8.dp)
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
        ) {
            Text(
                text = code,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
