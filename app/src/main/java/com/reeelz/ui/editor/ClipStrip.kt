package com.reeelz.ui.editor

import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.reeelz.editor.VideoClip
import com.reeelz.ui.theme.Divider
import com.reeelz.ui.theme.Muted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ClipStrip(clips: List<VideoClip>, selected: Int, enabled: Boolean, select: (Int) -> Unit,
              add: () -> Unit, move: (Int) -> Unit, duplicate: () -> Unit, remove: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Клипы · ${clips.size}/10", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = add, enabled = enabled && clips.size < 10) { Text("＋ Добавить") }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(clips, key = { _, clip -> clip.id }) { index, clip -> ClipThumbnail(clip, index, index == selected, enabled) { select(index) } }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = { move(-1) }, enabled = enabled && selected > 0) { Text("← Левее") }
            TextButton(onClick = { move(1) }, enabled = enabled && selected < clips.lastIndex) { Text("Правее →") }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = duplicate, enabled = enabled && clips.size < 10) { Text("Дублировать") }
            TextButton(onClick = remove, enabled = enabled && clips.size > 1) { Text("Удалить", color = if (enabled && clips.size > 1) MaterialTheme.colorScheme.error else Muted) }
        }
        HorizontalDivider(color = Divider)
    }
}

@Composable
private fun ClipThumbnail(clip: VideoClip, index: Int, selected: Boolean, enabled: Boolean, open: () -> Unit) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null, clip.source.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                MediaMetadataRetriever().use { reader ->
                    reader.setDataSource(context, clip.source.uri)
                    reader.getScaledFrameAtTime(clip.startMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 120, 80)
                }
            }.getOrNull()
        }
    }
    val shape = RoundedCornerShape(10.dp)
    Surface(Modifier.size(82.dp, 54.dp).clip(shape).then(if (selected) Modifier.border(2.dp, Color.White, shape) else Modifier)
        .clickable(enabled = enabled, onClick = open), color = Color(0xFF272B33), shape = shape) {
        Box {
            bitmap?.let { Image(it.asImageBitmap(), "Клип ${index + 1}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            Surface(Modifier.align(Alignment.BottomStart), color = Color.Black.copy(.7f), shape = RoundedCornerShape(topEnd = 7.dp)) {
                Text("${index + 1}", Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
