package com.reeelz.ui.editor

import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.reeelz.editor.VideoClip
import com.reeelz.editor.outputDurationMs
import com.reeelz.ui.theme.Divider
import com.reeelz.ui.theme.Muted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ClipStrip(clips: List<VideoClip>, selected: Int, positionMs: Long, enabled: Boolean, select: (Int) -> Unit,
              seekTimeline: (Long) -> Unit, split: (Long) -> Unit, add: () -> Unit,
              move: (Int) -> Unit, duplicate: () -> Unit, remove: () -> Unit) {
    val total = clips.sumOf(VideoClip::outputDurationMs).coerceAtLeast(1L)
    Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Клипы · ${clips.size}/10", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = add, enabled = enabled && clips.size < 10) { Text("＋ Добавить") }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(clips, key = { _, clip -> clip.id }) { index, clip -> ClipThumbnail(clip, index, index == selected, enabled) { select(index) } }
        }
        Canvas(Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(18.dp)) {
            var left = 0f
            clips.forEachIndexed { index, clip ->
                val width = size.width * clip.outputDurationMs / total
                drawRect(if (index == selected) Color.White else Color(0xFF565B66), Offset(left, 2.dp.toPx()), Size(width.coerceAtLeast(1f), 14.dp.toPx()))
                if (index > 0) drawLine(Color.Black, Offset(left, 2.dp.toPx()), Offset(left, 16.dp.toPx()), 2.dp.toPx())
                left += width
            }
            val cursor = size.width * positionMs.coerceIn(0, total) / total
            drawLine(Color.White, Offset(cursor, 0f), Offset(cursor, size.height), 2.dp.toPx())
        }
        Slider(positionMs.toFloat().coerceIn(0f, total.toFloat()), { seekTimeline(it.toLong()) },
            valueRange = 0f..total.toFloat(), enabled = enabled, modifier = Modifier.padding(horizontal = 14.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${timelineLabel(positionMs)} / ${timelineLabel(total)}", color = Muted, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = { split(positionMs) }, enabled = enabled && clips.size < 10) { Text("✂ Разделить") }
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

private fun timelineLabel(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return "%02d:%02d".format(seconds / 60, seconds % 60)
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
