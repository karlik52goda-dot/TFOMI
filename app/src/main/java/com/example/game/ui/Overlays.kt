package com.example.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ScreenMode
import com.example.game.model.GameHudSnapshot
import com.example.ui.theme.*

@Composable
fun PauseOverlay(
  soundEnabled: Boolean,
  hapticsEnabled: Boolean,
  screenMode: ScreenMode,
  onSelectScreenMode: (ScreenMode) -> Unit,
  onResume: () -> Unit,
  onRestart: () -> Unit,
  onToggleSound: () -> Unit,
  onToggleHaptics: () -> Unit,
  onOpenControlsEditor: () -> Unit = {},
  onMainMenu: () -> Unit,
) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(StormVoid.copy(alpha = 0.82f)),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier = Modifier
        .widthIn(max = 360.dp)
        .fillMaxWidth(0.9f)
        .clip(RoundedCornerShape(22.dp))
        .background(DungeonStone)
        .border(2.dp, StormViolet, RoundedCornerShape(22.dp))
        .padding(20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = stringResource(R.string.label_paused),
        style = MaterialTheme.typography.headlineMedium,
        color = TextPrimary,
      )

      Button(
        onClick = onResume,
        colors = ButtonDefaults.buttonColors(containerColor = MeatCrimson),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(50.dp)
          .testTag("resume_button"),
      ) {
        Text(stringResource(R.string.btn_resume), style = MaterialTheme.typography.labelLarge, color = TextPrimary)
      }

      ScreenModeSelectorRow(
        selected = screenMode,
        onSelect = onSelectScreenMode,
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedButton(
          onClick = onToggleSound,
          modifier = Modifier.weight(1f).height(48.dp),
        ) {
          Text(
            text = "${stringResource(R.string.label_sound)}: ${if (soundEnabled) "ВКЛ" else "ВЫКЛ"}",
            style = MaterialTheme.typography.labelSmall,
            color = if (soundEnabled) TearCyan else TextSecondary,
          )
        }
        OutlinedButton(
          onClick = onToggleHaptics,
          modifier = Modifier.weight(1f).height(48.dp),
        ) {
          Text(
            text = "${stringResource(R.string.label_haptics)}: ${if (hapticsEnabled) "ВКЛ" else "ВЫКЛ"}",
            style = MaterialTheme.typography.labelSmall,
            color = if (hapticsEnabled) TearCyan else TextSecondary,
          )
        }
      }

      OutlinedButton(
        onClick = onOpenControlsEditor,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("pause_controls_editor_button"),
      ) {
        Icon(Icons.Filled.OpenWith, contentDescription = null, tint = TearCyan, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.btn_edit_controls), style = MaterialTheme.typography.labelLarge, color = TearCyan)
      }

      OutlinedButton(
        onClick = onRestart,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("pause_restart_button"),
      ) {
        Text(stringResource(R.string.btn_restart), style = MaterialTheme.typography.labelLarge, color = TearCyan)
      }

      TextButton(
        onClick = onMainMenu,
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("pause_menu_button"),
      ) {
        Text(stringResource(R.string.btn_main_menu), style = MaterialTheme.typography.labelLarge, color = TextSecondary)
      }
    }
  }
}

@Composable
fun GameOverOverlay(
  hud: GameHudSnapshot,
  bestScore: Int,
  onRestart: () -> Unit,
  onMainMenu: () -> Unit,
) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(StormVoid.copy(alpha = 0.88f)),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier = Modifier
        .widthIn(max = 360.dp)
        .fillMaxWidth(0.88f)
        .clip(RoundedCornerShape(24.dp))
        .background(DungeonStone)
        .border(2.dp, MeatCrimson, RoundedCornerShape(24.dp))
        .padding(22.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Text(
        text = stringResource(R.string.label_game_over),
        style = MaterialTheme.typography.headlineMedium,
        color = MeatCrimson,
      )

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(14.dp))
          .background(DungeonSurface)
          .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
      ) {
        SummaryItem("СЧЁТ", "${hud.score}", LootLegendary)
        SummaryItem("ЭТАЖ", "${hud.floor}", StormGlow)
        SummaryItem("УБИТО", "${hud.kills}", TearCyan)
        SummaryItem("МОНЕТ", "+${hud.coinsEarned}", LootLegendary)
      }

      Text(
        text = "${stringResource(R.string.label_best_score)}: ${maxOf(bestScore, hud.score)}",
        style = MaterialTheme.typography.bodyLarge,
        color = TextSecondary,
      )

      Button(
        onClick = onRestart,
        colors = ButtonDefaults.buttonColors(containerColor = MeatCrimson),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(54.dp)
          .testTag("gameover_restart_button"),
      ) {
        Text(stringResource(R.string.btn_restart), style = MaterialTheme.typography.titleLarge, color = TextPrimary)
      }

      OutlinedButton(
        onClick = onMainMenu,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("gameover_menu_button"),
      ) {
        Text(stringResource(R.string.btn_main_menu), style = MaterialTheme.typography.labelLarge, color = TextPrimary)
      }
    }
  }
}

@Composable
private fun SummaryItem(label: String, value: String, color: Color) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(value, style = MaterialTheme.typography.titleLarge, color = color)
    Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
  }
}
