package com.reeelz.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.reeelz.ui.theme.Divider
import com.reeelz.ui.theme.Ink
import com.reeelz.ui.theme.Accent
import com.reeelz.ui.theme.Muted

@Composable
fun ReeelzSplash() {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1C2A36), Ink)))) {
        Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ReeelzLogo(Modifier.width(226.dp).height(42.dp))
            Spacer(Modifier.height(18.dp))
            Text("С О З Д А В А Й   С В О Ю   Э С Т Е Т И К У", color = Color.White.copy(.78f),
                style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        }
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            LinearProgressIndicator(Modifier.width(170.dp), color = Accent, trackColor = Divider)
            Spacer(Modifier.height(10.dp))
            Text("З А Г Р У З К А…", color = Muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}
