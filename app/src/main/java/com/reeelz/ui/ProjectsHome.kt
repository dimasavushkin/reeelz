package com.reeelz.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.reeelz.editor.ProjectSummary
import com.reeelz.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProjectsHome(projects: List<ProjectSummary>, busy: Boolean, create: () -> Unit, open: (String) -> Unit,
                 rename: (String, String) -> Unit, delete: (String) -> Unit, lastExport: (() -> Unit)?) {
    var action by remember { mutableStateOf<Pair<ProjectSummary, Boolean>?>(null) }
    var name by remember { mutableStateOf("") }
    var topMenu by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF17222D), Ink, Ink), endY = 950f))) {
        Box(Modifier.align(Alignment.TopEnd).offset(x = 100.dp, y = (-70).dp).size(280.dp)
            .background(Brush.radialGradient(listOf(LavenderStrong.copy(alpha = .18f), Color.Transparent)), RoundedCornerShape(140.dp)))
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ReeelzLogo()
                Spacer(Modifier.weight(1f))
                Box {
                    FilledIconButton(onClick = { topMenu = true }, colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.White.copy(alpha = .08f), contentColor = Color.White)) { Text("•••") }
                    DropdownMenu(expanded = topMenu, onDismissRequest = { topMenu = false }) {
                        lastExport?.let { action -> DropdownMenuItem(text = { Text("Последний экспорт") }, onClick = { topMenu = false; action() }) }
                        DropdownMenuItem(text = { Text("О приложении") }, onClick = { topMenu = false; info = true })
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Быстро", style = MaterialTheme.typography.headlineLarge)
                Text("Эстетично", style = MaterialTheme.typography.headlineLarge)
                Text("Бесплатно", style = MaterialTheme.typography.headlineLarge, color = Lavender)
            }
            Button(onClick = create, enabled = !busy, modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(20.dp), colors = ButtonDefaults.buttonColors(containerColor = Lavender, contentColor = Color(0xFF17101F))) {
                Text("＋", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(7.dp))
                Text("Новый ролик", style = MaterialTheme.typography.titleMedium)
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Lavender, trackColor = Divider)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Мои проекты", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                lastExport?.let { TextButton(onClick = it, enabled = !busy) { Text("Последний экспорт ›", color = Muted) } }
            }
            if (projects.isEmpty() && !busy) EmptyProjects(Modifier.weight(1f))
            else LazyRow(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(end = 12.dp)) {
                items(projects, key = { it.id }) { project ->
                    ProjectCard(project, !busy, { open(project.id) },
                        { name = project.name; action = project to false }, { action = project to true })
                }
            }
            Text("CREATE  /  EDIT  /  SHARE", color = Muted, style = MaterialTheme.typography.labelSmall)
        }
    }
    action?.let { (project, removing) ->
        AlertDialog(onDismissRequest = { action = null }, containerColor = SurfaceRaised,
            title = { Text(if (removing) "Удалить проект?" else "Название проекта") },
            text = { if (removing) Text("«${project.name}» будет удалён. Исходник и экспорт в галерее останутся.")
                else OutlinedTextField(name, { name = it.take(60) }, singleLine = true) },
            confirmButton = { TextButton(enabled = removing || name.isNotBlank(), onClick = {
                action = null; if (removing) delete(project.id) else rename(project.id, name)
            }) { Text(if (removing) "Удалить" else "Сохранить") } },
            dismissButton = { TextButton(onClick = { action = null }) { Text("Отмена") } })
    }
    if (info) AlertDialog(onDismissRequest = { info = false }, containerColor = SurfaceRaised,
        title = { ReeelzLogo() }, text = { Text("Простой мобильный редактор вертикальных роликов.\n\nCREATE  /  EDIT  /  SHARE") },
        confirmButton = { TextButton(onClick = { info = false }) { Text("Понятно") } })
}

@Composable
private fun ProjectCard(project: ProjectSummary, enabled: Boolean, open: () -> Unit, rename: () -> Unit, delete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val bitmap by produceState<android.graphics.Bitmap?>(null, project.cover) {
        value = withContext(Dispatchers.IO) { project.cover?.let(BitmapFactory::decodeFile) }
    }
    Card(Modifier.width(158.dp).height(222.dp).clickable(enabled = enabled, onClick = open),
        shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = SurfaceRaised)) {
        Box(Modifier.fillMaxSize()) {
            if (bitmap != null) Image(bitmap!!.asImageBitmap(), "Обложка ${project.name}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF343A48), Color(0xFF151820)))))
                ReeelzLogo(Modifier.align(Alignment.Center).padding(horizontal = 22.dp), null)
            }
            Box(Modifier.fillMaxWidth().height(86.dp).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(.9f)))))
            Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                Text(project.name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(project.modified)),
                    color = Color.White.copy(.7f), style = MaterialTheme.typography.bodySmall)
            }
            Box(Modifier.align(Alignment.TopEnd)) {
                TextButton(onClick = { menu = true }, enabled = enabled) { Text("•••", color = Color.White) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Переименовать") }, onClick = { menu = false; rename() })
                    DropdownMenuItem(text = { Text("Удалить") }, onClick = { menu = false; delete() })
                }
            }
        }
    }
}

@Composable
private fun EmptyProjects(modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), color = Color.White.copy(alpha = .045f), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("＋", color = Lavender, style = MaterialTheme.typography.headlineLarge)
            Text("Здесь появятся ваши ролики", style = MaterialTheme.typography.titleMedium)
            Text("Выберите видео — проект сохранится автоматически", color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
