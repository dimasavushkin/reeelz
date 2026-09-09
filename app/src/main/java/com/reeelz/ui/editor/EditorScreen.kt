package com.reeelz.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.reeelz.ui.theme.Divider
import com.reeelz.ui.theme.Ink
import com.reeelz.ui.theme.Accent
import com.reeelz.ui.theme.Muted
import com.reeelz.ui.theme.SurfaceRaised
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.reeelz.editor.*

@UnstableApi
@Composable
fun EditorScreen(vm: EditorViewModel, state: EditorUiState, playing: Boolean, busy: Boolean, saveStatus: String) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val focus = LocalFocusManager.current
    val enabled = !state.exporting && !busy
    val history by vm.historyAvailability.collectAsState()
    val exportedVideo by vm.exportedVideo.collectAsState()
    val position by vm.playback.positionMs.collectAsState()
    val sourcePosition by vm.playback.sourcePositionMs.collectAsState()
    val clipPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        vm.addClips(uris)
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Ink).safeDrawingPadding()) {
        val previewWidth = minOf(maxWidth - 40.dp, 310.dp, maxHeight * .46f * 9f / 16f)
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                EditorIconButton("‹", "На главную", enabled, vm::home)
                Spacer(Modifier.weight(1f))
                EditorIconButton("↶", "Отменить", enabled && history.first, vm::undo)
                EditorIconButton("↷", "Повторить", enabled && history.second, vm::redo)
                Spacer(Modifier.width(8.dp))
                Button(onClick = { focus.clearFocus(); vm.export() }, enabled = enabled,
                    shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)) { Text("Экспорт") }
            }
            Text(saveStatus, color = Muted, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(7.dp))
            Box(Modifier.width(previewWidth).aspectRatio(9f / 16f).clip(RoundedCornerShape(3.dp)).background(Color.Black)) {
                AndroidView(
                    factory = { PlayerView(it).apply { useController = false; player = vm.playback.player } },
                    update = { it.player = vm.playback.player },
                    onRelease = { it.player = null },
                    modifier = Modifier.fillMaxSize(),
                )
                if (tab == 1) CropGestureLayer(state.crop, enabled, vm::crop)
                if (tab == 2 && enabled && state.activeText().visibleAt(position)) {
                    CaptionDragLayer(state.activeText(), vm::moveText, vm::finishEdit, true)
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = vm.playback::toggle, enabled = enabled,
                    contentPadding = PaddingValues(4.dp)) { Text(if (playing) "Ⅱ" else "▶", style = MaterialTheme.typography.titleLarge) }
                Spacer(Modifier.weight(1f))
                Text("${timeLabel(position)}  /  ${timeLabel(state.totalDurationMs())}",
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Text("⌗", color = Muted, style = MaterialTheme.typography.titleLarge)
            }
            if (state.exporting) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 18.dp), color = Accent, trackColor = Divider)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Экспорт ${state.progress}%")
                    TextButton(onClick = vm::cancelExport) { Text("Отменить") }
                }
            }
            Surface(Modifier.fillMaxWidth().weight(1f), color = SurfaceRaised, shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)) {
                Column(Modifier.fillMaxSize()) {
                    TabRow(selectedTabIndex = tab, containerColor = Color.Transparent, contentColor = Accent, divider = {}) {
                        listOf("✂\nОбрезка", "▣\nКадр", "T\nТекст", "♫\nЗвук").forEachIndexed { index, label ->
                            Tab(selected = tab == index, onClick = { focus.clearFocus(); vm.finishEdit(); tab = index },
                                text = { Text(label, textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.labelLarge) })
                        }
                    }
                    key(tab) {
                    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            when (tab) {
                                0 -> TrimControls(state, sourcePosition, enabled, vm.playback::seek, vm::trim, vm::applyTrim)
                                1 -> {
                                    Text("Разведите пальцы для масштаба, перетащите видео для позиции.",
                                        color = Muted, style = MaterialTheme.typography.bodySmall)
                                    Text("Масштаб · %.2f×".format(state.crop.zoom))
                                    Slider(state.crop.zoom, { vm.crop(state.crop.copy(zoom = it)) }, valueRange = 1f..4f, enabled = enabled, onValueChangeFinished = vm::finishEdit)
                                    Text("По горизонтали")
                                    Slider(state.crop.x, { vm.crop(state.crop.copy(x = it)) }, valueRange = -1f..1f, enabled = enabled, onValueChangeFinished = vm::finishEdit)
                                    Text("По вертикали")
                                    Slider(state.crop.y, { vm.crop(state.crop.copy(y = it)) }, valueRange = -1f..1f, enabled = enabled, onValueChangeFinished = vm::finishEdit)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = { vm.finishEdit(); vm.crop(CropParameters()); vm.finishEdit() },
                                            enabled = enabled, modifier = Modifier.weight(1f)) { Text("↶  Сброс") }
                                        OutlinedButton(onClick = {
                                            vm.finishEdit(); vm.crop(state.crop.copy(rotation = state.crop.rotation + 90)); vm.finishEdit()
                                        }, enabled = enabled, modifier = Modifier.weight(1f)) { Text("↻  Повернуть") }
                                    }
                                    Button(onClick = { vm.finishEdit(); tab = 0 }, enabled = enabled,
                                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text("✓  Готово") }
                                }
                                2 -> CaptionControls(vm, state, enabled)
                                3 -> AudioControls(vm, state, enabled)
                            }
                            if (saveStatus.startsWith("Не удалось")) {
                                OutlinedButton(onClick = vm::saveDraft, enabled = enabled) { Text("Повторить сохранение") }
                            }
                            if (exportedVideo != null) {
                                TextButton(onClick = vm::showExportResult, enabled = enabled) { Text("Последний экспорт · посмотреть / поделиться") }
                            }
                        }
                        HorizontalDivider(color = Divider)
                        ClipStrip(state.timelineClips(), state.selectedClipIndex, position, enabled, vm::selectClip,
                            vm.playback::seekTimeline, vm::splitAt,
                            { clipPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                            vm::moveSelectedClip, vm::duplicateSelectedClip, vm::removeSelectedClip)
                    }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.CropGestureLayer(crop: CropParameters, enabled: Boolean, change: (CropParameters) -> Unit) {
    val current by rememberUpdatedState(crop)
    val onChange by rememberUpdatedState(change)
    Canvas(Modifier.matchParentSize().pointerInput(enabled) {
        if (enabled) detectTransformGestures { _, pan, gestureZoom, _ ->
            val value = current
            onChange(value.copy(
                zoom = (value.zoom * gestureZoom).coerceIn(1f, 4f),
                x = (value.x - pan.x / size.width * 2f / value.zoom).coerceIn(-1f, 1f),
                y = (value.y - pan.y / size.height * 2f / value.zoom).coerceIn(-1f, 1f),
            ))
        }
    }) {
        val line = Color.White.copy(alpha = .65f)
        for (part in 1..2) {
            val x = size.width * part / 3f
            val y = size.height * part / 3f
            drawLine(line, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
            drawLine(line, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        }
        drawRect(Color.White, style = Stroke(1.5.dp.toPx()))
    }
}

@Composable
private fun EditorIconButton(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Text(symbol, style = MaterialTheme.typography.headlineMedium,
            color = if (enabled) MaterialTheme.colorScheme.onBackground else Muted.copy(alpha = .4f))
    }
}

private fun timeLabel(ms: Long): String {
    val value = ms.coerceAtLeast(0) / 1000
    return "%02d:%02d".format(value / 60, value % 60)
}

@UnstableApi
@Composable
private fun BoxScope.CaptionDragLayer(text: TextParameters, move: (Float, Float) -> Unit, finish: () -> Unit, showBounds: Boolean) {
    val layout = remember(text.content, text.size) { CaptionEffect.layout(text) }
    val width = layout.width + 24
    val height = layout.height + 24
    val current by rememberUpdatedState(text)
    val onMove by rememberUpdatedState(move)
    val onFinish by rememberUpdatedState(finish)
    Canvas(Modifier.matchParentSize().pointerInput(width, height) {
        var dragging = false
        detectDragGestures(
            onDragStart = { point ->
                val bounds = captionBounds(current, width, height)
                dragging = point.x / size.width in bounds.left..bounds.right && point.y / size.height in bounds.top..bounds.bottom
            },
            onDragEnd = { dragging = false; onFinish() }, onDragCancel = { dragging = false; onFinish() },
        ) { change, delta ->
            if (dragging) {
                change.consume()
                onMove(captionDragDelta(delta.x / size.width, width, 1080), captionDragDelta(delta.y / size.height, height, 1920))
            }
        }
    }) {
        if (showBounds) {
            val b = captionBounds(text, width, height)
            drawRect(Color.White.copy(alpha = .6f), Offset(b.left * size.width, b.top * size.height),
                Size((b.right - b.left) * size.width, (b.bottom - b.top) * size.height), style = Stroke(1.dp.toPx()))
        }
    }
}




