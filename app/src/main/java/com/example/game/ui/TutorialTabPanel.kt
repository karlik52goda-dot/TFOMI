package com.example.game.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.game.model.PlayerSkin
import com.example.ui.theme.*
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private data class TutorialStep(
  val index: Int,
  val shortLabel: String,
  val title: String,
  val accent: Color,
  val icon: ImageVector,
  val bullets: List<String>,
)

private val TUTORIAL_STEPS = listOf(
  TutorialStep(
    index = 0,
    shortLabel = "1. Прыжки",
    title = "Движение, прыжки и рывок",
    accent = MeatCrimson,
    icon = Icons.Filled.Bolt,
    bullets = listOf(
      "Веди палец по слайдеру «ДВИЖЕНИЕ» влево и вправо для точного бега.",
      "Удерживай «ПРЫЖОК» дольше, чтобы взлететь выше — можно отталкиваться и от стен!",
      "Кнопка «РЫВОК» делает быстрый бросок с кратким щитом неуязвимости.",
    ),
  ),
  TutorialStep(
    index = 1,
    shortLabel = "2. Огонь",
    title = "Стрельба и авто-наводка",
    accent = TearCyan,
    icon = Icons.Filled.MyLocation,
    bullets = listOf(
      "Зажимай кнопку «ОГОНЬ» — при включённой «НАВОДКЕ» пули сами летят к ближайшему врагу.",
      "Или тапай/води пальцем прямо по арене, чтобы стрелять точно в выбранную точку.",
      "Переключатель «НАВОДКА» находится сверху в боевом интерфейсе.",
    ),
  ),
  TutorialStep(
    index = 2,
    shortLabel = "3. Стройка",
    title = "Тактические укрытия Fortnite",
    accent = BuildWood,
    icon = Icons.Filled.Construction,
    bullets = listOf(
      "«ПОЛ» (10 материалов) ставит прочную доску под ногами прямо в прыжке.",
      "«СТЕНА» (10 материалов) возводит барьер перед героем и блокирует вражеские пули.",
      "Дерево выпадает из побеждённых монстров и разрушенных ящиков.",
    ),
  ),
  TutorialStep(
    index = 3,
    shortLabel = "4. Карта",
    title = "Комнаты, двери и Буря",
    accent = StormViolet,
    icon = Icons.Filled.Map,
    bullets = listOf(
      "Двери и нижний люк в полу (S) открываются только после зачистки всех врагов.",
      "Жёлтая комната с короной — Сокровищница с бесплатной реликвией и оружием.",
      "Фиолетовая Буря постепенно сужает комнату — не затягивай бой!",
    ),
  ),
  TutorialStep(
    index = 4,
    shortLabel = "5. Боссы",
    title = "Боссы, монеты и настройки",
    accent = LootLegendary,
    icon = Icons.Filled.Star,
    bullets = listOf(
      "В красной комнате ждёт один из 4 уникальных боссов. После победы прыгай в портал!",
      "Каждый пройденный этаж навсегда даёт +10 монет на скины Банан, Айзек, Mr. Beast, Оливер200, Чекушка, Мелстрой и Мама птица.",
      "Во вкладке «НАСТРОЙКИ» (и на паузе) можно переставить любые кнопки под себя.",
    ),
  ),
)

@Composable
fun TutorialTabPanel(
  heroSkin: PlayerSkin,
  onStartPlay: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var currentStep by remember { mutableIntStateOf(0) }
  val step = TUTORIAL_STEPS[currentStep]

  val infinite = rememberInfiniteTransition(label = "tutorial_anim")
  val phase by infinite.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(2800, easing = LinearEasing),
      repeatMode = RepeatMode.Restart,
    ),
    label = "tut_phase",
  )

  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(20.dp))
      .background(DungeonSurface.copy(alpha = 0.92f))
      .border(1.dp, DungeonBorder, RoundedCornerShape(20.dp))
      .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    // Header + Step Counter
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Icon(Icons.Filled.School, contentDescription = null, tint = TearCyan, modifier = Modifier.size(18.dp))
        Text(
          text = stringResource(R.string.label_tutorial_header),
          style = MaterialTheme.typography.labelLarge,
          color = TearCyan,
        )
      }
      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(8.dp))
          .background(step.accent.copy(alpha = 0.16f))
          .border(1.dp, step.accent.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
          .padding(horizontal = 8.dp, vertical = 3.dp),
      ) {
        Text(
          text = "ШАГ ${currentStep + 1} / ${TUTORIAL_STEPS.size}",
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
          color = step.accent,
        )
      }
    }

    // Clickable Step Pills
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      TUTORIAL_STEPS.forEach { item ->
        val selected = item.index == currentStep
        Box(
          modifier = Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .testTag("tutorial_step_${item.index}")
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) item.accent.copy(alpha = 0.22f) else StormVoid.copy(alpha = 0.8f))
            .border(
              width = if (selected) 1.5.dp else 1.dp,
              color = if (selected) item.accent else DungeonBorder,
              shape = RoundedCornerShape(10.dp),
            )
            .clickable { currentStep = item.index }
            .padding(horizontal = 2.dp, vertical = 4.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = item.shortLabel,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = if (selected) item.accent else TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }

    // Animated Live Demonstration Stage
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(132.dp)
        .clip(RoundedCornerShape(16.dp))
        .background(StormVoid)
        .border(1.5.dp, step.accent.copy(alpha = 0.7f), RoundedCornerShape(16.dp)),
    ) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        drawTutorialScene(
          stepIndex = currentStep,
          phase = phase,
          heroSkin = heroSkin,
        )
      }
    }

    // Step Title & Bullets
    AnimatedContent(
      targetState = step,
      label = "tutorial_step_card",
      modifier = Modifier.fillMaxWidth(),
    ) { active ->
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(14.dp))
          .background(StormVoid.copy(alpha = 0.75f))
          .border(1.dp, DungeonBorder, RoundedCornerShape(14.dp))
          .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(active.icon, contentDescription = null, tint = active.accent, modifier = Modifier.size(20.dp))
          Text(
            text = active.title,
            style = MaterialTheme.typography.titleMedium,
            color = active.accent,
          )
        }
        active.bullets.forEach { bullet ->
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
          ) {
            Box(
              modifier = Modifier
                .padding(top = 6.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(active.accent),
            )
            Text(
              text = bullet,
              style = MaterialTheme.typography.bodyMedium,
              color = TextPrimary,
            )
          }
        }
      }
    }

    // Navigation Row: Prev / Next or Play!
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OutlinedButton(
        onClick = { if (currentStep > 0) currentStep-- },
        enabled = currentStep > 0,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (currentStep > 0) DungeonBorder else DungeonBorder.copy(alpha = 0.4f)),
        modifier = Modifier
          .weight(1f)
          .height(48.dp)
          .testTag("tutorial_prev_button"),
      ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
          text = stringResource(R.string.btn_tutorial_prev),
          style = MaterialTheme.typography.labelLarge,
        )
      }

      val isLast = currentStep == TUTORIAL_STEPS.lastIndex
      Button(
        onClick = {
          if (isLast) onStartPlay() else currentStep++
        },
        colors = ButtonDefaults.buttonColors(
          containerColor = if (isLast) MeatCrimson else TearCyan,
          contentColor = if (isLast) TextPrimary else StormVoid,
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
          .weight(1.2f)
          .height(48.dp)
          .testTag("tutorial_next_button"),
      ) {
        Text(
          text = if (isLast) stringResource(R.string.btn_play) else stringResource(R.string.btn_tutorial_next),
          style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.width(6.dp))
        Icon(
          imageVector = if (isLast) Icons.Filled.PlayArrow else Icons.AutoMirrored.Filled.ArrowForward,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
      }
    }

    // Compact Quick Visual Legend (Pickups & Rooms)
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .background(StormVoid.copy(alpha = 0.65f))
        .border(1.dp, DungeonBorder.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
        .padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Text(
        text = "ШПАРГАЛКА ПО ЗНАЧКАМ",
        style = MaterialTheme.typography.labelSmall,
        color = TextSecondary,
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        LegendChip(Icons.Filled.Favorite, "Сердце (+HP)", MeatCrimson, Modifier.weight(1f))
        LegendChip(Icons.Filled.Shield, "Щит (Броня)", ShieldBlue, Modifier.weight(1f))
      }
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        LegendChip(Icons.Filled.Layers, "Материалы", BuildWood, Modifier.weight(1f))
        LegendChip(Icons.Filled.MonetizationOn, "+10 за этаж", LootLegendary, Modifier.weight(1f))
      }
    }
  }
}

@Composable
private fun LegendChip(
  icon: ImageVector,
  label: String,
  color: Color,
  modifier: Modifier = Modifier,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    modifier = modifier
      .clip(RoundedCornerShape(10.dp))
      .background(DungeonSurface.copy(alpha = 0.75f))
      .padding(horizontal = 8.dp, vertical = 6.dp),
  ) {
    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
      color = TextPrimary,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

private fun DrawScope.drawTutorialScene(
  stepIndex: Int,
  phase: Float,
  heroSkin: PlayerSkin,
) {
  val w = size.width
  val h = size.height
  val floorY = h * 0.78f

  // Subtle background grid
  val step = 28.dp.toPx()
  var gx = 0f
  while (gx < w) {
    drawLine(Color.White.copy(alpha = 0.04f), Offset(gx, 0f), Offset(gx, h), 1f)
    gx += step
  }
  var gy = 0f
  while (gy < h) {
    drawLine(Color.White.copy(alpha = 0.04f), Offset(0f, gy), Offset(w, gy), 1f)
    gy += step
  }

  // Ground stone floor
  drawRect(
    color = DungeonSurface,
    topLeft = Offset(0f, floorY),
    size = Size(w, h - floorY),
  )
  drawLine(DungeonBorder, Offset(0f, floorY), Offset(w, floorY), strokeWidth = 2.dp.toPx())

  val charSize = 34.dp.toPx()

  when (stepIndex) {
    0 -> {
      // Step 1: Jump over spikes & dash onto platform
      val platX = w * 0.56f
      val platY = floorY - 34.dp.toPx()
      drawRoundRect(
        color = DungeonBorder,
        topLeft = Offset(platX, platY),
        size = Size(w * 0.28f, 10.dp.toPx()),
        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
      )
      // Red spikes on floor
      val spikeBaseX = w * 0.36f
      val spikeW = 12.dp.toPx()
      for (i in 0..2) {
        val sx = spikeBaseX + i * spikeW
        val p = Path().apply {
          moveTo(sx, floorY)
          lineTo(sx + spikeW * 0.5f, floorY - 14.dp.toPx())
          lineTo(sx + spikeW, floorY)
          close()
        }
        drawPath(p, MeatCrimson)
      }
      // Parabolic jump arc
      val t = phase
      val hx = w * 0.14f + t * w * 0.54f
      val arc = sin(t * PI.toFloat()).coerceAtLeast(0f)
      val hy = floorY - charSize - arc * 56.dp.toPx()
      // Violet dash trail
      if (t in 0.45f..0.72f) {
        drawCircle(StormViolet.copy(alpha = 0.45f), charSize * 0.48f, Offset(hx - 14.dp.toPx(), hy + charSize * 0.5f))
      }
      drawSkinCharacter(heroSkin, hx, hy, charSize, charSize, facingRight = true)
    }

    1 -> {
      // Step 2: Auto-aim & Tap-aim tears curving to target
      val hx = w * 0.16f
      val hy = floorY - charSize
      drawSkinCharacter(heroSkin, hx, hy, charSize, charSize, facingRight = true)

      val ex = w * 0.76f
      val enemyBounce = abs(sin(phase * 2f * PI.toFloat())) * 18.dp.toPx()
      val ey = floorY - 30.dp.toPx() - enemyBounce
      // Target enemy
      drawRoundRect(
        color = MeatCrimson,
        topLeft = Offset(ex - 15.dp.toPx(), ey),
        size = Size(30.dp.toPx(), 30.dp.toPx()),
        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
      )
      drawCircle(Color.White, 3.dp.toPx(), Offset(ex - 5.dp.toPx(), ey + 10.dp.toPx()))
      drawCircle(Color.White, 3.dp.toPx(), Offset(ex + 5.dp.toPx(), ey + 10.dp.toPx()))
      // Lock-on ring around enemy
      drawCircle(
        color = TearCyan.copy(alpha = 0.85f),
        radius = 22.dp.toPx(),
        center = Offset(ex, ey + 15.dp.toPx()),
        style = Stroke(1.8.dp.toPx()),
      )
      // Animated cyan tears flying from hero to enemy
      for (i in 0..2) {
        val bt = (phase + i * 0.33f) % 1f
        val bx = hx + charSize + bt * (ex - hx - charSize)
        val by = (hy + charSize * 0.45f) * (1f - bt) + (ey + 15.dp.toPx()) * bt
        drawCircle(TearCyan, 5.dp.toPx(), Offset(bx, by))
      }
    }

    2 -> {
      // Step 3: Build Floor + Build Wall blocking enemy shot
      val hx = w * 0.25f
      val hy = floorY - charSize - 24.dp.toPx()
      // Wooden floor placed under hero
      drawRoundRect(
        color = BuildWood,
        topLeft = Offset(hx - 10.dp.toPx(), hy + charSize),
        size = Size(54.dp.toPx(), 10.dp.toPx()),
        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
      )
      drawSkinCharacter(heroSkin, hx, hy, charSize, charSize, facingRight = true)

      // Wooden wall shielding hero from red shot
      val wallX = w * 0.52f
      drawRoundRect(
        color = BuildWood,
        topLeft = Offset(wallX, floorY - 56.dp.toPx()),
        size = Size(12.dp.toPx(), 56.dp.toPx()),
        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
      )
      drawRoundRect(
        color = BuildWoodDark,
        topLeft = Offset(wallX, floorY - 56.dp.toPx()),
        size = Size(12.dp.toPx(), 56.dp.toPx()),
        style = Stroke(1.5.dp.toPx()),
        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
      )
      // Incoming red projectile hitting wall
      val shotProgress = phase
      val sx = w * 0.85f - shotProgress * (w * 0.85f - wallX - 14.dp.toPx())
      drawCircle(MeatCrimson, 6.dp.toPx(), Offset(sx, floorY - 30.dp.toPx()))
    }

    3 -> {
      // Step 4: Storm closing from edges + glowing doorway + open SOUTH floor hatch
      val stormW = (0.08f + 0.14f * (0.5f + 0.5f * sin(phase * 2f * PI.toFloat()))) * w
      drawRect(
        brush = Brush.horizontalGradient(listOf(StormViolet.copy(alpha = 0.65f), Color.Transparent)),
        topLeft = Offset(0f, 0f),
        size = Size(stormW, floorY),
      )
      drawRect(
        brush = Brush.horizontalGradient(listOf(Color.Transparent, StormViolet.copy(alpha = 0.65f))),
        topLeft = Offset(w - stormW, 0f),
        size = Size(stormW, floorY),
      )
      // Open SOUTH hatch in center floor
      val hatchX = w * 0.5f - 26.dp.toPx()
      drawRect(StormVoid, Offset(hatchX, floorY - 2.dp.toPx()), Size(52.dp.toPx(), h - floorY + 2.dp.toPx()))
      drawLine(TearCyan, Offset(hatchX, floorY), Offset(hatchX + 52.dp.toPx(), floorY), strokeWidth = 3.dp.toPx())
      // Treasure & Boss minimap icons preview
      drawRoundRect(LootLegendary, Offset(w * 0.30f, h * 0.24f), Size(22.dp.toPx(), 16.dp.toPx()), CornerRadius(4.dp.toPx(), 4.dp.toPx()))
      drawRoundRect(MeatCrimson, Offset(w * 0.64f, h * 0.24f), Size(22.dp.toPx(), 16.dp.toPx()), CornerRadius(4.dp.toPx(), 4.dp.toPx()))
      drawSkinCharacter(heroSkin, w * 0.5f - charSize * 0.5f, floorY - charSize - 6.dp.toPx(), charSize, charSize, true)
    }

    4 -> {
      // Step 5: Portal +10 Coins reward
      val px = w * 0.66f
      val py = floorY - 38.dp.toPx()
      drawOval(
        brush = Brush.radialGradient(listOf(TearCyan, StormViolet, Color.Transparent)),
        topLeft = Offset(px - 22.dp.toPx(), py - 24.dp.toPx()),
        size = Size(44.dp.toPx(), 56.dp.toPx()),
      )
      drawOval(
        color = TearCyan,
        topLeft = Offset(px - 16.dp.toPx(), py - 18.dp.toPx()),
        size = Size(32.dp.toPx(), 44.dp.toPx()),
        style = Stroke(2.dp.toPx()),
      )
      // Floating gold coins rising from portal
      for (i in 0..2) {
        val cPhase = (phase + i * 0.33f) % 1f
        val cx = px + cos(cPhase * 4f) * 14.dp.toPx()
        val cy = py - cPhase * 34.dp.toPx()
        drawCircle(LootLegendary.copy(alpha = 1f - cPhase * 0.6f), 7.dp.toPx(), Offset(cx, cy))
      }
      val walkX = w * 0.24f + sin(phase * PI.toFloat()) * w * 0.18f
      drawSkinCharacter(heroSkin, walkX, floorY - charSize, charSize, charSize, facingRight = true)
    }
  }
}
