package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val MeatDropColorScheme = darkColorScheme(
  primary = MeatCrimson,
  onPrimary = TextPrimary,
  primaryContainer = MeatDark,
  onPrimaryContainer = TextPrimary,
  secondary = TearCyan,
  onSecondary = StormVoid,
  secondaryContainer = DungeonSurface,
  onSecondaryContainer = TearCyan,
  tertiary = StormViolet,
  onTertiary = TextPrimary,
  background = StormVoid,
  onBackground = TextPrimary,
  surface = DungeonStone,
  onSurface = TextPrimary,
  surfaceVariant = DungeonSurface,
  onSurfaceVariant = TextSecondary,
  outline = DungeonBorder,
)

@Composable
fun MeatDropTheme(
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = MeatDropColorScheme,
    typography = MeatDropTypography,
    content = content,
  )
}
