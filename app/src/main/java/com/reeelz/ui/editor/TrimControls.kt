package com.reeelz.ui.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.reeelz.editor.*
import kotlinx.coroutines.CancellationException

@Composable
fun TrimControls(state: EditorUiState, positionMs: Long, enabled: Boolean, seek: (Long) -> Unit,
                 trim: (Long, Long) -> Unit, apply: () -> Unit) {
    val source = requireNotNull(state.source)
    val context = LocalContext.current.applicationContext
    val frames by produceState<List<android.graphics.Bitmap?>>(emptyList(), source.uri) {
        try { value = TimelineFrames.load(context, source) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { value = emptyList() }
    }
    Text("%.2f — %.2f сек".format(state.startMs / 1000f, state.endMs / 1000f))
    Row(Modifier.fillMaxWidth()) {
        frames.forEachIndexed { index, bitmap ->
            Box(Modifier.weight(1f).height(52.dp).clickable(enabled = enabled) { seek(source.durationMs * index / 7) }) {
                bitmap?.let { Image(it.asImageBitmap(), "Кадр ${index + 1}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            }
        }
    }
    RangeSlider(value = state.startMs.toFloat()..state.endMs.toFloat(),
        onValueChange = { trim(it.start.toLong(), it.endInclusive.toLong()) }, onValueChangeFinished = apply,
        valueRange = 0f..source.durationMs.toFloat(), enabled = enabled)
    Text("Позиция · %.2f сек".format(positionMs / 1000f))
    Slider(positionMs.toFloat().coerceIn(state.startMs.toFloat(), state.endMs.toFloat()),
        { seek(it.toLong()) }, valueRange = state.startMs.toFloat()..state.endMs.toFloat(), enabled = enabled)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { seek(positionMs - 100) }, enabled = enabled) { Text("−0,1 с") }
        OutlinedButton(onClick = { seek(positionMs + 100) }, enabled = enabled) { Text("+0,1 с") }
    }
    var start by remember(state.startMs) { mutableStateOf((state.startMs / 1000.0).toString()) }
    var end by remember(state.endMs) { mutableStateOf((state.endMs / 1000.0).toString()) }
    val a = start.replace(',', '.').toDoubleOrNull()
    val b = end.replace(',', '.').toDoubleOrNull()
    val valid = a != null && b != null && a.isFinite() && b.isFinite() && a >= 0 && b * 1000 <= source.durationMs && (b - a) * 1000 >= minOf(100L, source.durationMs)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(start, { start = it }, label = { Text("Начало, сек") }, singleLine = true, enabled = enabled, modifier = Modifier.weight(1f))
        OutlinedTextField(end, { end = it }, label = { Text("Конец, сек") }, singleLine = true, enabled = enabled, modifier = Modifier.weight(1f))
    }
    Button(onClick = { trim((requireNotNull(a) * 1000).toLong(), (requireNotNull(b) * 1000).toLong()); apply() }, enabled = enabled && valid) { Text("Применить время") }
    Text("Миниатюры показывают весь исходник. Перемотка ограничена выбранным отрезком.", style = MaterialTheme.typography.bodySmall)
}
