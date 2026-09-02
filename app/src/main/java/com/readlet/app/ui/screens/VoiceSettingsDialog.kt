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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.readlet.app.tts.TtsIds
import com.readlet.app.tts.VoiceCatalog
import com.readlet.app.ui.AppViewModel

/**
 * 发音设置：语音引擎（系统语音/本地 kokoro）、音色、语音包下载管理。
 * 改动即时生效（直接写 Settings 并刷新 TtsManager，不经过主设置的「保存」）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSettingsDialog(vm: AppViewModel) {
    val s by vm.ttsManager.state.collectAsStateWithLifecycle()
    var engineMenuOpen by remember { mutableStateOf(false) }
    var voiceMenuOpen by remember { mutableStateOf(false) }

    val engineOptions = listOf(
        TtsIds.ENGINE_SYSTEM to "系统语音",
        TtsIds.ENGINE_LOCAL to "本地语音引擎（kokoro）",
    )
    val isLocal = s.engineId == TtsIds.ENGINE_LOCAL

    AlertDialog(
        onDismissRequest = { vm.closeVoiceSettings() },
        title = { Text("发音设置") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val fieldShape = RoundedCornerShape(14.dp)
                val fieldColors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    cursorColor = MaterialTheme.colorScheme.primary,
                )
                ExposedDropdownMenuBox(
                    expanded = engineMenuOpen,
                    onExpandedChange = { engineMenuOpen = it },
                ) {
                    OutlinedTextField(
                        value = engineOptions.firstOrNull { it.first == s.engineId }?.second ?: s.engineId,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("语音引擎") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = engineMenuOpen) },
                        singleLine = true,
                        shape = fieldShape,
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = engineMenuOpen,
                        onDismissRequest = { engineMenuOpen = false },
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        engineOptions.forEach { (id, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    vm.setVoiceEngine(id, s.voiceId)
                                    engineMenuOpen = false
                                },
                            )
                        }
                    }
                }

                if (isLocal) {
                    Spacer(Modifier.height(12.dp))
                    when {
                        s.packInstalled -> {
                            ExposedDropdownMenuBox(
                                expanded = voiceMenuOpen,
                                onExpandedChange = { voiceMenuOpen = it },
                            ) {
                                OutlinedTextField(
                                    value = VoiceCatalog.labelOf(s.voiceId),
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("音色（美音）") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = voiceMenuOpen) },
                                    singleLine = true,
                                    shape = fieldShape,
                                    colors = fieldColors,
                                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                )
                                ExposedDropdownMenu(
                                    expanded = voiceMenuOpen,
                                    onDismissRequest = { voiceMenuOpen = false },
                                    shape = RoundedCornerShape(14.dp),
                                ) {
                                    VoiceCatalog.US_VOICES.forEach { v ->
                                        DropdownMenuItem(
                                            text = { Text(VoiceCatalog.voiceLabel(v)) },
                                            onClick = {
                                                vm.setVoiceEngine(TtsIds.ENGINE_LOCAL, v.id)
                                                voiceMenuOpen = false
                                            },
                                        )
                                    }
                                }
                            }
                            Text(
                                "已安装 · ${VoiceCatalog.PACK_DISPLAY} · 约 ${VoiceCatalog.PACK_SIZE_LABEL}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = { vm.deleteVoicePack() }) {
                                Text("删除语音包（回到系统语音）")
                            }
                        }
                        s.downloading || s.extracting -> {
                            if (s.extracting) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Text(
                                    "解压中，请稍候…",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            } else {
                                LinearProgressIndicator(
                                    progress = { s.downloadProgress },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text(
                                    "下载中 ${(s.downloadProgress * 100).toInt()}% · ${VoiceCatalog.PACK_DISPLAY}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = { vm.cancelVoicePackDownload() }) {
                                Text("取消")
                            }
                        }
                        else -> {
                            OutlinedButton(onClick = { vm.startVoicePackDownload() }) {
                                Text("下载语音包（约 ${VoiceCatalog.PACK_SIZE_LABEL}）")
                            }
                            s.downloadError?.let {
                                Text(
                                    it,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                        }
                    }
                }

                Text(
                    when {
                        !isLocal -> "系统语音开箱即用；英文发音质量取决于设备 ROM 与语音包。想获得稳定的神经网络美音，请切换到本地语音引擎。"
                        s.packInstalled -> "本地引擎离线运行（sherpa-onnx · kokoro v1.1），无网络也能发音；音色 Maple 为默认美音女声。未安装完成前点播放会自动回退系统语音。"
                        else -> "本地引擎离线运行（sherpa-onnx · kokoro v1.1），需先下载语音包（约 ${VoiceCatalog.PACK_SIZE_LABEL}，断点续传）。"
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.closeVoiceSettings() }) { Text("完成") }
        },
    )
}
