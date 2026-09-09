package com.reeelz

import android.os.Bundle
import android.content.ActivityNotFoundException
import com.reeelz.export.ExportActions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler




import androidx.compose.foundation.layout.*


import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment

import androidx.compose.ui.unit.dp

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi

import com.reeelz.editor.EditorViewModel
import com.reeelz.ui.ReeelzSplash
import com.reeelz.ui.theme.ReeelzTheme
import kotlinx.coroutines.delay

@UnstableApi
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            ReeelzTheme {
                var showSplash by rememberSaveable { mutableStateOf(true) }
                LaunchedEffect(Unit) { delay(850); showSplash = false }
                val vm: EditorViewModel = viewModel()
                val state by vm.state.collectAsStateWithLifecycle()
                val exportedVideo by vm.exportedVideo.collectAsStateWithLifecycle()
                val resultVisible by vm.resultVisible.collectAsStateWithLifecycle()
                val actionScope = rememberCoroutineScope()
                var openingExport by remember { mutableStateOf(false) }

                val draftBusy by vm.draftBusy.collectAsStateWithLifecycle()
                val saveStatus by vm.saveStatus.collectAsStateWithLifecycle()

                val playing by vm.playback.isPlaying.collectAsStateWithLifecycle()
                val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
                    if (uris.isNotEmpty()) vm.open(uris)
                }
                val owner = LocalLifecycleOwner.current
                DisposableEffect(owner, vm) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_STOP) { vm.playback.pause(); vm.flushDraft() }
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
                    if (showSplash) ReeelzSplash() else Box(Modifier.fillMaxSize().imePadding()) {
                    Column(Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.source == null) {
                            com.reeelz.ui.ProjectsHome(
                                projects = vm.projects.collectAsStateWithLifecycle().value,
                                busy = state.loading || draftBusy,
                                create = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                                open = vm::restoreDraft, rename = vm::renameProject, delete = vm::deleteDraft,
                                lastExport = if (exportedVideo != null) vm::showExportResult else null,
                            )
                        } else {
                            com.reeelz.ui.editor.EditorScreen(vm, state, playing, draftBusy, saveStatus)
                        }
                    }
                }
                }
                if (!showSplash) state.message?.let { message ->
                    AlertDialog(
                        onDismissRequest = vm::dismissMessage,
                        title = { Text("Reeelz") },
                        text = { Text(message) },
                        confirmButton = {
                            TextButton(onClick = vm::dismissMessage) { Text("Понятно") }
                        },
                    )
                }
                if (!showSplash && resultVisible && exportedVideo != null && state.message == null) {
                    val uri = requireNotNull(exportedVideo)
                    val launchAction: (Boolean) -> Unit = { share ->
                        openingExport = true
                        vm.playback.pause()
                        actionScope.launch {
                            try {
                                ExportActions.open(this@MainActivity, uri, share)
                                vm.hideExportResult()
                            } catch (e: CancellationException) { throw e }
                            catch (e: ActivityNotFoundException) {
                                vm.exportActionError("Нет приложения для открытия MP4. Ролик сохранён в Movies/Reeelz.")
                            } catch (e: Exception) {
                                vm.exportActionError("Не удалось открыть ролик. Возможно, файл удалён из галереи. Можно экспортировать заново.")
                            } finally { openingExport = false }
                        }
                    }
                    AlertDialog(
                        onDismissRequest = vm::hideExportResult,
                        title = { Text("Ролик сохранён") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("MP4 · 1080 × 1920\nГалерея → Movies/Reeelz")
                                Text("Это последний готовый файл. Новые правки появятся в нём после повторного экспорта.")
                                Button(onClick = { launchAction(false) }, enabled = !openingExport, modifier = Modifier.fillMaxWidth()) { Text("Посмотреть") }
                                OutlinedButton(onClick = { launchAction(true) }, enabled = !openingExport, modifier = Modifier.fillMaxWidth()) { Text("Поделиться") }
                            }
                        },
                        confirmButton = { TextButton(onClick = vm::hideExportResult) { Text("Вернуться к редактору") } },
                    )
                }

            }
        }
    }
}





