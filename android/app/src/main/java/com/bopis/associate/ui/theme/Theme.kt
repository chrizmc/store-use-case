package com.bopis.associate.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BopisColorScheme = lightColorScheme(
    primary = SapBlue,
    onPrimary = Color.White,
    primaryContainer = SapBlueDark,
    onPrimaryContainer = Color.White,
    secondary = SapGold,
    onSecondary = Color.Black,
    background = SapBackground,
    surface = SapSurface,
)

@Composable
fun BopisTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = BopisColorScheme, content = content)
}
