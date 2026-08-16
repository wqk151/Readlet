package com.readlet.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.readlet.app.data.Settings
import com.readlet.app.ui.AppViewModel

/** 设置：API Key / Base URL / 模型 / 翻译术语表。保存后重建 LLM 客户端。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(vm: AppViewModel) {
    val state by vm.settingsState.collectAsStateWithLifecycle()
    var apiKey by remember { mutableStateOf(state.apiKey) }
    var baseUrl by remember { mutableStateOf(state.baseUrl) }
    var model by remember { mutableStateOf(state.model) }
    var glossary by remember { mutableStateOf(state.glossary) }
    var modelMenuOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { vm.settingsOpen.value = false },
        title = { Text("设置") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // 与全局卡片语言一致的柔和造型：14dp 圆角 + 暖色浅底 + 绿色聚焦描边
                val fieldShape = RoundedCornerShape(14.dp)
                val fieldColors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    cursorColor = MaterialTheme.colorScheme.primary,
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = fieldShape,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    singleLine = true,
                    shape = fieldShape,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                ExposedDropdownMenuBox(
                    expanded = modelMenuOpen,
                    onExpandedChange = { modelMenuOpen = it },
                ) {
                    OutlinedTextField(
                        value = model,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("模型") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelMenuOpen) },
                        singleLine = true,
                        shape = fieldShape,
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = modelMenuOpen,
                        onDismissRequest = { modelMenuOpen = false },
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Settings.MODELS.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m) },
                                onClick = {
                                    model = m
                                    modelMenuOpen = false
                                },
                            )
                        }
                    }
                }
                Text(
                    "默认 DeepSeek：https://api.deepseek.com · deepseek-v4-flash",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = glossary,
                    onValueChange = { glossary = it },
                    label = { Text("翻译术语表") },
                    placeholder = { Text("每行一个：词 → 指定译法\n如：tolerance → 公差（不译「容差」）\n支持 → / = / ： 作为分隔符") },
                    minLines = 4,
                    maxLines = 8,
                    shape = fieldShape,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "翻译时遇到术语表里的词，引擎必须按指定译法翻译（全局生效）",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.saveSettings(apiKey, baseUrl, model, glossary) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = { vm.settingsOpen.value = false }) { Text("取消") }
        },
    )
}
