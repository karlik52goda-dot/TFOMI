package com.example.game.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.example.game.engine.GameEngine
import com.example.game.model.*
import com.example.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private data class CameraLayout(
  val scale: Float,
  val cameraX: Float,
  val verticalOffset: Float,
)

private fun computeCameraLayout(
  canvasWidth: Float,
  canvasHeight: Float,
  player: PlayerState,
  currentCamX: Float,
): CameraLayout {
  val fitHeightScale = canvasHeight / WorldConst.ROOM_HEIGHT
  val fitWidthScale = canvasWidth / WorldConst.ROOM_WIDTH
  val isPortrait = canvasWidth < canvasHeight * 0.98f

  val scale = if (isPortrait) {
    min(fitHeightScale * 0.94f, canvasWidth / (WorldConst.ROOM_WIDTH * 0.56f))
      .coerceAtMost(fitHeightScale)
  } else {
    min(fitHeightScale, fitWidthScale * 1.25f)
      .coerceAtMost(fitHeightScale)
  }

  val visibleWorldWidth = canvasWidth / scale
  val targetCamX = if (visibleWorldWidth >= WorldConst.ROOM_WIDTH) {
    -(visibleWorldWidth - WorldConst.ROOM_WIDTH) * 0.5f
  } else {
    val playerCenter = player.x + player.width * 0.5f
    (playerCenter - visibleWorldWidth * 0.5f).coerceIn(0f, WorldConst.ROOM_WIDTH - visibleWorldWidth)
  }
  val nextCamX = currentCamX + (targetCamX - currentCamX) * 0.2f
  val verticalRatio = if (isPortrait) 0.78f else 0.5f
  val verticalOffset = ((canvasHeight - WorldConst.ROOM_HEIGHT * scale) * verticalRatio).coerceAtLeast(0f)

  return CameraLayout(scale = scale, cameraX = nextCamX, verticalOffset = verticalOffset)
}

@Composable
fun GameCanvas(
  engine: GameEngine,
  frameTick: Long,
  onTapAimChange: (Vec2?) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  var cameraX by remember { mutableFloatStateOf(0f) }
  var lastScale by remember { mutableFloatStateOf(1f) }
  var lastVerticalOffset by remember { mutableFloatStateOf(0f) }

  Canvas(
    modifier = modifier
      .fillMaxSize()
      .background(StormVoid)
      .clipToBounds()
      .pointerInput(Unit) {
        while (true) {
          awaitPointerEventScope {
            val down = awaitFirstDown(requireUnconsumed = false)
            fun emitWorldPoint(px: Float, py: Float) {
              val s = lastScale.coerceAtLeast(0.01f)
              val wx = (px / s) + cameraX
              val wy = (py - lastVerticalOffset) / s
              onTapAimChange(Vec2(wx, wy))
            }
            emitWorldPoint(down.position.x, down.position.y)
            while (true) {
              val event = awaitPointerEvent(PointerEventPass.Main)
              val tracked = event.changes.firstOrNull { it.id == down.id }
              if (tracked == null || !tracked.pressed) {
                onTapAimChange(null)
                break
              }
              emitWorldPoint(tracked.position.x, tracked.position.y)
            }
          }
        }
      }
  ) {
    val tick = frameTick
    val room = engine.floorMap.currentRoom

    drawOuterStormBackdrop(room, tick)

    val layout = computeCameraLayout(size.width, size.height, engine.player, cameraX)
    cameraX = layout.cameraX
    lastScale = layout.scale
    lastVerticalOffset = layout.verticalOffset

    val shakeX = if (engine.screenShake > 0.2f) (Random.nextFloat() - 0.5f) * engine.screenShake else 0f
    val shakeY = if (engine.screenShake > 0.2f) (Random.nextFloat() - 0.5f) * engine.screenShake else 0f

    withTransform({
      translate(left = -layout.cameraX * layout.scale + shakeX, top = layout.verticalOffset + shakeY)
      scale(scaleX = layout.scale, scaleY = layout.scale, pivot = Offset.Zero)
    }) {
      drawRoomBackground(room)
      drawStains(room)
      drawTiles(room)
      drawDoorways(room)
      drawBuilds(room)
      drawSaws(room)
      drawPickups(room, tick)
      drawEnemies(room)
      drawAimOverlay(engine, room, tick)
      drawRemotePlayer(engine, room)
      drawPlayer(engine.player, engine.playerSkin)
      drawProjectiles(engine.projectiles)
      drawParticles(engine.particles)
      drawStormWalls(room, tick)
      drawRect(
        color = StormViolet.copy(alpha = 0.55f),
        topLeft = Offset.Zero,
        size = Size(WorldConst.ROOM_WIDTH, WorldConst.ROOM_HEIGHT),
        style = Stroke(4f),
      )
    }
  }
}

private fun DrawScope.drawAimOverlay(engine: GameEngine, room: DungeonRoom, tick: Long) {
  val px = engine.player.x + engine.player.width * 0.5f
  val py = engine.player.y + engine.player.height * 0.45f

  val manualPos = engine.manualTargetPos
  if (manualPos != null) {
    drawLine(
      color = TearCyan.copy(alpha = 0.45f),
      start = Offset(px, py),
      end = Offset(manualPos.x, manualPos.y),
      strokeWidth = 2.5f,
      pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
    )
    drawCircle(TearCyan.copy(alpha = 0.25f), 20f, Offset(manualPos.x, manualPos.y))
    drawCircle(TearCyan, 14f, Offset(manualPos.x, manualPos.y), style = Stroke(2.5f))
    drawCircle(Color.White, 4f, Offset(manualPos.x, manualPos.y))
    return
  }

  if (engine.autoAimEnabled) {
    val locked = room.enemies.firstOrNull { it.id == engine.lockedEnemyId }
    if (locked != null) {
      val ex = locked.x + locked.width * 0.5f
      val ey = locked.y + locked.height * 0.5f
      val r = (maxOf(locked.width, locked.height) * 0.65f) + sin(tick * 0.15f) * 3f
      drawLine(
        color = TearCyan.copy(alpha = 0.28f),
        start = Offset(px, py),
        end = Offset(ex, ey),
        strokeWidth = 2f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 10f)),
      )
      drawCircle(TearCyan.copy(alpha = 0.72f), r, Offset(ex, ey), style = Stroke(2.5f))
      drawLine(TearCyan.copy(alpha = 0.8f), Offset(ex - r - 5f, ey), Offset(ex - r + 5f, ey), strokeWidth = 2.5f)
      drawLine(TearCyan.copy(alpha = 0.8f), Offset(ex + r - 5f, ey), Offset(ex + r + 5f, ey), strokeWidth = 2.5f)
      drawLine(TearCyan.copy(alpha = 0.8f), Offset(ex, ey - r - 5f), Offset(ex, ey - r + 5f), strokeWidth = 2.5f)
      drawLine(TearCyan.copy(alpha = 0.8f), Offset(ex, ey + r - 5f), Offset(ex, ey + r + 5f), strokeWidth = 2.5f)
    }
  }
}

private fun DrawScope.drawOuterStormBackdrop(room: DungeonRoom, tick: Long) {
  val skyGlow = when (room.type) {
    RoomType.BOSS -> Color(0xFF3A0F2E)
    RoomType.TREASURE -> Color(0xFF281947)
    else -> Color(0xFF211138)
  }
  drawRect(
    brush = Brush.verticalGradient(
      listOf(skyGlow, StormVoid, Color(0xFF140B20))
    ),
    topLeft = Offset.Zero,
    size = size,
  )
  val pulse = sin(tick * 0.04f) * 18f
  drawCircle(
    color = StormViolet.copy(alpha = 0.14f),
    radius = size.width * 0.55f,
    center = Offset(size.width * 0.25f + pulse, size.height * 0.12f),
  )
  drawCircle(
    color = TearCyan.copy(alpha = 0.06f),
    radius = size.width * 0.42f,
    center = Offset(size.width * 0.8f - pulse, size.height * 0.18f),
  )
  val cols = 6
  val step = size.width / cols
  for (i in 1 until cols) {
    drawLine(
      color = Color.White.copy(alpha = 0.03f),
      start = Offset(i * step, 0f),
      end = Offset(i * step, size.height),
      strokeWidth = 2f,
    )
  }
}

private fun DrawScope.drawRoomBackground(room: DungeonRoom) {
  val bgTop = when (room.type) {
    RoomType.BOSS -> Color(0xFF2C0D26)
    RoomType.TREASURE -> Color(0xFF211738)
    else -> Color(0xFF171026)
  }
  drawRect(
    brush = Brush.verticalGradient(listOf(bgTop, DungeonStone)),
    topLeft = Offset.Zero,
    size = Size(WorldConst.ROOM_WIDTH, WorldConst.ROOM_HEIGHT),
  )
  val step = WorldConst.TILE_SIZE
  val lineColor = Color.White.copy(alpha = 0.045f)
  for (c in 1 until WorldConst.GRID_COLS) {
    drawLine(lineColor, Offset(c * step, 0f), Offset(c * step, WorldConst.ROOM_HEIGHT), strokeWidth = 1f)
  }
  for (r in 1 until WorldConst.GRID_ROWS) {
    drawLine(lineColor, Offset(0f, r * step), Offset(WorldConst.ROOM_WIDTH, r * step), strokeWidth = 1f)
  }
}

private fun DrawScope.drawStains(room: DungeonRoom) {
  for (s in room.stains) {
    drawRect(
      color = MeatCrimson.copy(alpha = s.alpha * 0.65f),
      topLeft = Offset(s.x, s.y),
      size = Size(s.width, s.height),
    )
  }
}

private fun DrawScope.drawTiles(room: DungeonRoom) {
  val ts = WorldConst.TILE_SIZE
  for (r in 0 until WorldConst.GRID_ROWS) {
    for (c in 0 until WorldConst.GRID_COLS) {
      val x = c * ts
      val y = r * ts
      when (room.tiles[r][c]) {
        TileKind.SOLID -> {
          drawRect(Color(0xFF34254A), Offset(x, y), Size(ts, ts))
          drawRect(Color(0xFF4D386E), Offset(x + 2f, y + 2f), Size(ts - 4f, 6f))
          drawRect(Color(0xFF221633), Offset(x, y), Size(ts, ts), style = Stroke(2f))
        }
        TileKind.ONE_WAY -> {
          drawRoundRect(
            color = Color(0xFF5D4682),
            topLeft = Offset(x + 2f, y),
            size = Size(ts - 4f, 12f),
            cornerRadius = CornerRadius(4f, 4f),
          )
          drawRect(TearCyan.copy(alpha = 0.35f), Offset(x + 6f, y + 2f), Size(ts - 12f, 3f))
        }
        TileKind.CRATE -> {
          drawRect(BuildWood, Offset(x + 2f, y + 2f), Size(ts - 4f, ts - 4f))
          drawRect(BuildWoodDark, Offset(x + 2f, y + 2f), Size(ts - 4f, ts - 4f), style = Stroke(3f))
          drawLine(BuildWoodDark, Offset(x + 4f, y + 4f), Offset(x + ts - 4f, y + ts - 4f), strokeWidth = 3f)
          drawLine(BuildWoodDark, Offset(x + ts - 4f, y + 4f), Offset(x + 4f, y + ts - 4f), strokeWidth = 3f)
        }
        TileKind.SPIKE -> {
          val path = Path().apply {
            for (i in 0 until 3) {
              val sx = x + i * (ts / 3f)
              moveTo(sx, y + ts)
              lineTo(sx + ts / 6f, y + ts * 0.32f)
              lineTo(sx + ts / 3f, y + ts)
            }
            close()
          }
          drawPath(path, Color(0xFFD6DCE8))
          drawPath(path, MeatCrimson.copy(alpha = 0.7f), style = Stroke(2f))
        }
        TileKind.EMPTY -> Unit
      }
    }
  }
}

private fun DrawScope.drawDoorways(room: DungeonRoom) {
  val ts = WorldConst.TILE_SIZE
  val doorColor = if (room.cleared) SuccessGreen else MeatCrimson
  val glowColor = if (room.cleared) TearCyan else MeatCrimson

  fun drawChevron(cx: Float, cy: Float, dx: Float, dy: Float, size: Float) {
    val path = Path().apply {
      when {
        dx < 0f -> {
          moveTo(cx + size * 0.4f, cy - size)
          lineTo(cx - size * 0.6f, cy)
          lineTo(cx + size * 0.4f, cy + size)
        }
        dx > 0f -> {
          moveTo(cx - size * 0.4f, cy - size)
          lineTo(cx + size * 0.6f, cy)
          lineTo(cx - size * 0.4f, cy + size)
        }
        dy < 0f -> {
          moveTo(cx - size, cy + size * 0.4f)
          lineTo(cx, cy - size * 0.6f)
          lineTo(cx + size, cy + size * 0.4f)
        }
        else -> {
          moveTo(cx - size, cy - size * 0.4f)
          lineTo(cx, cy + size * 0.6f)
          lineTo(cx + size, cy - size * 0.4f)
        }
      }
    }
    drawPath(path, glowColor.copy(alpha = 0.9f), style = Stroke(3.5f))
  }

  if (Direction.WEST in room.doors) {
    drawRect(doorColor.copy(alpha = 0.28f), Offset(0f, 8 * ts), Size(ts, 3 * ts))
    drawRect(glowColor.copy(alpha = 0.7f), Offset(0f, 8 * ts), Size(8f, 3 * ts))
    if (!room.cleared) {
      drawLine(MeatCrimson, Offset(14f, 8 * ts), Offset(14f, 11 * ts), strokeWidth = 4f)
      drawLine(MeatCrimson, Offset(28f, 8 * ts), Offset(28f, 11 * ts), strokeWidth = 4f)
    } else {
      drawChevron(ts * 0.55f, 9.5f * ts, -1f, 0f, 10f)
    }
  }
  if (Direction.EAST in room.doors) {
    drawRect(doorColor.copy(alpha = 0.28f), Offset(WorldConst.ROOM_WIDTH - ts, 8 * ts), Size(ts, 3 * ts))
    drawRect(glowColor.copy(alpha = 0.7f), Offset(WorldConst.ROOM_WIDTH - 8f, 8 * ts), Size(8f, 3 * ts))
    if (!room.cleared) {
      drawLine(MeatCrimson, Offset(WorldConst.ROOM_WIDTH - 14f, 8 * ts), Offset(WorldConst.ROOM_WIDTH - 14f, 11 * ts), strokeWidth = 4f)
      drawLine(MeatCrimson, Offset(WorldConst.ROOM_WIDTH - 28f, 8 * ts), Offset(WorldConst.ROOM_WIDTH - 28f, 11 * ts), strokeWidth = 4f)
    } else {
      drawChevron(WorldConst.ROOM_WIDTH - ts * 0.55f, 9.5f * ts, 1f, 0f, 10f)
    }
  }
  if (Direction.NORTH in room.doors) {
    drawRect(doorColor.copy(alpha = 0.30f), Offset(9 * ts, 0f), Size(2 * ts, ts))
    drawRect(glowColor.copy(alpha = 0.75f), Offset(9 * ts, 0f), Size(2 * ts, 8f))
    if (!room.cleared) {
      drawLine(MeatCrimson, Offset(9 * ts, 16f), Offset(11 * ts, 16f), strokeWidth = 4f)
    } else {
      drawChevron(10f * ts, ts * 0.5f, 0f, -1f, 10f)
    }
  }
  if (Direction.SOUTH in room.doors) {
    val top = WorldConst.ROOM_HEIGHT - ts
    drawRect(doorColor.copy(alpha = 0.28f), Offset(9 * ts, top), Size(2 * ts, ts))
    drawRect(glowColor.copy(alpha = 0.8f), Offset(9 * ts, WorldConst.ROOM_HEIGHT - 8f), Size(2 * ts, 8f))
    drawLine(glowColor, Offset(9 * ts, top), Offset(9 * ts, WorldConst.ROOM_HEIGHT), strokeWidth = 3f)
    drawLine(glowColor, Offset(11 * ts, top), Offset(11 * ts, WorldConst.ROOM_HEIGHT), strokeWidth = 3f)
    if (!room.cleared) {
      drawLine(MeatCrimson, Offset(9 * ts, top + 6f), Offset(11 * ts, top + 6f), strokeWidth = 5f)
      drawLine(MeatCrimson, Offset(9 * ts, top + 20f), Offset(11 * ts, top + 20f), strokeWidth = 4f)
    } else {
      drawChevron(10f * ts, top + ts * 0.52f, 0f, 1f, 10f)
    }
  }
}

private fun DrawScope.drawBuilds(room: DungeonRoom) {
  for (b in room.builds) {
    val baseColor = if (b.isMetal) BuildMetal else BuildWood
    val borderColor = if (b.isMetal) Color(0xFFBCE3FF) else BuildWoodDark
    val alpha = (0.4f + 0.6f * (b.hp.toFloat() / b.maxHp)).coerceIn(0.35f, 1f)
    val scale = 0.5f + 0.5f * b.animProgress
    val cx = b.x + b.width * 0.5f
    val cy = b.y + b.height * 0.5f
    val w = b.width * scale
    val h = b.height * scale
    drawRoundRect(
      color = baseColor.copy(alpha = alpha),
      topLeft = Offset(cx - w * 0.5f, cy - h * 0.5f),
      size = Size(w, h),
      cornerRadius = CornerRadius(4f, 4f),
    )
    drawRoundRect(
      color = borderColor,
      topLeft = Offset(cx - w * 0.5f, cy - h * 0.5f),
      size = Size(w, h),
      cornerRadius = CornerRadius(4f, 4f),
      style = Stroke(2.5f),
    )
  }
}

private fun DrawScope.drawSaws(room: DungeonRoom) {
  for (saw in room.saws) {
    drawLine(
      Color(0xFF5A4975),
      Offset(saw.startX, saw.startY),
      Offset(saw.endX, saw.endY),
      strokeWidth = 4f,
    )
    rotate(degrees = saw.angle, pivot = Offset(saw.x, saw.y)) {
      val teeth = 10
      val outerR = saw.radius + 5f
      val innerR = saw.radius - 3f
      val path = Path()
      for (i in 0 until teeth * 2) {
        val r = if (i % 2 == 0) outerR else innerR
        val ang = (i * PI / teeth).toFloat()
        val px = saw.x + cos(ang) * r
        val py = saw.y + sin(ang) * r
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
      }
      path.close()
      drawPath(path, Color(0xFFDDE5F0))
      drawPath(path, MeatCrimson.copy(alpha = 0.85f), style = Stroke(2f))
      drawCircle(Color(0xFF475569), radius = saw.radius * 0.35f, center = Offset(saw.x, saw.y))
    }
  }
}

private fun DrawScope.drawPickups(room: DungeonRoom, tick: Long) {
  for (pk in room.pickups) {
    val bob = sin(pk.bobPhase) * 4f
    val y = pk.y + bob
    when (pk.type) {
      PickupType.RED_HEART -> {
        drawCircle(MeatCrimson, 11f, Offset(pk.x, y))
        drawCircle(Color.White.copy(alpha = 0.7f), 3.5f, Offset(pk.x - 3f, y - 3f))
      }
      PickupType.SHIELD_POTION -> {
        drawRoundRect(ShieldBlue, Offset(pk.x - 10f, y - 12f), Size(20f, 24f), CornerRadius(6f, 6f))
        drawRect(Color.White.copy(alpha = 0.85f), Offset(pk.x - 5f, y - 16f), Size(10f, 5f))
      }
      PickupType.MATS_PACK -> {
        drawRect(BuildWood, Offset(pk.x - 11f, y - 9f), Size(22f, 18f))
        drawRect(BuildWoodDark, Offset(pk.x - 11f, y - 9f), Size(22f, 18f), style = Stroke(2.5f))
      }
      PickupType.LOOT_CHEST -> {
        val c = pk.rarity.color
        drawCircle(c.copy(alpha = 0.28f), 28f + sin(tick * 0.1f) * 3f, Offset(pk.x, y))
        drawRoundRect(c, Offset(pk.x - 18f, y - 14f), Size(36f, 28f), CornerRadius(5f, 5f))
        drawRect(Color(0xFF1F162B), Offset(pk.x - 18f, y - 3f), Size(36f, 5f))
        drawCircle(LootLegendary, 5f, Offset(pk.x, y))
      }
      PickupType.RELIC_PEDESTAL -> {
        val c = pk.relic?.badgeColor ?: TearCyan
        drawRect(Color(0xFF5A4975), Offset(pk.x - 16f, pk.y + 8f), Size(32f, 14f))
        drawCircle(c.copy(alpha = 0.3f), 26f, Offset(pk.x, y - 6f))
        drawCircle(c, 13f, Offset(pk.x, y - 6f))
        drawCircle(Color.White, 5f, Offset(pk.x - 3f, y - 9f))
      }
      PickupType.EXIT_PORTAL -> {
        drawCircle(StormViolet.copy(alpha = 0.38f), 34f, Offset(pk.x, pk.y))
        drawCircle(StormViolet, 24f, Offset(pk.x, pk.y), style = Stroke(4f))
        drawCircle(TearCyan, 12f, Offset(pk.x, pk.y))
      }
    }
  }
}

private fun DrawScope.drawEnemies(room: DungeonRoom) {
  for (e in room.enemies) {
    val bodyColor = if (e.hitFlash > 0f) Color.White else when (e.type) {
      EnemyType.MEAT_HOPPER -> Color(0xFFD94A5A)
      EnemyType.BLOOD_FLY -> Color(0xFF8B2E4E)
      EnemyType.SAW_TURRET -> Color(0xFF6B7280)
      EnemyType.BOSS_STORM_MONSTRO -> Color(0xFFE06D85)
      EnemyType.BOSS_DUKE_OF_MEAT -> Color(0xFFA8244B)
      EnemyType.BOSS_SAW_MECH -> Color(0xFF64748B)
      EnemyType.BOSS_STORM_CUBE -> Color(0xFF7C2AE8)
    }

    // Wings for Duke of Meat boss
    if (e.type == EnemyType.BOSS_DUKE_OF_MEAT) {
      val flap = sin(e.moveTimer * 14f) * 10f
      drawOval(
        color = Color(0xFFD65C7A).copy(alpha = 0.85f),
        topLeft = Offset(e.x - 18f, e.y + 12f + flap),
        size = Size(22f, 30f),
      )
      drawOval(
        color = Color(0xFFD65C7A).copy(alpha = 0.85f),
        topLeft = Offset(e.x + e.width - 4f, e.y + 12f + flap),
        size = Size(22f, 30f),
      )
    }

    val corner = if (e.type == EnemyType.BOSS_STORM_CUBE) 6f else 12f
    drawRoundRect(
      color = bodyColor,
      topLeft = Offset(e.x, e.y),
      size = Size(e.width, e.height),
      cornerRadius = CornerRadius(corner, corner),
    )
    val strokeColor = when (e.type) {
      EnemyType.BOSS_STORM_CUBE -> TearCyan
      EnemyType.BOSS_SAW_MECH -> Color(0xFFCBD5E1)
      else -> Color(0xFF2B111C)
    }
    drawRoundRect(
      color = strokeColor,
      topLeft = Offset(e.x, e.y),
      size = Size(e.width, e.height),
      cornerRadius = CornerRadius(corner, corner),
      style = Stroke(if (e.type.isBoss) 3.5f else 2.5f),
    )

    when (e.type) {
      EnemyType.BOSS_SAW_MECH -> {
        // Spinning saw blade crown + glowing red visor
        rotate(degrees = e.moveTimer * 480f, pivot = Offset(e.x + e.width * 0.5f, e.y)) {
          drawCircle(Color(0xFFE2E8F0), 16f, Offset(e.x + e.width * 0.5f, e.y))
          drawCircle(MeatCrimson, 16f, Offset(e.x + e.width * 0.5f, e.y), style = Stroke(3f))
        }
        drawRoundRect(
          color = MeatCrimson,
          topLeft = Offset(e.x + 14f, e.y + e.height * 0.34f),
          size = Size(e.width - 28f, 11f),
          cornerRadius = CornerRadius(4f, 4f),
        )
        drawCircle(
          color = TearCyan,
          radius = 5f,
          center = Offset(e.x + e.width * (if (e.facingRight) 0.65f else 0.35f), e.y + e.height * 0.34f + 5.5f),
        )
      }
      EnemyType.BOSS_STORM_CUBE -> {
        // Glowing Kevin the Cube runes + pulsing center core
        drawRect(
          color = StormGlow.copy(alpha = 0.55f),
          topLeft = Offset(e.x + 12f, e.y + 12f),
          size = Size(e.width - 24f, e.height - 24f),
          style = Stroke(2.5f),
        )
        drawCircle(
          color = TearCyan,
          radius = 10f + sin(e.moveTimer * 8f) * 2.5f,
          center = Offset(e.x + e.width * 0.5f, e.y + e.height * 0.5f),
        )
      }
      EnemyType.BOSS_DUKE_OF_MEAT -> {
        // 3 creepy glowing eyes + jagged grin
        val eyeY = e.y + e.height * 0.34f
        drawCircle(LootLegendary, 7f, Offset(e.x + e.width * 0.28f, eyeY))
        drawCircle(LootLegendary, 8f, Offset(e.x + e.width * 0.50f, eyeY - 6f))
        drawCircle(LootLegendary, 7f, Offset(e.x + e.width * 0.72f, eyeY))
        drawCircle(Color.Black, 3f, Offset(e.x + e.width * 0.28f, eyeY))
        drawCircle(Color.Black, 3.5f, Offset(e.x + e.width * 0.50f, eyeY - 6f))
        drawCircle(Color.Black, 3f, Offset(e.x + e.width * 0.72f, eyeY))
      }
      else -> {
        val eyeY = e.y + e.height * 0.36f
        val eyeX1 = e.x + e.width * 0.32f
        val eyeX2 = e.x + e.width * 0.68f
        drawCircle(Color.White, e.width * 0.12f, Offset(eyeX1, eyeY))
        drawCircle(Color.White, e.width * 0.12f, Offset(eyeX2, eyeY))
        drawCircle(Color.Black, e.width * 0.06f, Offset(eyeX1 + (if (e.facingRight) 2f else -2f), eyeY))
        drawCircle(Color.Black, e.width * 0.06f, Offset(eyeX2 + (if (e.facingRight) 2f else -2f), eyeY))
        if (e.type == EnemyType.BOSS_STORM_MONSTRO) {
          drawRoundRect(
            color = Color(0xFF3A0F1D),
            topLeft = Offset(e.x + 16f, e.y + e.height * 0.62f),
            size = Size(e.width - 32f, 14f),
            cornerRadius = CornerRadius(6f, 6f),
          )
        }
      }
    }

    if (!e.type.isBoss && e.hp < e.maxHp) {
      val ratio = (e.hp / e.maxHp).coerceIn(0f, 1f)
      drawRect(Color.Black.copy(alpha = 0.6f), Offset(e.x, e.y - 8f), Size(e.width, 4f))
      drawRect(MeatCrimson, Offset(e.x, e.y - 8f), Size(e.width * ratio, 4f))
    }
  }
}

private fun DrawScope.drawPlayer(p: PlayerState, skin: PlayerSkin) {
  val cx = p.x + p.width * 0.5f
  val cy = p.y + p.height
  val w = p.width * p.squashX
  val h = p.height * p.squashY
  val left = cx - w * 0.5f
  val top = cy - h
  val alpha = if (p.invulnTimer > 0f && ((p.invulnTimer * 18f).toInt() % 2 == 0)) 0.45f else 1f
  drawSkinCharacter(skin, left, top, w, h, p.facingRight, alpha)
}

private fun DrawScope.drawRemotePlayer(engine: GameEngine, room: DungeonRoom) {
  val rp = engine.remotePlayer ?: return
  if (!rp.alive || rp.roomGx != room.gx || rp.roomGy != room.gy) return
  val isPvp = engine.multiplayerMode == "pvp"
  val ringColor = if (isPvp) MeatCrimson else Color(0xFF00FFA3)
  val w = 32f
  val h = 32f
  drawOval(
    color = ringColor.copy(alpha = 0.35f),
    topLeft = Offset(rp.x - 4f, rp.y + h - 6f),
    size = Size(w + 8f, 10f),
  )
  drawSkinCharacter(rp.skin, rp.x, rp.y, w, h, rp.facingRight, alpha = 0.94f)
  // Floating HP bar above remote player
  val ratio = (rp.hp.toFloat() / rp.maxHp.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
  drawRect(Color.Black.copy(alpha = 0.7f), Offset(rp.x - 2f, rp.y - 10f), Size(w + 4f, 5f))
  drawRect(ringColor, Offset(rp.x - 2f, rp.y - 10f), Size((w + 4f) * ratio, 5f))
}

fun DrawScope.drawSkinCharacter(
  skin: PlayerSkin,
  left: Float,
  top: Float,
  w: Float,
  h: Float,
  facingRight: Boolean = true,
  alpha: Float = 1f,
) {
  val cx = left + w * 0.5f
  val lookShift = if (facingRight) w * 0.09f else -w * 0.09f

  when (skin) {
    PlayerSkin.MEAT_BOY -> {
      drawRoundRect(
        color = MeatCrimson.copy(alpha = alpha),
        topLeft = Offset(left, top),
        size = Size(w, h),
        cornerRadius = CornerRadius(w * 0.22f, w * 0.22f),
      )
      drawRect(
        color = ShieldBlue.copy(alpha = alpha),
        topLeft = Offset(left + 1f, top + h * 0.16f),
        size = Size(w - 2f, h * 0.16f),
      )
      val eyeR = w * 0.13f
      val pupilR = w * 0.065f
      val eyeY = top + h * 0.54f
      drawCircle(Color.White.copy(alpha = alpha), eyeR, Offset(cx - w * 0.16f + lookShift, eyeY))
      drawCircle(Color.White.copy(alpha = alpha), eyeR, Offset(cx + w * 0.16f + lookShift, eyeY))
      drawCircle(Color.Black.copy(alpha = alpha), pupilR, Offset(cx - w * 0.13f + lookShift * 1.2f, eyeY))
      drawCircle(Color.Black.copy(alpha = alpha), pupilR, Offset(cx + w * 0.19f + lookShift * 1.2f, eyeY))
    }

    PlayerSkin.BANANA -> {
      // Top stem & bottom tip
      drawRoundRect(
        color = Color(0xFF5C3A16).copy(alpha = alpha),
        topLeft = Offset(cx - w * 0.12f + lookShift * 0.5f, top - h * 0.06f),
        size = Size(w * 0.24f, h * 0.18f),
        cornerRadius = CornerRadius(3f, 3f),
      )
      // Banana yellow torso
      drawRoundRect(
        color = Color(0xFFFFD93D).copy(alpha = alpha),
        topLeft = Offset(left + w * 0.14f, top + h * 0.06f),
        size = Size(w * 0.72f, h * 0.90f),
        cornerRadius = CornerRadius(w * 0.36f, w * 0.36f),
      )
      drawRoundRect(
        color = Color(0xFF92400E).copy(alpha = alpha),
        topLeft = Offset(left + w * 0.14f, top + h * 0.06f),
        size = Size(w * 0.72f, h * 0.90f),
        cornerRadius = CornerRadius(w * 0.36f, w * 0.36f),
        style = Stroke(w * 0.06f),
      )
      // Bottom brown tip
      drawCircle(Color(0xFF5C3A16).copy(alpha = alpha), w * 0.08f, Offset(cx, top + h * 0.94f))
      // Cheerful eyes + smile
      val eyeY = top + h * 0.44f
      drawCircle(Color(0xFF1F1606).copy(alpha = alpha), w * 0.075f, Offset(cx - w * 0.12f + lookShift, eyeY))
      drawCircle(Color(0xFF1F1606).copy(alpha = alpha), w * 0.075f, Offset(cx + w * 0.12f + lookShift, eyeY))
      drawLine(
        color = Color(0xFF1F1606).copy(alpha = alpha),
        start = Offset(cx - w * 0.14f + lookShift, top + h * 0.62f),
        end = Offset(cx + w * 0.14f + lookShift, top + h * 0.62f),
        strokeWidth = w * 0.07f,
      )
    }

    PlayerSkin.ISAAC -> {
      val skinTone = Color(0xFFF5C6BA).copy(alpha = alpha)
      // Small lower body
      drawRoundRect(
        color = Color(0xFFE6AC9E).copy(alpha = alpha),
        topLeft = Offset(cx - w * 0.28f, top + h * 0.62f),
        size = Size(w * 0.56f, h * 0.38f),
        cornerRadius = CornerRadius(w * 0.16f, w * 0.16f),
      )
      // Iconic giant round Isaac head
      drawOval(
        color = skinTone,
        topLeft = Offset(left + w * 0.04f, top),
        size = Size(w * 0.92f, h * 0.72f),
      )
      drawOval(
        color = Color(0xFF5C3A33).copy(alpha = alpha),
        topLeft = Offset(left + w * 0.04f, top),
        size = Size(w * 0.92f, h * 0.72f),
        style = Stroke(w * 0.055f),
      )
      // Tear streams
      val ex1 = cx - w * 0.18f + lookShift * 0.8f
      val ex2 = cx + w * 0.18f + lookShift * 0.8f
      val eyeY = top + h * 0.34f
      drawRect(
        color = TearCyan.copy(alpha = 0.88f * alpha),
        topLeft = Offset(ex1 - w * 0.07f, eyeY),
        size = Size(w * 0.14f, h * 0.30f),
      )
      drawRect(
        color = TearCyan.copy(alpha = 0.88f * alpha),
        topLeft = Offset(ex2 - w * 0.07f, eyeY),
        size = Size(w * 0.14f, h * 0.30f),
      )
      // Giant crying eyes + white glints
      drawCircle(Color(0xFF1A1118).copy(alpha = alpha), w * 0.12f, Offset(ex1, eyeY))
      drawCircle(Color(0xFF1A1118).copy(alpha = alpha), w * 0.12f, Offset(ex2, eyeY))
      drawCircle(Color.White.copy(alpha = alpha), w * 0.045f, Offset(ex1 - w * 0.04f, eyeY - w * 0.04f))
      drawCircle(Color.White.copy(alpha = alpha), w * 0.045f, Offset(ex2 - w * 0.04f, eyeY - w * 0.04f))
    }

    PlayerSkin.MR_BEAST -> {
      // Cyan & pink MrBeast hoodie torso
      drawRoundRect(
        color = Color(0xFF00C2FF).copy(alpha = alpha),
        topLeft = Offset(left + w * 0.06f, top + h * 0.42f),
        size = Size(w * 0.88f, h * 0.58f),
        cornerRadius = CornerRadius(w * 0.2f, w * 0.2f),
      )
      // Pink panther chest stripe + gold $ badge
      drawRect(
        color = Color(0xFFFF409A).copy(alpha = alpha),
        topLeft = Offset(left + w * 0.16f, top + h * 0.68f),
        size = Size(w * 0.68f, h * 0.16f),
      )
      drawCircle(LootLegendary.copy(alpha = alpha), w * 0.09f, Offset(cx, top + h * 0.76f))
      // Head + brown hair
      drawRoundRect(
        color = Color(0xFFF6C7A9).copy(alpha = alpha),
        topLeft = Offset(left + w * 0.12f, top + h * 0.06f),
        size = Size(w * 0.76f, h * 0.52f),
        cornerRadius = CornerRadius(w * 0.2f, w * 0.2f),
      )
      drawRoundRect(
        color = Color(0xFF4B3222).copy(alpha = alpha),
        topLeft = Offset(left + w * 0.10f, top),
        size = Size(w * 0.80f, h * 0.20f),
        cornerRadius = CornerRadius(w * 0.14f, w * 0.14f),
      )
      // Cool dark sunglasses
      val glassY = top + h * 0.25f
      drawRoundRect(
        color = Color(0xFF0F172A).copy(alpha = alpha),
        topLeft = Offset(cx - w * 0.30f + lookShift, glassY),
        size = Size(w * 0.26f, h * 0.15f),
        cornerRadius = CornerRadius(3f, 3f),
      )
      drawRoundRect(
        color = Color(0xFF0F172A).copy(alpha = alpha),
        topLeft = Offset(cx + w * 0.04f + lookShift, glassY),
        size = Size(w * 0.26f, h * 0.15f),
        cornerRadius = CornerRadius(3f, 3f),
      )
      drawLine(
        color = TearCyan.copy(alpha = alpha),
        start = Offset(cx - w * 0.04f + lookShift, glassY + h * 0.06f),
        end = Offset(cx + w * 0.04f + lookShift, glassY + h * 0.06f),
        strokeWidth = w * 0.06f,
      )
    }

    PlayerSkin.OLIVER_200 -> {
      // Baggy light-wash JNCO denim jeans at the bottom
      drawRoundRect(
        color = Color(0xFF60A5FA).copy(alpha = alpha),
        topLeft = Offset(left + w * 0.12f, top + h * 0.78f),
        size = Size(w * 0.34f, h * 0.22f),
        cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
      )
      drawRoundRect(
        color = Color(0xFF60A5FA).copy(alpha = alpha),
        topLeft = Offset(left + w * 0.54f, top + h * 0.78f),
        size = Size(w * 0.34f, h * 0.22f),
        cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
      )

      // Oversized 90s color-blocked windbreaker (purple base, hot-pink chest, blue stripes)
      drawRoundRect(
        color = Color(0xFF7C3AED).copy(alpha = alpha),
        topLeft = Offset(left + w * 0.03f, top + h * 0.44f),
        size = Size(w * 0.94f, h * 0.40f),
        cornerRadius = CornerRadius(w * 0.20f, w * 0.20f),
      )
      // Hot pink windbreaker color-block
      drawRoundRect(
        color = Color(0xFFFF2E93).copy(alpha = alpha),
        topLeft = Offset(left + w * 0.08f, top + h * 0.46f),
        size = Size(w * 0.84f, h * 0.20f),
        cornerRadius = CornerRadius(w * 0.10f, w * 0.10f),
      )
      // Electric blue windbreaker hem & collar trim
      drawRect(
        color = Color(0xFF2563EB).copy(alpha = alpha),
        topLeft = Offset(left + w * 0.10f, top + h * 0.68f),
        size = Size(w * 0.80f, h * 0.11f),
      )
      // White zipper line down center
      drawLine(
        color = Color.White.copy(alpha = 0.85f * alpha),
        start = Offset(cx + lookShift * 0.2f, top + h * 0.46f),
        end = Offset(cx + lookShift * 0.2f, top + h * 0.82f),
        strokeWidth = w * 0.045f,
      )

      // Face
      drawRoundRect(
        color = Color(0xFFF6D0B4).copy(alpha = alpha),
        topLeft = Offset(left + w * 0.16f, top + h * 0.14f),
        size = Size(w * 0.68f, h * 0.36f),
        cornerRadius = CornerRadius(w * 0.18f, w * 0.18f),
      )

      // Iconic thick straight-across brown bowl-cut wig
      val bowlBrown = Color(0xFF4A2B18).copy(alpha = alpha)
      val bowlHighlight = Color(0xFF6B3F25).copy(alpha = alpha)
      // Dome top of bowl cut
      drawOval(
        color = bowlBrown,
        topLeft = Offset(left + w * 0.06f, top - h * 0.02f),
        size = Size(w * 0.88f, h * 0.34f),
      )
      // Straight flat blunt bangs across forehead + side flaps
      drawRect(
        color = bowlBrown,
        topLeft = Offset(left + w * 0.08f, top + h * 0.11f),
        size = Size(w * 0.84f, h * 0.14f),
      )
      drawRect(
        color = bowlBrown,
        topLeft = Offset(left + w * 0.08f, top + h * 0.16f),
        size = Size(w * 0.14f, h * 0.20f),
      )
      drawRect(
        color = bowlBrown,
        topLeft = Offset(left + w * 0.78f, top + h * 0.16f),
        size = Size(w * 0.14f, h * 0.20f),
      )
      // Subtle bowl highlight arc
      drawLine(
        color = bowlHighlight,
        start = Offset(left + w * 0.22f, top + h * 0.08f),
        end = Offset(left + w * 0.78f, top + h * 0.08f),
        strokeWidth = w * 0.05f,
      )

      // Iconic round/oval red-tinted sunglasses
      val glassY = top + h * 0.255f
      val lensW = w * 0.26f
      val lensH = h * 0.145f
      val leftLensX = cx - w * 0.28f + lookShift
      val rightLensX = cx + w * 0.02f + lookShift
      drawOval(
        color = Color(0xFFEF4444).copy(alpha = alpha),
        topLeft = Offset(leftLensX, glassY),
        size = Size(lensW, lensH),
      )
      drawOval(
        color = Color(0xFFEF4444).copy(alpha = alpha),
        topLeft = Offset(rightLensX, glassY),
        size = Size(lensW, lensH),
      )
      drawOval(
        color = Color(0xFF1E1B24).copy(alpha = alpha),
        topLeft = Offset(leftLensX, glassY),
        size = Size(lensW, lensH),
        style = Stroke(w * 0.045f),
      )
      drawOval(
        color = Color(0xFF1E1B24).copy(alpha = alpha),
        topLeft = Offset(rightLensX, glassY),
        size = Size(lensW, lensH),
        style = Stroke(w * 0.045f),
      )
      // Bridge & lens glint
      drawLine(
        color = Color(0xFF1E1B24).copy(alpha = alpha),
        start = Offset(leftLensX + lensW, glassY + lensH * 0.45f),
        end = Offset(rightLensX, glassY + lensH * 0.45f),
        strokeWidth = w * 0.045f,
      )
      drawCircle(Color.White.copy(alpha = 0.75f * alpha), w * 0.032f, Offset(leftLensX + lensW * 0.3f, glassY + lensH * 0.3f))
      drawCircle(Color.White.copy(alpha = 0.75f * alpha), w * 0.032f, Offset(rightLensX + lensW * 0.3f, glassY + lensH * 0.3f))
      // Deadpan smirk
      drawLine(
        color = Color(0xFF5C3A28).copy(alpha = alpha),
        start = Offset(cx - w * 0.09f + lookShift, top + h * 0.43f),
        end = Offset(cx + w * 0.09f + lookShift, top + h * 0.43f),
        strokeWidth = w * 0.045f,
      )
    }

    PlayerSkin.CHEKUSHKA -> {
      // Photo 1: Clear frosted glass bottle, silver cap + blue neck band, silver ticket label with blue "Чекушка №3"
      // Silver screw cap + top blue rim
      drawRoundRect(
        color = Color(0xFFE2E8F0).copy(alpha = alpha),
        topLeft = Offset(cx - w * 0.14f, top),
        size = Size(w * 0.28f, h * 0.14f),
        cornerRadius = CornerRadius(w * 0.04f, w * 0.04f),
      )
      drawRect(
        color = Color(0xFF1D4ED8).copy(alpha = alpha),
        topLeft = Offset(cx - w * 0.14f, top),
        size = Size(w * 0.28f, h * 0.03f),
      )
      // Royal blue neck band ("Чекушка")
      drawRect(
        color = Color(0xFF1E40AF).copy(alpha = alpha),
        topLeft = Offset(cx - w * 0.15f, top + h * 0.13f),
        size = Size(w * 0.30f, h * 0.08f),
      )
      drawLine(
        color = Color.White.copy(alpha = 0.8f * alpha),
        start = Offset(cx - w * 0.10f, top + h * 0.17f),
        end = Offset(cx + w * 0.10f, top + h * 0.17f),
        strokeWidth = w * 0.028f,
      )

      // Clear frosty glass body with rounded shoulders
      drawRoundRect(
        color = Color(0xFFE2E8F0).copy(alpha = 0.92f * alpha),
        topLeft = Offset(left + w * 0.15f, top + h * 0.20f),
        size = Size(w * 0.70f, h * 0.78f),
        cornerRadius = CornerRadius(w * 0.22f, w * 0.22f),
      )
      // Subtle translucent glass inner shine & shadow
      drawRoundRect(
        color = Color.White.copy(alpha = 0.65f * alpha),
        topLeft = Offset(left + w * 0.20f, top + h * 0.24f),
        size = Size(w * 0.13f, h * 0.68f),
        cornerRadius = CornerRadius(w * 0.06f, w * 0.06f),
      )
      drawRoundRect(
        color = Color(0xFF94A3B8).copy(alpha = alpha),
        topLeft = Offset(left + w * 0.15f, top + h * 0.20f),
        size = Size(w * 0.70f, h * 0.78f),
        cornerRadius = CornerRadius(w * 0.22f, w * 0.22f),
        style = Stroke(w * 0.045f),
      )

      // Metallic silver perforated ticket label
      val lblX = left + w * 0.19f
      val lblY = top + h * 0.41f
      val lblW = w * 0.62f
      val lblH = h * 0.36f
      drawRoundRect(
        color = Color(0xFFCBD5E1).copy(alpha = alpha),
        topLeft = Offset(lblX, lblY),
        size = Size(lblW, lblH),
        cornerRadius = CornerRadius(w * 0.03f, w * 0.03f),
      )
      // Side dark perforations on ticket label
      drawRect(Color(0xFF64748B).copy(alpha = alpha), Offset(lblX, lblY), Size(w * 0.05f, lblH))
      drawRect(Color(0xFF64748B).copy(alpha = alpha), Offset(lblX + lblW - w * 0.05f, lblY), Size(w * 0.05f, lblH))
      // Bold blue "Чекушка" header bar + cyan "Душевная" + "№3" accent
      drawRect(
        color = Color(0xFF1D4ED8).copy(alpha = alpha),
        topLeft = Offset(lblX + w * 0.07f, lblY + h * 0.04f),
        size = Size(lblW - w * 0.14f, h * 0.09f),
      )
      drawRect(
        color = Color(0xFF0EA5E9).copy(alpha = alpha),
        topLeft = Offset(lblX + w * 0.07f, lblY + h * 0.16f),
        size = Size(lblW * 0.48f, h * 0.055f),
      )
      // "№3" blue vertical accent on right of label
      drawRect(
        color = Color(0xFF1E40AF).copy(alpha = alpha),
        topLeft = Offset(lblX + lblW * 0.66f, lblY + h * 0.15f),
        size = Size(lblW * 0.18f, h * 0.15f),
      )
      // Bottom cyan stripe
      drawRect(
        color = Color(0xFF06B6D4).copy(alpha = alpha),
        topLeft = Offset(lblX + w * 0.07f, lblY + lblH - h * 0.065f),
        size = Size(lblW - w * 0.14f, h * 0.045f),
      )
    }

    PlayerSkin.MELLSTROY -> {
      // Photo 2: Backwards black cap + black velour jacket with gold Versace shoulder stripes + gold zipper & medallion
      // Black bomber jacket body
      drawRoundRect(
        color = Color(0xFF121216).copy(alpha = alpha),
        topLeft = Offset(left + w * 0.05f, top + h * 0.46f),
        size = Size(w * 0.90f, h * 0.54f),
        cornerRadius = CornerRadius(w * 0.18f, w * 0.18f),
      )
      // Gold geometric/Greek-key stripes down both shoulders & sleeves
      val goldTrim = Color(0xFFEAB308).copy(alpha = alpha)
      val goldDark = Color(0xFF92400E).copy(alpha = alpha)
      drawRoundRect(
        color = goldTrim,
        topLeft = Offset(left + w * 0.06f, top + h * 0.47f),
        size = Size(w * 0.16f, h * 0.46f),
        cornerRadius = CornerRadius(w * 0.06f, w * 0.06f),
      )
      drawRoundRect(
        color = goldTrim,
        topLeft = Offset(left + w * 0.78f, top + h * 0.47f),
        size = Size(w * 0.16f, h * 0.46f),
        cornerRadius = CornerRadius(w * 0.06f, w * 0.06f),
      )
      // Subtle black notch pattern inside gold shoulder stripes
      for (i in 0..2) {
        val py = top + h * (0.53f + i * 0.13f)
        drawRect(Color(0xFF121216).copy(alpha = alpha), Offset(left + w * 0.09f, py), Size(w * 0.09f, h * 0.045f))
        drawRect(Color(0xFF121216).copy(alpha = alpha), Offset(left + w * 0.82f, py), Size(w * 0.09f, h * 0.045f))
      }
      // Gold collar zipper line & pull
      drawLine(
        color = goldTrim,
        start = Offset(cx, top + h * 0.48f),
        end = Offset(cx, top + h * 0.98f),
        strokeWidth = w * 0.05f,
      )
      drawCircle(goldTrim, w * 0.055f, Offset(cx, top + h * 0.53f))
      // Gold Medusa chest medallion on right chest
      drawCircle(goldTrim, w * 0.075f, Offset(cx + w * 0.18f, top + h * 0.72f))
      drawCircle(goldDark, w * 0.04f, Offset(cx + w * 0.18f, top + h * 0.72f))

      // Neck + Face
      drawRect(
        color = Color(0xFFEAB89E).copy(alpha = alpha),
        topLeft = Offset(cx - w * 0.14f, top + h * 0.39f),
        size = Size(w * 0.28f, h * 0.12f),
      )
      drawRoundRect(
        color = Color(0xFFF3C3AA).copy(alpha = alpha),
        topLeft = Offset(left + w * 0.16f, top + h * 0.10f),
        size = Size(w * 0.68f, h * 0.37f),
        cornerRadius = CornerRadius(w * 0.18f, w * 0.18f),
      )

      // Backwards black baseball cap (dome + strap cutout on forehead + brim pointing behind)
      val capBlack = Color(0xFF18181B).copy(alpha = alpha)
      val brimX = if (facingRight) left + w * 0.03f else left + w * 0.73f
      drawRoundRect(
        color = capBlack,
        topLeft = Offset(brimX, top + h * 0.14f),
        size = Size(w * 0.24f, h * 0.09f),
        cornerRadius = CornerRadius(w * 0.04f, w * 0.04f),
      )
      drawOval(
        color = capBlack,
        topLeft = Offset(left + w * 0.14f, top - h * 0.02f),
        size = Size(w * 0.72f, h * 0.24f),
      )
      // Backwards cap arch & strap over forehead + brown hair tuft
      drawRoundRect(
        color = Color(0xFF27272A).copy(alpha = alpha),
        topLeft = Offset(cx - w * 0.18f, top + h * 0.06f),
        size = Size(w * 0.36f, h * 0.06f),
        cornerRadius = CornerRadius(2f, 2f),
      )
      drawRect(
        color = Color(0xFF4B3222).copy(alpha = alpha),
        topLeft = Offset(cx - w * 0.08f, top + h * 0.10f),
        size = Size(w * 0.16f, h * 0.06f),
      )

      // Serious deadpan eyes + straight mouth matching photo
      val eyeY = top + h * 0.26f
      drawCircle(Color.White.copy(alpha = alpha), w * 0.075f, Offset(cx - w * 0.14f + lookShift, eyeY))
      drawCircle(Color.White.copy(alpha = alpha), w * 0.075f, Offset(cx + w * 0.14f + lookShift, eyeY))
      drawCircle(Color(0xFF334155).copy(alpha = alpha), w * 0.04f, Offset(cx - w * 0.13f + lookShift * 1.15f, eyeY))
      drawCircle(Color(0xFF334155).copy(alpha = alpha), w * 0.04f, Offset(cx + w * 0.15f + lookShift * 1.15f, eyeY))
      // Nose shadow & straight neutral lips
      drawCircle(Color(0xFFDC967E).copy(alpha = alpha), w * 0.045f, Offset(cx + lookShift * 0.9f, top + h * 0.33f))
      drawLine(
        color = Color(0xFF9F4C4C).copy(alpha = alpha),
        start = Offset(cx - w * 0.09f + lookShift, top + h * 0.405f),
        end = Offset(cx + w * 0.09f + lookShift, top + h * 0.405f),
        strokeWidth = w * 0.045f,
      )
    }

    PlayerSkin.MAMA_PTITSA -> {
      // Photo 3: Fluffy round cream-yellow chick with thin orange bird legs & pink human meme face blended on front (NO beak!)
      // Two thin orange chick legs + feet at bottom
      val legOrange = Color(0xFFEA7B2A).copy(alpha = alpha)
      drawLine(legOrange, Offset(cx - w * 0.16f, top + h * 0.80f), Offset(cx - w * 0.18f, top + h * 0.97f), strokeWidth = w * 0.06f)
      drawLine(legOrange, Offset(cx + w * 0.14f, top + h * 0.80f), Offset(cx + w * 0.16f, top + h * 0.97f), strokeWidth = w * 0.06f)
      drawLine(legOrange, Offset(cx - w * 0.28f, top + h * 0.97f), Offset(cx - w * 0.08f, top + h * 0.97f), strokeWidth = w * 0.055f)
      drawLine(legOrange, Offset(cx + w * 0.06f, top + h * 0.97f), Offset(cx + w * 0.26f, top + h * 0.97f), strokeWidth = w * 0.055f)

      // Fluffy cream/golden chick body silhouette
      drawOval(
        color = Color(0xFFF3E2BA).copy(alpha = alpha),
        topLeft = Offset(left + w * 0.10f, top + h * 0.04f),
        size = Size(w * 0.80f, h * 0.82f),
      )
      // Warm yellow chest fluff & folded wing
      drawOval(
        color = Color(0xFFE7B855).copy(alpha = 0.65f * alpha),
        topLeft = Offset(left + w * 0.24f, top + h * 0.48f),
        size = Size(w * 0.50f, h * 0.34f),
      )
      val wingX = if (facingRight) left + w * 0.08f else left + w * 0.66f
      drawOval(
        color = Color(0xFFD9C396).copy(alpha = alpha),
        topLeft = Offset(wingX, top + h * 0.42f),
        size = Size(w * 0.26f, h * 0.34f),
      )
      drawOval(
        color = Color(0xFF9C8456).copy(alpha = 0.7f * alpha),
        topLeft = Offset(left + w * 0.10f, top + h * 0.04f),
        size = Size(w * 0.80f, h * 0.82f),
        style = Stroke(w * 0.04f),
      )

      // Blended pinkish-reddish human meme face on chick's head
      val faceCx = cx + lookShift * 0.6f
      val faceCy = top + h * 0.30f
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(
            Color(0xFFF27D78).copy(alpha = alpha),
            Color(0xFFE89B82).copy(alpha = 0.85f * alpha),
            Color.Transparent,
          ),
          center = Offset(faceCx, faceCy),
          radius = w * 0.30f,
        ),
        radius = w * 0.30f,
        center = Offset(faceCx, faceCy),
      )
      // Side-glancing human meme eyes + dark brows
      val eyeY = top + h * 0.25f
      drawLine(Color(0xFF4A2522).copy(alpha = alpha), Offset(faceCx - w * 0.19f, eyeY - h * 0.055f), Offset(faceCx - w * 0.07f, eyeY - h * 0.055f), strokeWidth = w * 0.032f)
      drawLine(Color(0xFF4A2522).copy(alpha = alpha), Offset(faceCx + w * 0.07f, eyeY - h * 0.055f), Offset(faceCx + w * 0.19f, eyeY - h * 0.055f), strokeWidth = w * 0.032f)
      drawCircle(Color.White.copy(alpha = alpha), w * 0.065f, Offset(faceCx - w * 0.13f, eyeY))
      drawCircle(Color.White.copy(alpha = alpha), w * 0.065f, Offset(faceCx + w * 0.13f, eyeY))
      drawCircle(Color(0xFF1F1618).copy(alpha = alpha), w * 0.035f, Offset(faceCx - w * 0.145f, eyeY))
      drawCircle(Color(0xFF1F1618).copy(alpha = alpha), w * 0.035f, Offset(faceCx + w * 0.115f, eyeY))
      // Human nose shading & straight small red mouth
      drawCircle(Color(0xFFD9645F).copy(alpha = alpha), w * 0.045f, Offset(faceCx, top + h * 0.33f))
      drawLine(
        color = Color(0xFFB3383B).copy(alpha = alpha),
        start = Offset(faceCx - w * 0.09f, top + h * 0.415f),
        end = Offset(faceCx + w * 0.09f, top + h * 0.415f),
        strokeWidth = w * 0.045f,
      )
    }

    PlayerSkin.ADMIN -> {
      val neonGreen = Color(0xFF00FFA3).copy(alpha = alpha)
      val adminGold = Color(0xFFFFD700).copy(alpha = alpha)
      val obsidian = Color(0xFF0B1120).copy(alpha = alpha)

      // Cyber aura behind body
      drawCircle(
        color = neonGreen.copy(alpha = 0.20f * alpha),
        radius = w * 0.56f,
        center = Offset(cx, top + h * 0.54f),
      )

      // Dark obsidian cloak/armor torso
      drawRoundRect(
        color = obsidian,
        topLeft = Offset(left + w * 0.06f, top + h * 0.44f),
        size = Size(w * 0.88f, h * 0.54f),
        cornerRadius = CornerRadius(w * 0.20f, w * 0.20f),
      )
      drawRoundRect(
        color = neonGreen,
        topLeft = Offset(left + w * 0.06f, top + h * 0.44f),
        size = Size(w * 0.88f, h * 0.54f),
        cornerRadius = CornerRadius(w * 0.20f, w * 0.20f),
        style = Stroke(w * 0.045f),
      )
      // Glowing Admin chest diamond emblem
      val chestPath = Path().apply {
        moveTo(cx, top + h * 0.56f)
        lineTo(cx + w * 0.14f, top + h * 0.70f)
        lineTo(cx, top + h * 0.84f)
        lineTo(cx - w * 0.14f, top + h * 0.70f)
        close()
      }
      drawPath(chestPath, neonGreen)

      // Hooded Cyber Head
      drawRoundRect(
        color = Color(0xFF1E293B).copy(alpha = alpha),
        topLeft = Offset(left + w * 0.14f, top + h * 0.14f),
        size = Size(w * 0.72f, h * 0.38f),
        cornerRadius = CornerRadius(w * 0.18f, w * 0.18f),
      )
      drawRoundRect(
        color = obsidian,
        topLeft = Offset(left + w * 0.20f, top + h * 0.18f),
        size = Size(w * 0.60f, h * 0.30f),
        cornerRadius = CornerRadius(w * 0.12f, w * 0.12f),
      )

      // Glowing matrix cyber-visor
      drawRoundRect(
        color = neonGreen,
        topLeft = Offset(left + w * 0.22f + lookShift * 0.5f, top + h * 0.27f),
        size = Size(w * 0.56f, h * 0.12f),
        cornerRadius = CornerRadius(w * 0.05f, w * 0.05f),
      )
      drawLine(
        color = Color.White.copy(alpha = 0.85f * alpha),
        start = Offset(left + w * 0.27f + lookShift * 0.5f, top + h * 0.31f),
        end = Offset(left + w * 0.62f + lookShift * 0.5f, top + h * 0.31f),
        strokeWidth = w * 0.03f,
      )

      // Floating Royal Admin Gold Crown on top
      val crownPath = Path().apply {
        moveTo(left + w * 0.20f, top + h * 0.15f)
        lineTo(left + w * 0.23f, top - h * 0.02f)
        lineTo(cx - w * 0.12f, top + h * 0.08f)
        lineTo(cx, top - h * 0.06f)
        lineTo(cx + w * 0.12f, top + h * 0.08f)
        lineTo(left + w * 0.77f, top - h * 0.02f)
        lineTo(left + w * 0.80f, top + h * 0.15f)
        close()
      }
      drawPath(crownPath, adminGold)
      drawPath(crownPath, Color(0xFF92400E).copy(alpha = alpha), style = Stroke(w * 0.03f))
      drawCircle(neonGreen, w * 0.045f, Offset(cx, top + h * 0.08f))
    }
  }
}

private fun DrawScope.drawProjectiles(list: List<Projectile>) {
  for (pr in list) {
    val glowColor = when {
      pr.isRemoteCoopAlly -> Color(0xFF00FFA3)
      pr.isRemotePlayerShot -> Color(0xFFFF9F1C)
      pr.isPlayer -> TearCyan
      else -> MeatCrimson
    }
    drawCircle(glowColor.copy(alpha = 0.32f), pr.radius * 1.65f, Offset(pr.x, pr.y))
    drawCircle(glowColor, pr.radius, Offset(pr.x, pr.y))
    drawCircle(Color.White, pr.radius * 0.42f, Offset(pr.x - pr.radius * 0.25f, pr.y - pr.radius * 0.25f))
  }
}

private fun DrawScope.drawParticles(list: List<Particle>) {
  for (pt in list) {
    val a = (pt.life / pt.maxLife).coerceIn(0f, 1f)
    drawCircle(pt.color.copy(alpha = a), pt.size * a, Offset(pt.x, pt.y))
  }
}

private fun DrawScope.drawStormWalls(room: DungeonRoom, tick: Long) {
  if (room.stormInset <= 1f) return
  val inset = room.stormInset
  val pulse = 0.38f + 0.1f * sin(tick * 0.15f)
  drawRect(
    brush = Brush.horizontalGradient(listOf(StormViolet.copy(alpha = 0.72f), StormViolet.copy(alpha = pulse))),
    topLeft = Offset.Zero,
    size = Size(inset, WorldConst.ROOM_HEIGHT),
  )
  drawRect(
    brush = Brush.horizontalGradient(listOf(StormViolet.copy(alpha = pulse), StormViolet.copy(alpha = 0.72f))),
    topLeft = Offset(WorldConst.ROOM_WIDTH - inset, 0f),
    size = Size(inset, WorldConst.ROOM_HEIGHT),
  )
  drawLine(StormGlow, Offset(inset, 0f), Offset(inset, WorldConst.ROOM_HEIGHT), strokeWidth = 3f)
  drawLine(StormGlow, Offset(WorldConst.ROOM_WIDTH - inset, 0f), Offset(WorldConst.ROOM_WIDTH - inset, WorldConst.ROOM_HEIGHT), strokeWidth = 3f)
}
