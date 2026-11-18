package com.example.game.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storm
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.game.model.GameHudSnapshot
import com.example.game.model.MinimapNode
import com.example.game.model.RoomType
import com.example.ui.theme.*

@Composable
fun GameHud(
  hud: GameHudSnapshot,
  onPauseClick: () -> Unit,
  onToggleAutoAim: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  BoxWithConstraints(
    modifier = modifier
      .fillMaxWidth()
      .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
      .padding(horizontal = 10.dp, vertical = 6.dp)
  ) {
    val isCompactPortrait = maxWidth < 520.dp

    Column(modifier = Modifier.fillMaxWidth()) {
      if (isCompactPortrait) {
        // Portrait Book Mode: clean 2-row non-overlapping HUD
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          HeartsAndShieldsPill(hud = hud, iconSize = 14.dp)

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
          ) {
            FloorScorePill(hud = hud, compact = true)
            AutoAimChip(enabled = hud.autoAimEnabled, onClick = onToggleAutoAim)
            PauseIconBtn(onPauseClick = onPauseClick)
          }
        }

        Spacer(Modifier.height(6.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.Top,
        ) {
          Column(
            verticalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.weight(1f).padding(end = 8.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              MatsPill(hud.mats)
              WeaponPill(hud)
              if (!hud.roomCleared) {
                StormPill(hud, compact = true)
              }
            }
            if (hud.relics.isNotEmpty()) {
              RelicDotsRow(hud)
            }
            if (hud.multiplayerModeLabel != null) {
              MultiplayerHudPill(hud)
            }
          }

          IsaacMinimap(rooms = hud.minimapRooms, modifier = Modifier.size(56.dp))
        }
      } else {
        // Wide / Landscape HUD
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.Top,
        ) {
          Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f),
          ) {
            HeartsAndShieldsPill(hud = hud, iconSize = 16.dp)
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              MatsPill(hud.mats)
              WeaponPill(hud)
            }
            if (hud.relics.isNotEmpty()) {
              RelicDotsRow(hud)
            }
            if (hud.multiplayerModeLabel != null) {
              MultiplayerHudPill(hud)
            }
          }

          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp),
          ) {
            FloorScorePill(hud = hud, compact = false)
            if (!hud.roomCleared) {
              StormPill(hud, compact = false)
            }
          }

          Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            AutoAimChip(enabled = hud.autoAimEnabled, onClick = onToggleAutoAim)
            IsaacMinimap(rooms = hud.minimapRooms, modifier = Modifier.size(64.dp))
            PauseIconBtn(onPauseClick = onPauseClick)
          }
        }
      }

      // Boss Health Bar
      hud.bossHpRatio?.let { ratio ->
        Spacer(Modifier.height(6.dp))
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = hud.bossTitle ?: "БОСС БУРИ",
            style = MaterialTheme.typography.labelSmall,
            color = MeatCrimson,
          )
          Box(
            modifier = Modifier
              .fillMaxWidth(0.7f)
              .height(10.dp)
              .clip(RoundedCornerShape(5.dp))
              .background(StormVoid.copy(alpha = 0.85f))
              .border(1.dp, MeatCrimson, RoundedCornerShape(5.dp)),
          ) {
            Box(
              modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(ratio)
                .background(MeatCrimson),
            )
          }
        }
      }

      // Loot / Relic Toast
      Spacer(Modifier.height(6.dp))
      AnimatedVisibility(
        visible = hud.toast != null,
        enter = fadeIn() + slideInVertically { -it / 2 },
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.CenterHorizontally),
      ) {
        hud.toast?.let { toast ->
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
              .clip(RoundedCornerShape(12.dp))
              .background(StormVoid.copy(alpha = 0.9f))
              .border(1.5.dp, toast.accentColor, RoundedCornerShape(12.dp))
              .padding(horizontal = 14.dp, vertical = 6.dp),
          ) {
            Text(
              text = toast.title,
              style = MaterialTheme.typography.labelLarge,
              color = toast.accentColor,
            )
            Text(
              text = toast.subtitle,
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
              color = TextPrimary,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun HeartsAndShieldsPill(hud: GameHudSnapshot, iconSize: androidx.compose.ui.unit.Dp) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(3.dp),
    modifier = Modifier
      .clip(RoundedCornerShape(12.dp))
      .background(StormVoid.copy(alpha = 0.8f))
      .border(1.dp, DungeonBorder, RoundedCornerShape(12.dp))
      .padding(horizontal = 8.dp, vertical = 5.dp),
  ) {
    val fullHearts = (hud.maxHp + 1) / 2
    for (i in 0 until fullHearts) {
      val filled = hud.hp >= (i + 1) * 2
      val half = !filled && hud.hp == i * 2 + 1
      Icon(
        imageVector = Icons.Filled.Favorite,
        contentDescription = null,
        tint = when {
          filled -> MeatCrimson
          half -> MeatCrimson.copy(alpha = 0.55f)
          else -> Color.White.copy(alpha = 0.18f)
        },
        modifier = Modifier.size(iconSize),
      )
    }
    if (hud.shield > 0) {
      Spacer(Modifier.width(3.dp))
      val shields = (hud.shield + 1) / 2
      for (s in 0 until shields) {
        Icon(
          imageVector = Icons.Filled.Shield,
          contentDescription = null,
          tint = ShieldBlue,
          modifier = Modifier.size(iconSize - 1.dp),
        )
      }
    }
  }
}

@Composable
private fun MatsPill(mats: Int) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    modifier = Modifier
      .clip(RoundedCornerShape(10.dp))
      .background(StormVoid.copy(alpha = 0.8f))
      .border(1.dp, BuildWood.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
      .padding(horizontal = 7.dp, vertical = 4.dp),
  ) {
    Icon(Icons.Filled.Build, contentDescription = null, tint = BuildWood, modifier = Modifier.size(13.dp))
    Text(
      text = "$mats",
      style = MaterialTheme.typography.labelSmall,
      color = BuildWood,
    )
  }
}

@Composable
private fun WeaponPill(hud: GameHudSnapshot) {
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(10.dp))
      .background(StormVoid.copy(alpha = 0.82f))
      .border(1.5.dp, hud.weapon.rarity.color, RoundedCornerShape(10.dp))
      .padding(horizontal = 8.dp, vertical = 4.dp),
  ) {
    Text(
      text = hud.weapon.type.titleRu,
      style = MaterialTheme.typography.labelSmall,
      color = hud.weapon.rarity.color,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun RelicDotsRow(hud: GameHudSnapshot) {
  Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    hud.relics.take(7).forEach { relic ->
      Box(
        modifier = Modifier
          .size(11.dp)
          .clip(CircleShape)
          .background(relic.badgeColor)
          .border(1.dp, Color.White, CircleShape)
      )
    }
  }
}

@Composable
private fun MultiplayerHudPill(hud: GameHudSnapshot) {
  val modeLabel = hud.multiplayerModeLabel ?: return
  val isPvp = modeLabel.contains("PvP")
  val accent = if (isPvp) MeatCrimson else Color(0xFF00FFA3)
  val rivalText = when {
    hud.remotePlayerName == null -> "$modeLabel • Ожидание"
    !hud.remotePlayerAlive -> "$modeLabel • ${hud.remotePlayerName}: ВЫБЫЛ"
    else -> "$modeLabel • ${hud.remotePlayerName} (${hud.remotePlayerHp}/${hud.remotePlayerMaxHp})"
  }
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(10.dp))
      .background(StormVoid.copy(alpha = 0.85f))
      .border(1.dp, accent, RoundedCornerShape(10.dp))
      .padding(horizontal = 8.dp, vertical = 3.dp),
  ) {
    Text(
      text = rivalText,
      style = MaterialTheme.typography.labelSmall,
      color = accent,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun FloorScorePill(hud: GameHudSnapshot, compact: Boolean) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(5.dp),
    modifier = Modifier
      .clip(RoundedCornerShape(12.dp))
      .background(StormVoid.copy(alpha = 0.82f))
      .border(1.dp, DungeonBorder, RoundedCornerShape(12.dp))
      .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = 4.dp),
  ) {
    Text(
      text = if (compact) {
        stringResource(R.string.label_floor_short_fmt, hud.floor)
      } else {
        stringResource(R.string.label_floor_fmt, hud.floor)
      },
      style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
      color = TextPrimary,
    )
    Text(
      text = "• ${hud.score}",
      style = MaterialTheme.typography.labelSmall,
      color = LootLegendary,
    )
    if (hud.coinsEarned > 0) {
      Icon(
        imageVector = Icons.Filled.MonetizationOn,
        contentDescription = null,
        tint = LootLegendary,
        modifier = Modifier.size(12.dp),
      )
      Text(
        text = "+${hud.coinsEarned}",
        style = MaterialTheme.typography.labelSmall,
        color = LootLegendary,
      )
    }
  }
}

@Composable
private fun StormPill(hud: GameHudSnapshot, compact: Boolean) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(3.dp),
    modifier = Modifier
      .clip(RoundedCornerShape(10.dp))
      .background(if (hud.stormActive) StormViolet.copy(alpha = 0.88f) else StormVoid.copy(alpha = 0.8f))
      .border(1.dp, StormGlow, RoundedCornerShape(10.dp))
      .padding(horizontal = 7.dp, vertical = 3.dp),
  ) {
    Icon(Icons.Filled.Storm, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
    Text(
      text = if (hud.stormActive) {
        stringResource(R.string.label_storm_active)
      } else if (compact) {
        "${hud.stormSecondsLeft}с"
      } else {
        stringResource(R.string.label_storm_incoming, hud.stormSecondsLeft)
      },
      style = MaterialTheme.typography.labelSmall,
      color = Color.White,
      maxLines = 1,
    )
  }
}

@Composable
private fun AutoAimChip(
  enabled: Boolean,
  onClick: () -> Unit,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(3.dp),
    modifier = Modifier
      .heightIn(min = 48.dp)
      .testTag("hud_auto_aim_toggle")
      .clip(RoundedCornerShape(12.dp))
      .background(if (enabled) TearCyan.copy(alpha = 0.22f) else StormVoid.copy(alpha = 0.8f))
      .border(1.2.dp, if (enabled) TearCyan else DungeonBorder, RoundedCornerShape(12.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 8.dp, vertical = 6.dp),
  ) {
    Icon(
      imageVector = Icons.Filled.MyLocation,
      contentDescription = stringResource(R.string.label_auto_aim),
      tint = if (enabled) TearCyan else TextSecondary,
      modifier = Modifier.size(14.dp),
    )
    Text(
      text = stringResource(R.string.label_auto_short),
      style = MaterialTheme.typography.labelSmall,
      color = if (enabled) TearCyan else TextSecondary,
    )
  }
}

@Composable
private fun PauseIconBtn(onPauseClick: () -> Unit) {
  IconButton(
    onClick = onPauseClick,
    modifier = Modifier
      .size(48.dp)
      .testTag("pause_button")
      .clip(RoundedCornerShape(12.dp))
      .background(StormVoid.copy(alpha = 0.84f))
      .border(1.dp, DungeonBorder, RoundedCornerShape(12.dp)),
  ) {
    Icon(
      imageVector = Icons.Filled.Pause,
      contentDescription = stringResource(R.string.cd_pause),
      tint = TextPrimary,
    )
  }
}

@Composable
fun IsaacMinimap(
  rooms: List<MinimapNode>,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(10.dp))
      .background(StormVoid.copy(alpha = 0.84f))
      .border(1.dp, DungeonBorder, RoundedCornerShape(10.dp))
      .padding(4.dp),
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val cellW = size.width / 5f
      val cellH = size.height / 5f
      val coordSet = rooms.map { it.gx to it.gy }.toSet()

      // Draw connecting corridor bridges first
      for (node in rooms) {
        val cx = (node.gx + 2.5f) * cellW
        val cy = (node.gy + 2.5f) * cellH
        if ((node.gx + 1 to node.gy) in coordSet) {
          drawLine(Color(0xFF6B588C), Offset(cx, cy), Offset(cx + cellW, cy), strokeWidth = 2.5f)
        }
        if ((node.gx to node.gy + 1) in coordSet) {
          drawLine(Color(0xFF6B588C), Offset(cx, cy), Offset(cx, cy + cellH), strokeWidth = 2.5f)
        }
      }

      for (node in rooms) {
        val cx = (node.gx + 2) * cellW
        val cy = (node.gy + 2) * cellH
        if (cx < 0f || cy < 0f || cx >= size.width || cy >= size.height) continue
        val color = when {
          node.isCurrent -> TearCyan
          node.type == RoomType.BOSS -> MeatCrimson
          node.type == RoomType.TREASURE -> LootLegendary
          node.visited -> Color(0xFF8C78AD)
          else -> Color(0xFF42305E)
        }
        drawRect(
          color = color,
          topLeft = Offset(cx + 2f, cy + 2f),
          size = Size(cellW - 4f, cellH - 4f),
        )
        if (node.hasRemotePlayer) {
          drawCircle(
            color = Color(0xFF00FFA3),
            radius = (cellW * 0.22f).coerceAtLeast(2.5f),
            center = Offset(cx + cellW * 0.5f, cy + cellH * 0.5f),
          )
        }
      }
    }
  }
}
