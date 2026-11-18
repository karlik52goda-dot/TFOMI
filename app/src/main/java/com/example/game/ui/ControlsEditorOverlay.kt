package com.example.game.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ControlId
import com.example.data.ControlNormPos
import com.example.data.ControlsLayoutConfig
import com.example.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun ControlsEditorOverlay(
  initialConfig: ControlsLayoutConfig,
  onSave: (ControlsLayoutConfig) -> Unit,
  onCancel: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var positions by remember(initialConfig) {
    mutableStateOf(
      ControlId.entries.associateWith { id -> initialConfig.posFor(id) }
    )
  }
  var swapSides by remember(initialConfig) { mutableStateOf(initialConfig.swapSides) }
  var swapJumpShoot by remember(initialConfig) { mutableStateOf(initialConfig.swapJumpShoot) }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(StormVoid.copy(alpha = 0.94f))
      .windowInsetsPadding(WindowInsets.safeDrawing),
  ) {
    // Subtle blueprint grid in editor background
    Canvas(modifier = Modifier.fillMaxSize()) {
      val step = 36.dp.toPx()
      val lineColor = TearCyan.copy(alpha = 0.08f)
      var x = 0f
      while (x < size.width) {
        drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        x += step
      }
      var y = 0f
      while (y < size.height) {
        drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += step
      }
    }

    // Top Editor Toolbar
    Column(
      modifier = Modifier
        .align(Alignment.TopCenter)
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 10.dp)
        .clip(RoundedCornerShape(18.dp))
        .background(DungeonStone.copy(alpha = 0.95f))
        .border(1.5.dp, TearCyan.copy(alpha = 0.7f), RoundedCornerShape(18.dp))
        .padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(Icons.Filled.OpenWith, contentDescription = null, tint = TearCyan, modifier = Modifier.size(20.dp))
          Text(
            text = "РЕДАКТОР КНОПОК",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
          )
        }
        IconButton(
          onClick = onCancel,
          modifier = Modifier.size(48.dp).testTag("editor_cancel_button"),
        ) {
          Icon(Icons.Filled.Close, contentDescription = "Закрыть", tint = TextSecondary)
        }
      }

      Text(
        text = "Перетаскивай любую кнопку пальцем в удобное место!",
        style = MaterialTheme.typography.bodyMedium,
        color = TextSecondary,
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        OutlinedButton(
          onClick = {
            swapSides = !swapSides
            positions = positions.mapValues { (_, p) ->
              p.copy(x = (1f - p.x).coerceIn(0.08f, 0.92f))
            }
          },
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.weight(1f).height(48.dp).testTag("editor_mirror_button"),
        ) {
          Icon(Icons.Filled.SwapHoriz, contentDescription = null, tint = TearCyan, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text(
            text = if (swapSides) "Левша" else "Правша",
            style = MaterialTheme.typography.labelSmall,
            color = TearCyan,
            maxLines = 1,
          )
        }

        OutlinedButton(
          onClick = {
            swapSides = false
            swapJumpShoot = false
            positions = ControlsLayoutConfig.defaultPositions(false)
            onSave(ControlsLayoutConfig(customEnabled = false, swapSides = false, swapJumpShoot = false))
          },
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.weight(1f).height(48.dp).testTag("editor_reset_button"),
        ) {
          Icon(Icons.Filled.RestartAlt, contentDescription = null, tint = LootLegendary, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text(
            text = "Сбросить",
            style = MaterialTheme.typography.labelSmall,
            color = LootLegendary,
            maxLines = 1,
          )
        }

        Button(
          onClick = {
            onSave(
              ControlsLayoutConfig(
                customEnabled = true,
                swapSides = swapSides,
                swapJumpShoot = swapJumpShoot,
                positions = positions,
              )
            )
          },
          colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.weight(1.1f).height(48.dp).testTag("editor_save_button"),
        ) {
          Icon(Icons.Filled.Check, contentDescription = null, tint = StormVoid, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text(
            text = "ГОТОВО",
            style = MaterialTheme.typography.labelSmall,
            color = StormVoid,
            maxLines = 1,
          )
        }
      }
    }

    // Draggable Playground Canvas
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
      val density = LocalDensity.current
      val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
      val heightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)

      ControlId.entries.forEach { id ->
        val normPos = positions[id] ?: ControlsLayoutConfig.defaultPositions(swapSides).getValue(id)
        val (wDp, hDp) = when (id) {
          ControlId.MOVE -> 148.dp to 84.dp
          ControlId.SHOOT -> 82.dp to 82.dp
          ControlId.JUMP -> 78.dp to 86.dp
          ControlId.DASH -> 66.dp to 68.dp
          ControlId.BUILD_FLOOR, ControlId.BUILD_WALL -> 86.dp to 48.dp
        }
        val wPx = with(density) { wDp.toPx() }
        val hPx = with(density) { hDp.toPx() }
        val offsetX = (normPos.x * widthPx - wPx * 0.5f).roundToInt()
        val offsetY = (normPos.y * heightPx - hPx * 0.5f).roundToInt()

        val accent = when (id) {
          ControlId.MOVE -> TearCyan
          ControlId.SHOOT -> TearCyan
          ControlId.JUMP -> MeatCrimson
          ControlId.DASH -> StormViolet
          ControlId.BUILD_FLOOR, ControlId.BUILD_WALL -> BuildWood
        }

        Box(
          modifier = Modifier
            .offset { IntOffset(offsetX, offsetY) }
            .size(wDp, hDp)
            .testTag("drag_control_${id.key}")
            .clip(if (id == ControlId.SHOOT) CircleShape else RoundedCornerShape(16.dp))
            .background(DungeonSurface.copy(alpha = 0.88f))
            .border(2.dp, accent, if (id == ControlId.SHOOT) CircleShape else RoundedCornerShape(16.dp))
            .pointerInput(id, widthPx, heightPx) {
              detectDragGestures { change, dragAmount ->
                change.consume()
                val cur = positions[id] ?: ControlNormPos(0.5f, 0.7f)
                val nx = (cur.x + dragAmount.x / widthPx).coerceIn(0.08f, 0.92f)
                val ny = (cur.y + dragAmount.y / heightPx).coerceIn(0.24f, 0.93f)
                positions = positions.toMutableMap().apply { put(id, ControlNormPos(nx, ny)) }
              }
            },
          contentAlignment = Alignment.Center,
        ) {
          when (id) {
            ControlId.MOVE -> {
              Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null, tint = accent)
                Text("ДВИГ", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = TextPrimary)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = accent)
              }
            }
            ControlId.SHOOT -> {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.MyLocation, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                Text(id.labelRu, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = accent)
              }
            }
            ControlId.JUMP -> {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.ArrowUpward, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                Text(id.labelRu, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = TextPrimary)
              }
            }
            ControlId.DASH -> {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Bolt, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                Text(id.labelRu, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = TextPrimary)
              }
            }
            ControlId.BUILD_FLOOR -> {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Filled.Layers, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                Text(id.labelRu, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = TextPrimary)
              }
            }
            ControlId.BUILD_WALL -> {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Filled.Construction, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                Text(id.labelRu, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = TextPrimary)
              }
            }
          }
        }
      }
    }
  }
}
