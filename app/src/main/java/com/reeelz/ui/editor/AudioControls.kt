package com.reeelz.ui.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.media3.common.util.UnstableApi
import com.reeelz.editor.*

@UnstableApi
@Composable
fun AudioControls(vm: EditorViewModel, state: EditorUiState, enabled: Boolean) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importMusic) }
    val audio = state.audio
    Text("Звук исходного видео · ${(audio.originalVolume * 100).toInt()}%")
    Slider(audio.originalVolume, { vm.audio(audio.copy(originalVolume = it)) }, enabled = enabled, onValueChangeFinished = vm::finishEdit)
    TextButton(onClick = { vm.finishEdit(); vm.audio(audio.copy(originalVolume = if (audio.originalVolume > 0) 0f else 1f)); vm.finishEdit() }, enabled = enabled) {
        Text(if (audio.originalVolume > 0) "Выключить исходный звук" else "Включить исходный звук")
    }
    Button(onClick = { picker.launch(arrayOf("audio/*")) }, enabled = enabled) { Text(if (audio.musicUri == null) "Добавить музыку" else "Заменить музыку") }
    if (audio.musicUri != null) {
        Text(audio.musicName)
        Text("Громкость музыки · ${(audio.musicVolume * 100).toInt()}%")
        Slider(audio.musicVolume, { vm.audio(audio.copy(musicVolume = it)) }, enabled = enabled, onValueChangeFinished = vm::finishEdit)
        TextButton(onClick = { vm.finishEdit(); vm.audio(audio.copy(musicUri = null, musicDurationMs = 0)); vm.finishEdit() }, enabled = enabled) { Text("Удалить музыку") }
        Text("Музыка начинается вместе с роликом и повторяется до его конца.", style = MaterialTheme.typography.bodySmall)
    }
}
