package com.reeelz

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import com.reeelz.ui.editor.TextControls
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.reeelz.editor.EditorViewModel

@UnstableApi
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val vm: EditorViewModel = viewModel()
                val state by vm.state.collectAsStateWithLifecycle()
                val playing by vm.playback.isPlaying.collectAsStateWithLifecycle()
                val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                    uri?.let(vm::open)
                }
                val owner = LocalLifecycleOwner.current
                DisposableEffect(owner, vm) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_STOP) vm.playback.pause()
                    }
                    owner.lifecycle.addObserver(observer)
                    onDispose { owner.lifecycle.removeObserver(observer) }
                }
                DisposableEffect(state.exporting) {
                    if (state.exporting) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
                }
                BackHandler(state.source != null) { vm.home() }
                Surface(Modifier.fillMaxSize()) {
                    BoxWithConstraints(Modifier.safeDrawingPadding().imePadding().padding(16.dp)) {
                    val canvasWidth = minOf(220.dp, maxHeight * 0.45f * 9f / 16f)
                    Column(Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.source == null) {
                            Spacer(Modifier.height(80.dp))
                            Text("Reeelz", style = MaterialTheme.typography.headlineLarge)
                            Text("Один ролик. Вертикальный кадр.")
                            Button(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }, enabled = !state.loading) {
                                Text("Новый ролик")
                            }
                            if (state.loading) CircularProgressIndicator()
                        } else {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                TextButton(onClick = vm::home, enabled = !state.exporting) { Text("← Главная") }
                                Text("Редактор", style = MaterialTheme.typography.titleLarge)
                            }
                            Box(Modifier.width(canvasWidth).aspectRatio(9f / 16f)) {
                            AndroidView(
                                factory = { context -> PlayerView(context).apply { useController = false; player = vm.playback.player } },
                                update = { it.player = vm.playback.player },
                                onRelease = { it.player = null },
                                modifier = Modifier.fillMaxSize().background(Color.Black),
                            )
                            if (state.text.content.isNotBlank() && !state.exporting) {
                                Box(Modifier.matchParentSize().pointerInput(vm) {
                                    detectDragGesturesAfterLongPress { change, drag ->
                                        change.consume()
                                        vm.moveText(2f * drag.x / size.width, 2f * drag.y / size.height)
                                    }
                                })
                            }
                            }
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = vm.playback::toggle, enabled = !state.exporting) { Text(if (playing) "Пауза" else "Воспроизвести") }
                            Text("Обрезка: %.2f — %.2f сек".format(state.startMs / 1000f, state.endMs / 1000f))
                            RangeSlider(
                                value = state.startMs.toFloat()..state.endMs.toFloat(),
                                onValueChange = { vm.trim(it.start.toLong(), it.endInclusive.toLong()) },
                                onValueChangeFinished = vm::applyTrim,
                                valueRange = 0f..state.source!!.durationMs.toFloat(),
                                enabled = !state.exporting,
                            )
                            Text("Масштаб · %.2f×".format(state.crop.zoom))
                            Slider(state.crop.zoom, { vm.crop(state.crop.copy(zoom = it)) }, valueRange = 1f..4f, enabled = !state.exporting)
                            Text("Положение кадра по горизонтали")
                            Slider(state.crop.x, { vm.crop(state.crop.copy(x = it)) }, valueRange = -1f..1f, enabled = !state.exporting)
                            Text("Положение кадра по вертикали")
                            Slider(state.crop.y, { vm.crop(state.crop.copy(y = it)) }, valueRange = -1f..1f, enabled = !state.exporting)
                            HorizontalDivider()
                            TextControls(state.text, !state.exporting, vm::text)
                            if (state.exporting) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Text("Экспорт ${state.progress}% · оставьте приложение открытым")
                                TextButton(onClick = vm::cancelExport) { Text("Отменить") }
                            } else {
                                Button(onClick = vm::export) { Text("Экспорт MP4 · 1080 × 1920") }
                            }
                            }
                        }
                    }
                    }
                }
                state.message?.let { message ->
                    AlertDialog(
                        onDismissRequest = vm::dismissMessage,
                        title = { Text("Reeelz") },
                        text = { Text(message) },
                        confirmButton = {
                            TextButton(onClick = vm::dismissMessage) { Text("Понятно") }
                        },
                    )
                }
            }
        }
    }
}
