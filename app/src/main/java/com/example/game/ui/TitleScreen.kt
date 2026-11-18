package com.example.game.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.ControlId
import com.example.data.ControlsLayoutConfig
import com.example.data.PlayerStats
import com.example.data.ScreenMode
import com.example.game.model.PlayerSkin
import com.example.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private enum class LobbyTab(val titleRu: String) {
  SKINS("СКИНЫ"),
  BOSSES("БОССЫ"),
  TUTORIAL("ТУТОРИАЛ"),
  SETTINGS("НАСТРОЙКИ"),
}

@Composable
fun TitleScreen(
  stats: PlayerStats,
  onPlayClick: () -> Unit,
  onOpenMultiplayer: () -> Unit = {},
  onSelectOrBuySkin: (PlayerSkin) -> Unit,
  onUnlockSecretSkin: (PlayerSkin, String, (Boolean) -> Unit) -> Unit = { _, _, cb -> cb(false) },
  onToggleSound: () -> Unit,
  onToggleHaptics: () -> Unit,
  onSelectScreenMode: (ScreenMode) -> Unit,
  onUpdateControlsLayout: (ControlsLayoutConfig) -> Unit = {},
  onOpenControlsEditor: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  var activeTab by remember { mutableStateOf(LobbyTab.SKINS) }
  var passwordDialogSkin by remember { mutableStateOf<PlayerSkin?>(null) }

  val infinite = rememberInfiniteTransition(label = "lobby_anim")
  val pulse by infinite.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(2600, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse,
    ),
    label = "pulse",
  )
  val orbitPhase by infinite.animateFloat(
    initialValue = 0f,
    targetValue = (2f * PI).toFloat(),
    animationSpec = infiniteRepeatable(
      animation = tween(6500, easing = LinearEasing),
      repeatMode = RepeatMode.Restart,
    ),
    label = "orbit",
  )

  val heroSkin = stats.selectedSkin

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(StormVoid),
  ) {
    // Live Animated Storm & Cyber-Dungeon Canvas Backdrop
    Canvas(modifier = Modifier.fillMaxSize()) {
      drawRect(
        brush = Brush.verticalGradient(
          listOf(
            Color(0xFF140924),
            StormVoid,
            Color(0xFF1B0D2C),
          )
        ),
        size = size,
      )
      // Breathing storm & skin glow
      drawCircle(
        brush = Brush.radialGradient(
          listOf(StormViolet.copy(alpha = 0.28f + pulse * 0.12f), Color.Transparent),
          center = Offset(size.width * 0.2f, size.height * 0.18f),
          radius = size.width * 0.65f,
        ),
        center = Offset(size.width * 0.2f, size.height * 0.18f),
        radius = size.width * 0.65f,
      )
      drawCircle(
        brush = Brush.radialGradient(
          listOf(heroSkin.accentColor.copy(alpha = 0.20f + pulse * 0.10f), Color.Transparent),
          center = Offset(size.width * 0.78f, size.height * 0.34f),
          radius = size.width * 0.58f,
        ),
        center = Offset(size.width * 0.78f, size.height * 0.34f),
        radius = size.width * 0.58f,
      )
      // Subtle angled dungeon grid lines
      val gridStep = 48.dp.toPx()
      val lineColor = Color.White.copy(alpha = 0.035f)
      var gx = 0f
      while (gx < size.width) {
        drawLine(lineColor, Offset(gx, 0f), Offset(gx, size.height), strokeWidth = 1f)
        gx += gridStep
      }
      var gy = 0f
      while (gy < size.height) {
        drawLine(lineColor, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f)
        gy += gridStep
      }
      // Drifting storm embers
      for (i in 0 until 14) {
        val angle = orbitPhase + i * 0.45f
        val px = size.width * ((i * 0.13f + 0.08f) % 0.92f) + cos(angle) * 14f
        val py = size.height * ((i * 0.17f + 0.10f) % 0.85f) + sin(angle * 1.2f) * 18f
        val c = if (i % 3 == 0) TearCyan else if (i % 3 == 1) MeatCrimson else StormGlow
        drawCircle(c.copy(alpha = 0.25f + 0.2f * pulse), radius = (2.5f + (i % 3)).dp.toPx(), center = Offset(px, py))
      }
    }

    Column(
      modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .widthIn(max = 620.dp)
        .align(Alignment.Center)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // 1. Top Game Lobby Header Bar (Title Pill + Quick Audio + Coins Balance)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Box(
            modifier = Modifier
              .size(44.dp)
              .clip(RoundedCornerShape(12.dp))
              .border(1.5.dp, TearCyan.copy(alpha = 0.7f), RoundedCornerShape(12.dp)),
          ) {
            Image(
              painter = painterResource(id = R.drawable.img_custom_launcher),
              contentDescription = stringResource(R.string.cd_title_art),
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize(),
            )
          }
          Column {
            Text(
              text = "TFOMI",
              style = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp),
              color = TextPrimary,
            )
            Text(
              text = stringResource(R.string.title_subtitle),
              style = MaterialTheme.typography.labelSmall,
              color = TearCyan,
            )
          }
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          IconButton(
            onClick = onToggleSound,
            modifier = Modifier
              .size(48.dp)
              .testTag("sound_toggle")
              .clip(RoundedCornerShape(14.dp))
              .background(DungeonSurface.copy(alpha = 0.85f))
              .border(1.dp, if (stats.soundEnabled) TearCyan.copy(alpha = 0.6f) else DungeonBorder, RoundedCornerShape(14.dp)),
          ) {
            Icon(
              imageVector = if (stats.soundEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
              contentDescription = stringResource(R.string.label_sound),
              tint = if (stats.soundEnabled) TearCyan else TextSecondary,
            )
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier
              .heightIn(min = 48.dp)
              .clip(RoundedCornerShape(14.dp))
              .background(Brush.horizontalGradient(listOf(Color(0xFF2D1E08), DungeonSurface)))
              .border(1.5.dp, LootLegendary, RoundedCornerShape(14.dp))
              .padding(horizontal = 12.dp, vertical = 8.dp),
          ) {
            Icon(Icons.Filled.MonetizationOn, contentDescription = null, tint = LootLegendary, modifier = Modifier.size(18.dp))
            Text(
              text = "${stats.coins}",
              style = MaterialTheme.typography.titleMedium,
              color = LootLegendary,
            )
          }
        }
      }

      // 2. Hero Showcase Arena Stage (Banner Art + Animated Podium + Live Hero Skin)
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(214.dp)
          .clip(RoundedCornerShape(24.dp))
          .border(2.dp, heroSkin.accentColor.copy(alpha = 0.7f), RoundedCornerShape(24.dp)),
      ) {
        Image(
          painter = painterResource(id = R.drawable.img_title_banner),
          contentDescription = stringResource(R.string.cd_title_art),
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(
              Brush.verticalGradient(
                listOf(
                  StormVoid.copy(alpha = 0.30f),
                  StormVoid.copy(alpha = 0.65f),
                  StormVoid.copy(alpha = 0.96f),
                )
              )
            ),
        )

        // Mashup Franchise Tags (Top Left)
        Row(
          modifier = Modifier
            .align(Alignment.TopStart)
            .padding(12.dp),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          MiniTagChip("MEAT BOY", MeatCrimson)
          MiniTagChip("FORTNITE", BuildWood)
          MiniTagChip("ISAAC", TearCyan)
        }

        // Best Score Chip (Top Right)
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(12.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(StormVoid.copy(alpha = 0.85f))
            .border(1.dp, StormViolet.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
          Icon(Icons.Filled.Star, contentDescription = null, tint = LootLegendary, modifier = Modifier.size(14.dp))
          Text(
            text = "РЕКОРД ${stats.bestScore}",
            style = MaterialTheme.typography.labelSmall,
            color = TextPrimary,
          )
        }

        // Centerpiece Live Animated Hero Stage
        Row(
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
          verticalAlignment = Alignment.Bottom,
          horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          // Animated Hero Podium
          Box(
            modifier = Modifier.size(width = 104.dp, height = 114.dp),
            contentAlignment = Alignment.Center,
          ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
              val cx = size.width * 0.5f
              // Glowing energy ring under hero feet
              drawOval(
                color = heroSkin.accentColor.copy(alpha = 0.28f + 0.14f * pulse),
                topLeft = Offset(cx - 44.dp.toPx(), size.height - 26.dp.toPx()),
                size = Size(88.dp.toPx(), 22.dp.toPx()),
              )
              drawOval(
                color = heroSkin.accentColor,
                topLeft = Offset(cx - 38.dp.toPx(), size.height - 23.dp.toPx()),
                size = Size(76.dp.toPx(), 16.dp.toPx()),
                style = Stroke(2.dp.toPx()),
              )
              // Floating bobbing Hero Skin!
              val bobY = sin(orbitPhase * 2f) * 4.dp.toPx()
              val charSize = 68.dp.toPx()
              drawSkinCharacter(
                skin = heroSkin,
                left = cx - charSize * 0.5f,
                top = size.height - 22.dp.toPx() - charSize + bobY,
                w = charSize,
                h = charSize,
                facingRight = true,
              )
              // Orbiting Tear & Spark
              val orbX = cx + cos(orbitPhase) * 44.dp.toPx()
              val orbY = size.height * 0.45f + sin(orbitPhase) * 16.dp.toPx()
              drawCircle(TearCyan.copy(alpha = 0.85f), 4.5.dp.toPx(), Offset(orbX, orbY))
            }
          }

          // Hero Info & Quick Stats
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(
              text = "БОЕЦ НА АРЕНЕ",
              style = MaterialTheme.typography.labelSmall,
              color = TextSecondary,
            )
            Text(
              text = heroSkin.titleRu.uppercase(),
              style = MaterialTheme.typography.headlineMedium.copy(fontSize = 22.sp),
              color = heroSkin.accentColor,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = heroSkin.subtitleRu,
              style = MaterialTheme.typography.bodyMedium,
              color = TextPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              TinyPill("ЭТАЖ ${stats.bestFloor}", StormGlow)
              TinyPill("УБИТО ${stats.totalKills}", MeatCrimson)
            }
          }
        }
      }

      // 3. Bold Glowing Play CTA Button
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .shadow(
            elevation = (10 + pulse * 8).dp,
            shape = RoundedCornerShape(20.dp),
            ambientColor = MeatCrimson,
            spotColor = MeatCrimson,
          )
          .clip(RoundedCornerShape(20.dp))
          .background(
            Brush.horizontalGradient(
              listOf(MeatCrimson, Color(0xFFE11D48), StormViolet)
            )
          )
          .border(1.5.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
          .clickable(onClick = onPlayClick)
          .testTag("play_button")
          .heightIn(min = 64.dp)
          .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = TextPrimary,
            modifier = Modifier.size(30.dp),
          )
          Column {
            Text(
              text = stringResource(R.string.btn_play),
              style = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp),
              color = TextPrimary,
            )
            Text(
              text = "Пройди этаж босса = +10 монет",
              style = MaterialTheme.typography.labelSmall,
              color = TextPrimary.copy(alpha = 0.85f),
            )
          }
        }
      }

      // 3b. Online Multiplayer CTA Button
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(18.dp))
          .background(
            Brush.horizontalGradient(
              listOf(Color(0xFF0E293D), DungeonSurface, Color(0xFF281542))
            )
          )
          .border(1.5.dp, TearCyan.copy(alpha = 0.8f), RoundedCornerShape(18.dp))
          .clickable(onClick = onOpenMultiplayer)
          .testTag("multiplayer_button")
          .heightIn(min = 56.dp)
          .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.Groups,
            contentDescription = null,
            tint = TearCyan,
            modifier = Modifier.size(26.dp),
          )
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = stringResource(R.string.btn_multiplayer),
              style = MaterialTheme.typography.titleMedium,
              color = TearCyan,
            )
            Text(
              text = "Онлайн Кооп и Дуэль PvP по коду комнаты",
              style = MaterialTheme.typography.labelSmall,
              color = TextSecondary,
            )
          }
          TinyPill("ONLINE", Color(0xFF00FFA3))
        }
      }

      // 4. Interactive Lobby Tabs (Skins / Bosses / Tutorial / Settings)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(16.dp))
          .background(DungeonSurface.copy(alpha = 0.85f))
          .border(1.dp, DungeonBorder, RoundedCornerShape(16.dp))
          .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        LobbyTab.entries.forEach { tab ->
          val isSelected = activeTab == tab
          val icon = when (tab) {
            LobbyTab.SKINS -> Icons.Filled.Style
            LobbyTab.BOSSES -> Icons.Filled.Whatshot
            LobbyTab.TUTORIAL -> Icons.Filled.School
            LobbyTab.SETTINGS -> Icons.Filled.Settings
          }
          Box(
            modifier = Modifier
              .weight(1f)
              .heightIn(min = 52.dp)
              .testTag("lobby_tab_${tab.name.lowercase()}")
              .clip(RoundedCornerShape(12.dp))
              .background(if (isSelected) TearCyan.copy(alpha = 0.18f) else Color.Transparent)
              .border(
                width = if (isSelected) 1.2.dp else 0.dp,
                color = if (isSelected) TearCyan else Color.Transparent,
                shape = RoundedCornerShape(12.dp),
              )
              .clickable { activeTab = tab }
              .padding(horizontal = 4.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
              Icon(
                imageVector = icon,
                contentDescription = tab.titleRu,
                tint = if (isSelected) TearCyan else TextSecondary,
                modifier = Modifier.size(16.dp),
              )
              Text(
                text = tab.titleRu,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = if (isSelected) TearCyan else TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }

      // 5. Dynamic Tab Content Card
      AnimatedContent(
        targetState = activeTab,
        label = "lobby_tab_content",
        modifier = Modifier.fillMaxWidth(),
      ) { tab ->
        when (tab) {
          LobbyTab.SKINS -> SkinsWardrobePanel(
            stats = stats,
            onSelectOrBuySkin = { skin ->
              if (skin.requiresPassword && skin.key !in stats.unlockedSkins) {
                passwordDialogSkin = skin
              } else {
                onSelectOrBuySkin(skin)
              }
            },
          )

          LobbyTab.BOSSES -> BossesAndRecordsPanel(stats = stats)

          LobbyTab.TUTORIAL -> TutorialTabPanel(
            heroSkin = heroSkin,
            onStartPlay = onPlayClick,
          )

          LobbyTab.SETTINGS -> LobbySettingsPanel(
            stats = stats,
            onSelectScreenMode = onSelectScreenMode,
            onToggleSound = onToggleSound,
            onToggleHaptics = onToggleHaptics,
            onUpdateControlsLayout = onUpdateControlsLayout,
            onOpenControlsEditor = onOpenControlsEditor,
          )
        }
      }
    }

    passwordDialogSkin?.let { targetSkin ->
      SecretSkinPasswordDialog(
        skin = targetSkin,
        onDismiss = { passwordDialogSkin = null },
        onSubmitPassword = { code, onResult ->
          onUnlockSecretSkin(targetSkin, code) { unlocked ->
            if (unlocked) passwordDialogSkin = null
            onResult(unlocked)
          }
        },
      )
    }
  }
}

@Composable
private fun SecretSkinPasswordDialog(
  skin: PlayerSkin,
  onDismiss: () -> Unit,
  onSubmitPassword: (String, (Boolean) -> Unit) -> Unit,
) {
  var passwordText by remember { mutableStateOf("") }
  var isWrong by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = DungeonStone,
    titleContentColor = skin.accentColor,
    title = {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Box(
          modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(StormVoid)
            .border(1.5.dp, skin.accentColor, CircleShape),
          contentAlignment = Alignment.Center,
        ) {
          Canvas(modifier = Modifier.size(30.dp)) {
            drawSkinCharacter(skin, 0f, 0f, size.width, size.height, facingRight = true)
          }
        }
        Column {
          Text(
            text = stringResource(R.string.title_admin_unlock),
            style = MaterialTheme.typography.titleMedium,
            color = skin.accentColor,
          )
          Text(
            text = skin.titleRu,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
          )
        }
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "Введите секретный пароль для открытия скина «${skin.titleRu}»:",
          style = MaterialTheme.typography.bodyMedium,
          color = TextPrimary,
        )
        OutlinedTextField(
          value = passwordText,
          onValueChange = {
            passwordText = it
            isWrong = false
          },
          singleLine = true,
          placeholder = { Text(stringResource(R.string.hint_admin_password), color = TextSecondary) },
          visualTransformation = PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
          isError = isWrong,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("admin_password_input"),
        )
        if (isWrong) {
          Text(
            text = stringResource(R.string.error_wrong_password),
            style = MaterialTheme.typography.labelSmall,
            color = MeatCrimson,
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          onSubmitPassword(passwordText) { ok ->
            if (!ok) isWrong = true
          }
        },
        colors = ButtonDefaults.buttonColors(containerColor = skin.accentColor, contentColor = StormVoid),
        modifier = Modifier
          .heightIn(min = 48.dp)
          .testTag("admin_unlock_confirm_button"),
      ) {
        Text(stringResource(R.string.btn_unlock), style = MaterialTheme.typography.labelLarge)
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.heightIn(min = 48.dp),
      ) {
        Text("ОТМЕНА", color = TextSecondary)
      }
    },
  )
}

@Composable
private fun SkinsWardrobePanel(
  stats: PlayerStats,
  onSelectOrBuySkin: (PlayerSkin) -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(20.dp))
      .background(DungeonSurface.copy(alpha = 0.90f))
      .border(1.dp, DungeonBorder, RoundedCornerShape(20.dp))
      .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = stringResource(R.string.label_skins_title),
        style = MaterialTheme.typography.labelLarge,
        color = LootLegendary,
      )
      Text(
        text = stringResource(R.string.label_coins_fmt, stats.coins),
        style = MaterialTheme.typography.labelSmall,
        color = TextPrimary,
      )
    }

    val skins = PlayerSkin.entries
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      skins.chunked(2).forEach { rowSkins ->
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          rowSkins.forEach { skin ->
            SkinCard(
              skin = skin,
              unlocked = skin.key in stats.unlockedSkins,
              selected = stats.selectedSkin == skin,
              coins = stats.coins,
              onClick = { onSelectOrBuySkin(skin) },
              modifier = Modifier.weight(1f),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun BossesAndRecordsPanel(stats: PlayerStats) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(20.dp))
      .background(DungeonSurface.copy(alpha = 0.90f))
      .border(1.dp, DungeonBorder, RoundedCornerShape(20.dp))
      .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .background(StormVoid.copy(alpha = 0.75f))
        .padding(vertical = 10.dp, horizontal = 8.dp),
      horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
      StatColumn(stringResource(R.string.label_best_score), "${stats.bestScore}", LootLegendary)
      StatColumn(stringResource(R.string.label_best_floor), "${stats.bestFloor}", StormGlow)
      StatColumn(stringResource(R.string.label_kills), "${stats.totalKills}", MeatCrimson)
      StatColumn(stringResource(R.string.label_runs), "${stats.totalRuns}", TearCyan)
    }

    Text(
      text = "ДОСЬЕ БОССОВ (НАГРАДА +10 МОНЕТ)",
      style = MaterialTheme.typography.labelLarge,
      color = MeatCrimson,
    )

    BossIntelRow("МОНСТРО БУРИ", "Прыгает и бьёт веером слёз + ударной волной", Color(0xFFE06D85))
    BossIntelRow("ГЕРЦОГ МЯСА", "Летает, делает рывки, круговые залпы и зовёт мух", Color(0xFFA8244B))
    BossIntelRow("МЕГА-ПИЛА X9", "Катится по арене, запрыгивает вверх и бьёт очередями", Color(0xFF94A3B8))
    BossIntelRow("КУБ КЕВИН", "Парит над ареной и атакует спиралями Бури", StormViolet)
  }
}

@Composable
private fun BossIntelRow(title: String, desc: String, color: Color) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .background(StormVoid.copy(alpha = 0.8f))
      .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
      .padding(horizontal = 10.dp, vertical = 8.dp),
  ) {
    Box(
      modifier = Modifier
        .size(12.dp)
        .clip(CircleShape)
        .background(color),
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.labelLarge, color = color)
      Text(desc, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
  }
}

@Composable
private fun LobbySettingsPanel(
  stats: PlayerStats,
  onSelectScreenMode: (ScreenMode) -> Unit,
  onToggleSound: () -> Unit,
  onToggleHaptics: () -> Unit,
  onUpdateControlsLayout: (ControlsLayoutConfig) -> Unit,
  onOpenControlsEditor: () -> Unit,
) {
  val layoutConfig = stats.controlsLayout
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(20.dp))
      .background(DungeonSurface.copy(alpha = 0.90f))
      .border(1.dp, DungeonBorder, RoundedCornerShape(20.dp))
      .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Icon(Icons.Filled.MenuBook, contentDescription = null, tint = TearCyan, modifier = Modifier.size(18.dp))
      Text(
        text = stringResource(R.string.label_orientation),
        style = MaterialTheme.typography.labelLarge,
        color = TextPrimary,
      )
    }

    ScreenModeSelectorRow(
      selected = stats.screenMode,
      onSelect = onSelectScreenMode,
    )

    // Controls Location Customization
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Icon(Icons.Filled.OpenWith, contentDescription = null, tint = TearCyan, modifier = Modifier.size(18.dp))
        Text(
          text = stringResource(R.string.label_controls_layout),
          style = MaterialTheme.typography.labelLarge,
          color = TextPrimary,
        )
      }
      TinyPill(
        text = if (layoutConfig.customEnabled) "СВОЁ" else "СТАНДАРТ",
        color = if (layoutConfig.customEnabled) SuccessGreen else TextSecondary,
      )
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OutlinedButton(
        onClick = {
          val newSwapSides = !layoutConfig.swapSides
          val newPositions = if (layoutConfig.customEnabled) {
            layoutConfig.positions.mapValues { (_, p) ->
              p.copy(x = (1f - p.x).coerceIn(0.08f, 0.92f))
            }
          } else {
            ControlsLayoutConfig.defaultPositions(newSwapSides)
          }
          onUpdateControlsLayout(
            layoutConfig.copy(
              swapSides = newSwapSides,
              positions = newPositions,
            )
          )
        },
        border = BorderStroke(1.dp, if (layoutConfig.swapSides) TearCyan else DungeonBorder),
        colors = ButtonDefaults.outlinedButtonColors(
          containerColor = if (layoutConfig.swapSides) TearCyan.copy(alpha = 0.16f) else StormVoid,
        ),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        modifier = Modifier
          .weight(1f)
          .height(48.dp)
          .testTag("controls_mirror_toggle"),
      ) {
        Icon(Icons.Filled.SwapHoriz, contentDescription = null, tint = TearCyan, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(
          text = if (layoutConfig.swapSides) "Левша" else "Правша",
          style = MaterialTheme.typography.labelSmall,
          color = if (layoutConfig.swapSides) TearCyan else TextPrimary,
          maxLines = 1,
        )
      }

      OutlinedButton(
        onClick = {
          val newSwapJS = !layoutConfig.swapJumpShoot
          val newPositions = if (layoutConfig.customEnabled) {
            val jumpPos = layoutConfig.posFor(ControlId.JUMP)
            val shootPos = layoutConfig.posFor(ControlId.SHOOT)
            layoutConfig.positions.toMutableMap().apply {
              put(ControlId.JUMP, shootPos)
              put(ControlId.SHOOT, jumpPos)
            }
          } else {
            layoutConfig.positions
          }
          onUpdateControlsLayout(
            layoutConfig.copy(
              swapJumpShoot = newSwapJS,
              positions = newPositions,
            )
          )
        },
        border = BorderStroke(1.dp, if (layoutConfig.swapJumpShoot) StormViolet else DungeonBorder),
        colors = ButtonDefaults.outlinedButtonColors(
          containerColor = if (layoutConfig.swapJumpShoot) StormViolet.copy(alpha = 0.2f) else StormVoid,
        ),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        modifier = Modifier
          .weight(1.25f)
          .height(48.dp)
          .testTag("controls_swap_js_toggle"),
      ) {
        Text(
          text = "Прыжок ↔ Огонь",
          style = MaterialTheme.typography.labelSmall,
          color = if (layoutConfig.swapJumpShoot) StormGlow else TextPrimary,
          maxLines = 1,
        )
      }
    }

    Button(
      onClick = onOpenControlsEditor,
      colors = ButtonDefaults.buttonColors(
        containerColor = TearCyan.copy(alpha = 0.2f),
        contentColor = TearCyan,
      ),
      border = BorderStroke(1.2.dp, TearCyan),
      shape = RoundedCornerShape(14.dp),
      modifier = Modifier
        .fillMaxWidth()
        .height(48.dp)
        .testTag("open_controls_editor_button"),
    ) {
      Icon(Icons.Filled.OpenWith, contentDescription = null, tint = TearCyan, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(8.dp))
      Text(
        text = stringResource(R.string.btn_edit_controls),
        style = MaterialTheme.typography.labelLarge,
        color = TearCyan,
      )
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      OutlinedButton(
        onClick = onToggleSound,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .weight(1f)
          .height(48.dp),
      ) {
        Icon(
          imageVector = if (stats.soundEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
          contentDescription = null,
          tint = if (stats.soundEnabled) TearCyan else TextSecondary,
        )
        Spacer(Modifier.width(6.dp))
        Text(
          text = "${stringResource(R.string.label_sound)}: ${if (stats.soundEnabled) "ВКЛ" else "ВЫКЛ"}",
          color = if (stats.soundEnabled) TextPrimary else TextSecondary,
          maxLines = 1,
        )
      }

      OutlinedButton(
        onClick = onToggleHaptics,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .weight(1f)
          .height(48.dp)
          .testTag("haptics_toggle"),
      ) {
        Icon(
          imageVector = Icons.Filled.Vibration,
          contentDescription = null,
          tint = if (stats.hapticsEnabled) TearCyan else TextSecondary,
        )
        Spacer(Modifier.width(6.dp))
        Text(
          text = "${stringResource(R.string.label_haptics)}: ${if (stats.hapticsEnabled) "ВКЛ" else "ВЫКЛ"}",
          color = if (stats.hapticsEnabled) TextPrimary else TextSecondary,
          maxLines = 1,
        )
      }
    }

    // Control Legend Hint
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(StormVoid.copy(alpha = 0.7f))
        .padding(10.dp),
    ) {
      Icon(Icons.Filled.Bolt, contentDescription = null, tint = TearCyan, modifier = Modifier.size(18.dp))
      Text(
        text = "Тапай по арене чтобы стрелять в точку, или жми ОГОНЬ с авто-наводкой!",
        style = MaterialTheme.typography.bodyMedium,
        color = TextSecondary,
      )
    }
  }
}

@Composable
private fun SkinCard(
  skin: PlayerSkin,
  unlocked: Boolean,
  selected: Boolean,
  coins: Int,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val canAfford = coins >= skin.price
  val borderColor = when {
    selected -> TearCyan
    unlocked -> skin.accentColor.copy(alpha = 0.75f)
    canAfford -> LootLegendary
    else -> DungeonBorder
  }
  val bg = when {
    selected -> Brush.horizontalGradient(listOf(TearCyan.copy(alpha = 0.22f), StormVoid))
    else -> Brush.horizontalGradient(listOf(StormVoid, DungeonStone.copy(alpha = 0.85f)))
  }

  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    modifier = modifier
      .heightIn(min = 66.dp)
      .testTag("skin_card_${skin.key}")
      .clip(RoundedCornerShape(14.dp))
      .background(bg)
      .border(if (selected) 2.dp else 1.dp, borderColor, RoundedCornerShape(14.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 10.dp, vertical = 8.dp),
  ) {
    Box(
      modifier = Modifier
        .size(42.dp)
        .clip(CircleShape)
        .background(DungeonSurface)
        .border(1.dp, skin.accentColor.copy(alpha = 0.5f), CircleShape),
      contentAlignment = Alignment.Center,
    ) {
      Canvas(modifier = Modifier.size(30.dp)) {
        drawSkinCharacter(skin, 0f, 0f, size.width, size.height, facingRight = true)
      }
    }

    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = skin.titleRu,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) TearCyan else TextPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        when {
          selected -> {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = TearCyan, modifier = Modifier.size(13.dp))
            Text(
              text = stringResource(R.string.label_skin_equipped),
              style = MaterialTheme.typography.labelSmall,
              color = TearCyan,
            )
          }
          unlocked -> {
            Text(
              text = stringResource(R.string.label_skin_select),
              style = MaterialTheme.typography.labelSmall,
              color = SuccessGreen,
            )
          }
          skin.requiresPassword -> {
            Icon(
              imageVector = Icons.Filled.VpnKey,
              contentDescription = null,
              tint = skin.accentColor,
              modifier = Modifier.size(13.dp),
            )
            Text(
              text = stringResource(R.string.label_skin_by_password),
              style = MaterialTheme.typography.labelSmall,
              color = skin.accentColor,
            )
          }
          else -> {
            Icon(
              imageVector = if (canAfford) Icons.Filled.MonetizationOn else Icons.Filled.Lock,
              contentDescription = null,
              tint = if (canAfford) LootLegendary else TextSecondary,
              modifier = Modifier.size(13.dp),
            )
            Text(
              text = "${skin.price} мон.",
              style = MaterialTheme.typography.labelSmall,
              color = if (canAfford) LootLegendary else TextSecondary,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun MiniTagChip(text: String, color: Color) {
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(8.dp))
      .background(StormVoid.copy(alpha = 0.85f))
      .border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
      .padding(horizontal = 7.dp, vertical = 3.dp),
  ) {
    Text(text = text, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = color)
  }
}

@Composable
private fun TinyPill(text: String, color: Color) {
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(8.dp))
      .background(color.copy(alpha = 0.16f))
      .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
      .padding(horizontal = 8.dp, vertical = 3.dp),
  ) {
    Text(text = text, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = color)
  }
}

@Composable
fun ScreenModeSelectorRow(
  selected: ScreenMode,
  onSelect: (ScreenMode) -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    ScreenModeChip(
      label = stringResource(R.string.mode_book),
      isSelected = selected == ScreenMode.BOOK,
      onClick = { onSelect(ScreenMode.BOOK) },
      tag = "mode_book_button",
      modifier = Modifier.weight(1f),
    )
    ScreenModeChip(
      label = stringResource(R.string.mode_auto),
      isSelected = selected == ScreenMode.AUTO,
      onClick = { onSelect(ScreenMode.AUTO) },
      tag = "mode_auto_button",
      modifier = Modifier.weight(1f),
    )
    ScreenModeChip(
      label = stringResource(R.string.mode_landscape),
      isSelected = selected == ScreenMode.LANDSCAPE,
      onClick = { onSelect(ScreenMode.LANDSCAPE) },
      tag = "mode_landscape_button",
      modifier = Modifier.weight(1f),
    )
  }
}

@Composable
private fun ScreenModeChip(
  label: String,
  isSelected: Boolean,
  onClick: () -> Unit,
  tag: String,
  modifier: Modifier = Modifier,
) {
  Button(
    onClick = onClick,
    colors = ButtonDefaults.buttonColors(
      containerColor = if (isSelected) TearCyan.copy(alpha = 0.22f) else StormVoid,
      contentColor = if (isSelected) TearCyan else TextSecondary,
    ),
    border = BorderStroke(
      width = 1.dp,
      color = if (isSelected) TearCyan else DungeonBorder,
    ),
    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
    shape = RoundedCornerShape(12.dp),
    modifier = modifier
      .height(48.dp)
      .testTag(tag),
  ) {
    Text(text = label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
  }
}

@Composable
private fun StatColumn(label: String, value: String, color: Color) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(text = value, style = MaterialTheme.typography.titleLarge, color = color)
    Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
  }
}
