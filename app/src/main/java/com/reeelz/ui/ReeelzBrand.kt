package com.reeelz.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.reeelz.R

@Composable
fun ReeelzLogo(modifier: Modifier = Modifier, contentDescription: String? = "Reeelz") {
    Image(
        painter = painterResource(R.drawable.reeelz_logo),
        contentDescription = contentDescription,
        modifier = modifier.width(126.dp).height(24.dp),
    )
}
