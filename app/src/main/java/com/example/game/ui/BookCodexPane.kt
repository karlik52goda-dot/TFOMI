package com.example.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ControlsLayoutConfig
import com.example.game.model.GameHudSnapshot
import com.example.game.model.Vec2
import com.example.ui.theme.*

@Composable
fun BookSpreadLayout(
  hud: GameHudSnapshot,
  arenaContent: @Composable BoxScope.() -> Unit,
  onMoveXChange: (Float) -> Unit,
  onJumpPress: () -> Unit,
  onJumpRelease: () -> Unit,
  onShootChange: (Boolean, Vec2) -> Unit,
  onDashPress: () -> Unit,
  onBuildFloor: () -> Unit,
  onBuildWall: () -> Unit,
  layoutConfig: ControlsLayoutConfig = ControlsLayoutConfig(),
  modifier: Modifier = Modifier,
) {
  val canBuild = hud.mats >= hud.buildCost
  val mirror = layoutConfig.swapSides
  val swapJS = layoutConfig.swapJumpShoot

  val moveAndBuildDeck: @Composable () -> Unit = {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(DungeonStone)
        .border(1.dp, DungeonBorder)
        .padding(12.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      val moveSlider: @Composable () -> Unit = {
        MoveSliderPad(
          onMoveXChange = onMoveXChange,
          modifier = Modifier
            .weight(1f)
            .height(92.dp),
        )
      }
      val buildBtns: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          TapActionButton(
            label = "${stringResource(R.string.ctrl_floor)} ${hud.buildCost}",
            icon = Icons.Filled.Layers,
            accent = if (canBuild) BuildWood else LootCommon,
            enabled = canBuild,
            tag = "build_floor_button",
            horizontal = true,
            onPress = onBuildFloor,
            onRelease = {},
            modifier = Modifier.size(width = 108.dp, height = 48.dp),
          )
          TapActionButton(
            label = "${stringResource(R.string.ctrl_wall)} ${hud.buildCost}",
            icon = Icons.Filled.Construction,
            accent = if (canBuild) BuildWood else LootCommon,
            enabled = canBuild,
            tag = "build_wall_button",
            horizontal = true,
            onPress = onBuildWall,
            onRelease = {},
            modifier = Modifier.size(width = 108.dp, height = 48.dp),
          )
        }
      }
      if (mirror) {
        buildBtns(); moveSlider()
      } else {
        moveSlider(); buildBtns()
      }
    }
  }

  val combatDeck: @Composable () -> Unit = {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(104.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      val dashBtn: @Composable () -> Unit = {
        TapActionButton(
          label = stringResource(R.string.ctrl_dash),
          icon = Icons.Filled.Bolt,
          accent = if (hud.dashReadyFraction >= 0.99f) StormViolet else LootCommon,
          enabled = hud.dashReadyFraction >= 0.99f,
          tag = "dash_button",
          onPress = onDashPress,
          onRelease = {},
          modifier = Modifier
            .weight(0.8f)
            .fillMaxHeight(),
        )
      }
      val shootBtn: @Composable () -> Unit = {
        ShootAimPad(
          onShootChange = onShootChange,
          modifier = Modifier
            .size(92.dp)
            .testTag("shoot_pad"),
        )
      }
      val jumpBtn: @Composable () -> Unit = {
        TapActionButton(
          label = stringResource(R.string.ctrl_jump),
          icon = Icons.Filled.ArrowUpward,
          accent = MeatCrimson,
          enabled = true,
          tag = "jump_button",
          onPress = onJumpPress,
          onRelease = onJumpRelease,
          modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
        )
      }

      if (mirror) {
        if (swapJS) { shootBtn(); jumpBtn() } else { jumpBtn(); shootBtn() }
        dashBtn()
      } else {
        dashBtn()
        if (swapJS) { jumpBtn(); shootBtn() } else { shootBtn(); jumpBtn() }
      }
    }
  }

  Row(
    modifier = modifier
      .fillMaxSize()
      .background(StormVoid)
      .windowInsetsPadding(WindowInsets.safeDrawing),
  ) {
    // Left Page: Arena + Left-Thumb Controls (Move + Build)
    Column(
      modifier = Modifier
        .weight(1.15f)
        .fillMaxHeight(),
    ) {
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth(),
        content = arenaContent,
      )

      if (mirror) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .background(DungeonStone)
            .border(1.dp, DungeonBorder)
            .padding(12.dp),
        ) {
          combatDeck()
        }
      } else {
        moveAndBuildDeck()
      }
    }

    // Center Book Spine Gutter (keeps UI clear of foldable hinge)
    Box(
      modifier = Modifier
        .width(12.dp)
        .fillMaxHeight()
        .background(StormVoid)
        .border(1.dp, DungeonBorder),
    )

    // Right Page: Book Codex + Right-Thumb Combat Controls
    Column(
      modifier = Modifier
        .weight(0.85f)
        .fillMaxHeight()
        .background(DungeonStone)
        .padding(14.dp),
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(
        modifier = Modifier
          .weight(1f)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          text = stringResource(R.string.label_book_codex),
          style = MaterialTheme.typography.titleLarge,
          color = TearCyan,
        )

        // Expanded Minimap
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DungeonSurface)
            .border(1.dp, DungeonBorder, RoundedCornerShape(16.dp))
            .padding(12.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          IsaacMinimap(rooms = hud.minimapRooms, modifier = Modifier.size(86.dp))
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
              text = stringResource(R.string.label_floor_fmt, hud.floor),
              style = MaterialTheme.typography.titleMedium,
              color = TextPrimary,
            )
            Text(
              text = "Счёт: ${hud.score}",
              style = MaterialTheme.typography.bodyMedium,
              color = LootLegendary,
            )
            Text(
              text = "Убито: ${hud.kills}",
              style = MaterialTheme.typography.bodyMedium,
              color = TextSecondary,
            )
          }
        }

        // Active Weapon Card
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DungeonSurface)
            .border(1.5.dp, hud.weapon.rarity.color, RoundedCornerShape(16.dp))
            .padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Text(
            text = "${hud.weapon.rarity.labelRu}: ${hud.weapon.type.titleRu}",
            style = MaterialTheme.typography.titleMedium,
            color = hud.weapon.rarity.color,
          )
          Text(
            text = "Урон: ${hud.weapon.damage.toInt()} • Материалы: ${hud.mats}",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
          )
        }

        // Relics Codex
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DungeonSurface)
            .border(1.dp, DungeonBorder, RoundedCornerShape(16.dp))
            .padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text("РЕЛИКВИИ (${hud.relics.size})", style = MaterialTheme.typography.labelLarge, color = StormGlow)
          if (hud.relics.isEmpty()) {
            Text(
              text = stringResource(R.string.label_no_relics),
              style = MaterialTheme.typography.bodyMedium,
              color = TextSecondary,
            )
          } else {
            hud.relics.forEach { relic ->
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Box(
                  modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(relic.badgeColor),
                )
                Column {
                  Text(relic.titleRu, style = MaterialTheme.typography.labelSmall, color = relic.badgeColor)
                  Text(relic.descRu, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
              }
            }
          }
        }
      }

      Spacer(Modifier.height(10.dp))

      if (mirror) {
        moveAndBuildDeck()
      } else {
        combatDeck()
      }
    }
  }
}
