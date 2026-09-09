package com.reeelz.ui.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.media3.common.util.UnstableApi
import com.reeelz.editor.*

@UnstableApi
@Composable
fun CaptionControls(vm: EditorViewModel, state: EditorUiState, enabled: Boolean) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        state.allTexts().forEachIndexed { i, _ ->
            FilterChip(selected = state.selectedText == i, onClick = { vm.selectText(i) }, enabled = enabled, label = { Text("Текст ${i + 1}") })
        }
        TextButton(onClick = vm::addText, enabled = enabled && state.extraTexts.size < 4) { Text("+ Добавить") }
    }
    val text = state.activeText()
    TextControls(text, enabled, vm::text, vm::finishEdit, vm::removeText)
    Text("Выравнивание")
    Row {
        listOf("Слева", "Центр", "Справа").forEachIndexed { i, label ->
            FilterChip(selected = text.alignment == i, onClick = { vm.finishEdit(); vm.text(text.copy(alignment = i)); vm.finishEdit() }, enabled = enabled, label = { Text(label) })
        }
    }
    Row {
        Text("Тёмный фон надписи")
        Switch(checked = text.background, onCheckedChange = { vm.finishEdit(); vm.text(text.copy(background = it)); vm.finishEdit() }, enabled = enabled)
    }
    val duration = state.totalDurationMs().coerceAtLeast(1)
    val begin = text.startMs.coerceIn(0, duration - 1)
    val end = text.endMs.coerceIn(begin + 1, duration)
    Text("Показ: %.2f — %.2f сек".format(begin / 1000f, end / 1000f))
    RangeSlider(value = begin.toFloat()..end.toFloat(), onValueChange = {
        val a = it.start.toLong().coerceIn(0, duration - 1)
        vm.text(text.copy(startMs = a, endMs = it.endInclusive.toLong().coerceIn(a + 1, duration)))
    }, onValueChangeFinished = vm::finishEdit, valueRange = 0f..duration.toFloat(), enabled = enabled)
    TextButton(onClick = { vm.finishEdit(); vm.text(text.copy(startMs = 0, endMs = Long.MAX_VALUE)); vm.finishEdit() }, enabled = enabled) { Text("На весь ролик") }
    Text("Время отсчитывается от начала обрезанного ролика.", style = MaterialTheme.typography.bodySmall)
}
