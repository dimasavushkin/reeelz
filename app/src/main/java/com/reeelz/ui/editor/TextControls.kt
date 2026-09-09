package com.reeelz.ui.editor

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.reeelz.editor.TextParameters

@Composable
fun TextControls(text: TextParameters, enabled: Boolean, onChange: (TextParameters) -> Unit, finish: () -> Unit, remove: () -> Unit) {
    Text("Текст на видео", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        value = text.content,
        onValueChange = { onChange(text.copy(content = it.take(200))) },
        enabled = enabled,
        label = { Text("Введите надпись") },
        supportingText = { Text("${text.content.length}/200") },
        modifier = Modifier.fillMaxWidth(),
        maxLines = 4,
    )
    if (text.content.isNotEmpty()) {
        Text("Размер текста · ${text.size.toInt()}")
        Slider(text.size, { onChange(text.copy(size = it)) }, valueRange = 32f..120f, enabled = enabled, onValueChangeFinished = finish)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("Белый" to 0xFFFFFFFF.toInt(), "Жёлтый" to 0xFFFFEB3B.toInt(), "Розовый" to 0xFFFF80AB.toInt()).forEach { (name, color) ->
                FilterChip(selected = text.color == color, onClick = { onChange(text.copy(color = color)) },
                    enabled = enabled, label = { Text(name, color = Color(color)) })
            }
        }
        Text("Текст: положение по горизонтали")
        Slider(text.x, { onChange(text.copy(x = it)) }, valueRange = -1f..1f, enabled = enabled, onValueChangeFinished = finish)
        Text("Текст: положение по вертикали")
        Slider(text.y, { onChange(text.copy(y = it)) }, valueRange = -1f..1f, enabled = enabled, onValueChangeFinished = finish)
        Text("Перетаскивайте саму надпись на видео. Ползунки — для точной настройки.")
        TextButton(onClick = remove, enabled = enabled) { Text("Удалить текст") }
    }
}



