package com.example.androidstyletransfer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Clay,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = Slate,
    background = Sand,
    surface = androidx.compose.ui.graphics.Color.White,
    onSurface = Ink,
    onSurfaceVariant = Slate,
)

private val DarkColors = darkColorScheme(
    primary = Clay,
    secondary = Sky,
    background = Ink,
    surface = Ink,
    onSurface = androidx.compose.ui.graphics.Color.White,
)

@Composable
fun AndroidStyleTransferTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography,
        content = content,
    )
}