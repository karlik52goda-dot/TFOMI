package com.example.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.game.model.Vec2
import com.example.ui.theme.*
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun TouchControls(
  mats: Int,
  buildCost: Int,
  dashReadyFraction: Float,
  layoutConfig: com.example.data.ControlsLayoutConfig = com.example.data.ControlsLayoutConfig(),
  onMoveXChange: (Float) -> Unit,
  onJumpPress: () -> Unit,
  onJumpRelease: () -> Unit,
  onShootChange: (Boolean, Vec2) -> Unit,
  onDashPress: () -> Unit,
  onBuildFloor: () -> Unit,
  onBuildWall: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BoxWithConstraints(
    modifier = modifier
      .fillMaxWidth()
      .background(DungeonStone)
      .border(1.dp, DungeonBorder)
      .windowInsetsPadding(WindowInsets.navigationBars)
  ) {
    val isPortraitDeck = maxWidth < 560.dp
    val canBuild = mats >= buildCost
    val mirror = layoutConfig.swapSides
    val swapJS = layoutConfig.swapJumpShoot

    if (isPortraitDeck) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        // Row 1: Tactical Fortnite Build + Dash Strip
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          val floorBtn: @Composable () -> Unit = {
            TapActionButton(
              label = "${stringResource(R.string.ctrl_floor)} $buildCost",
              icon = Icons.Filled.Layers,
              accent = if (canBuild) BuildWood else LootCommon,
              enabled = canBuild,
              tag = "build_floor_button",
              horizontal = true,
              onPress = onBuildFloor,
              onRelease = {},
              modifier = Modifier.weight(1f).height(48.dp),
            )
          }
          val wallBtn: @Composable () -> Unit = {
            TapActionButton(
              label = "${stringResource(R.string.ctrl_wall)} $buildCost",
              icon = Icons.Filled.Construction,
              accent = if (canBuild) BuildWood else LootCommon,
              enabled = canBuild,
              tag = "build_wall_button",
              horizontal = true,
              onPress = onBuildWall,
              onRelease = {},
              modifier = Modifier.weight(1f).height(48.dp),
            )
          }
          val dashBtn: @Composable () -> Unit = {
            TapActionButton(
              label = stringResource(R.string.ctrl_dash),
              icon = Icons.Filled.Bolt,
              accent = if (dashReadyFraction >= 0.99f) StormViolet else LootCommon,
              enabled = dashReadyFraction >= 0.99f,
              tag = "dash_button",
              horizontal = true,
              onPress = onDashPress,
              onRelease = {},
              modifier = Modifier.weight(1f).height(48.dp),
            )
          }
          if (mirror) {
            dashBtn(); wallBtn(); floorBtn()
          } else {
            floorBtn(); wallBtn(); dashBtn()
          }
        }

        // Row 2: Movement Slider + Shoot & Jump (respecting mirror & swapJumpShoot)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .height(104.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          val movePad: @Composable () -> Unit = {
            MoveSliderPad(
              onMoveXChange = onMoveXChange,
              modifier = Modifier
                .weight(1.15f)
                .fillMaxHeight(),
            )
          }
          val shootPad: @Composable () -> Unit = {
            ShootAimPad(
              onShootChange = onShootChange,
              modifier = Modifier
                .size(86.dp)
                .testTag("shoot_pad"),
            )
          }
          val jumpPad: @Composable () -> Unit = {
            TapActionButton(
              label = stringResource(R.string.ctrl_jump),
              icon = Icons.Filled.ArrowUpward,
              accent = MeatCrimson,
              enabled = true,
              tag = "jump_button",
              horizontal = false,
              onPress = onJumpPress,
              onRelease = onJumpRelease,
              modifier = Modifier
                .weight(0.85f)
                .widthIn(min = 74.dp)
                .fillMaxHeight(),
            )
          }

          if (mirror) {
            if (swapJS) { shootPad(); jumpPad() } else { jumpPad(); shootPad() }
            movePad()
          } else {
            movePad()
            if (swapJS) { jumpPad(); shootPad() } else { shootPad(); jumpPad() }
          }
        }
      }
    } else {
      // Wide / Landscape Control Deck
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
          .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        val moveDeck: @Composable () -> Unit = {
          MoveSliderPad(
            onMoveXChange = onMoveXChange,
            modifier = Modifier
              .widthIn(min = 152.dp, max = 200.dp)
              .height(84.dp),
          )
        }
        val actionDeck: @Composable () -> Unit = {
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            val buildCol: @Composable () -> Unit = {
              Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TapActionButton(
                  label = "${stringResource(R.string.ctrl_floor)} $buildCost",
                  icon = Icons.Filled.Layers,
                  accent = if (canBuild) BuildWood else LootCommon,
                  enabled = canBuild,
                  tag = "build_floor_button",
                  horizontal = true,
                  onPress = onBuildFloor,
                  onRelease = {},
                  modifier = Modifier.size(width = 98.dp, height = 48.dp),
                )
                TapActionButton(
                  label = "${stringResource(R.string.ctrl_wall)} $buildCost",
                  icon = Icons.Filled.Construction,
                  accent = if (canBuild) BuildWood else LootCommon,
                  enabled = canBuild,
                  tag = "build_wall_button",
                  horizontal = true,
                  onPress = onBuildWall,
                  onRelease = {},
                  modifier = Modifier.size(width = 98.dp, height = 48.dp),
                )
              }
            }
            val dashBox: @Composable () -> Unit = {
              TapActionButton(
                label = stringResource(R.string.ctrl_dash),
                icon = Icons.Filled.Bolt,
                accent = if (dashReadyFraction >= 0.99f) StormViolet else LootCommon,
                enabled = dashReadyFraction >= 0.99f,
                tag = "dash_button",
                horizontal = false,
                onPress = onDashPress,
                onRelease = {},
                modifier = Modifier.size(width = 68.dp, height = 90.dp),
              )
            }
            val shootBox: @Composable () -> Unit = {
              ShootAimPad(
                onShootChange = onShootChange,
                modifier = Modifier
                  .size(84.dp)
                  .testTag("shoot_pad"),
              )
            }
            val jumpBox: @Composable () -> Unit = {
              TapActionButton(
                label = stringResource(R.string.ctrl_jump),
                icon = Icons.Filled.ArrowUpward,
                accent = MeatCrimson,
                enabled = true,
                tag = "jump_button",
                horizontal = false,
                onPress = onJumpPress,
                onRelease = onJumpRelease,
                modifier = Modifier.size(width = 76.dp, height = 90.dp),
              )
            }

            if (mirror) {
              if (swapJS) { shootBox(); jumpBox() } else { jumpBox(); shootBox() }
              dashBox()
              buildCol()
            } else {
              buildCol()
              dashBox()
              if (swapJS) { jumpBox(); shootBox() } else { shootBox(); jumpBox() }
            }
          }
        }

        if (mirror) {
          actionDeck()
          moveDeck()
        } else {
          moveDeck()
          actionDeck()
        }
      }
    }
  }
}

@Composable
fun FloatingCustomTouchControls(
  mats: Int,
  buildCost: Int,
  dashReadyFraction: Float,
  layoutConfig: com.example.data.ControlsLayoutConfig,
  onMoveXChange: (Float) -> Unit,
  onJumpPress: () -> Unit,
  onJumpRelease: () -> Unit,
  onShootChange: (Boolean, Vec2) -> Unit,
  onDashPress: () -> Unit,
  onBuildFloor: () -> Unit,
  onBuildWall: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val canBuild = mats >= buildCost
  BoxWithConstraints(
    modifier = modifier
      .fillMaxSize()
      .windowInsetsPadding(WindowInsets.safeDrawing),
  ) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
    val heightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)

    com.example.data.ControlId.entries.forEach { id ->
      val pos = layoutConfig.posFor(id)
      val (wDp, hDp) = when (id) {
        com.example.data.ControlId.MOVE -> 148.dp to 84.dp
        com.example.data.ControlId.SHOOT -> 82.dp to 82.dp
        com.example.data.ControlId.JUMP -> 78.dp to 86.dp
        com.example.data.ControlId.DASH -> 66.dp to 68.dp
        com.example.data.ControlId.BUILD_FLOOR, com.example.data.ControlId.BUILD_WALL -> 86.dp to 48.dp
      }
      val wPx = with(density) { wDp.toPx() }
      val hPx = with(density) { hDp.toPx() }
      val ox = (pos.x * widthPx - wPx * 0.5f).roundToInt()
      val oy = (pos.y * heightPx - hPx * 0.5f).roundToInt()
      val mod = Modifier.offset { IntOffset(ox, oy) }.size(wDp, hDp)

      when (id) {
        com.example.data.ControlId.MOVE -> MoveSliderPad(onMoveXChange = onMoveXChange, modifier = mod)
        com.example.data.ControlId.SHOOT -> ShootAimPad(onShootChange = onShootChange, modifier = mod.testTag("shoot_pad"))
        com.example.data.ControlId.JUMP -> TapActionButton(
          label = stringResource(R.string.ctrl_jump),
          icon = Icons.Filled.ArrowUpward,
          accent = MeatCrimson,
          enabled = true,
          tag = "jump_button",
          horizontal = false,
          onPress = onJumpPress,
          onRelease = onJumpRelease,
          modifier = mod,
        )
        com.example.data.ControlId.DASH -> TapActionButton(
          label = stringResource(R.string.ctrl_dash),
          icon = Icons.Filled.Bolt,
          accent = if (dashReadyFraction >= 0.99f) StormViolet else LootCommon,
          enabled = dashReadyFraction >= 0.99f,
          tag = "dash_button",
          horizontal = false,
          onPress = onDashPress,
          onRelease = {},
          modifier = mod,
        )
        com.example.data.ControlId.BUILD_FLOOR -> TapActionButton(
          label = "${stringResource(R.string.ctrl_floor)} $buildCost",
          icon = Icons.Filled.Layers,
          accent = if (canBuild) BuildWood else LootCommon,
          enabled = canBuild,
          tag = "build_floor_button",
          horizontal = true,
          onPress = onBuildFloor,
          onRelease = {},
          modifier = mod,
        )
        com.example.data.ControlId.BUILD_WALL -> TapActionButton(
          label = "${stringResource(R.string.ctrl_wall)} $buildCost",
          icon = Icons.Filled.Construction,
          accent = if (canBuild) BuildWood else LootCommon,
          enabled = canBuild,
          tag = "build_wall_button",
          horizontal = true,
          onPress = onBuildWall,
          onRelease = {},
          modifier = mod,
        )
      }
    }
  }
}

@Composable
fun MoveSliderPad(
  onMoveXChange: (Float) -> Unit,
  modifier: Modifier = Modifier,
) {
  var activeDir by remember { mutableFloatStateOf(0f) }

  Box(
    modifier = modifier
      .testTag("move_pad")
      .clip(RoundedCornerShape(20.dp))
      .background(DungeonSurface)
      .border(1.5.dp, if (activeDir != 0f) TearCyan else DungeonBorder, RoundedCornerShape(20.dp))
      .pointerInput(Unit) {
        while (true) {
          awaitPointerEventScope {
            val down = awaitFirstDown(requireUnconsumed = false)
            val center = size.width * 0.5f
            fun updateFromX(px: Float) {
              val dir = ((px - center) / (size.width * 0.34f)).coerceIn(-1f, 1f)
              activeDir = if (kotlin.math.abs(dir) < 0.12f) 0f else dir
              onMoveXChange(activeDir)
            }
            updateFromX(down.position.x)
            while (true) {
              val event = awaitPointerEvent(PointerEventPass.Main)
              val tracked = event.changes.firstOrNull { it.id == down.id }
              if (tracked == null || !tracked.pressed) {
                activeDir = 0f
                onMoveXChange(0f)
                break
              }
              updateFromX(tracked.position.x)
            }
          }
        }
      },
    contentAlignment = Alignment.Center,
  ) {
    Row(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 10.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = Modifier
          .size(44.dp)
          .clip(CircleShape)
          .background(if (activeDir < -0.15f) TearCyan.copy(alpha = 0.35f) else StormVoid.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          Icons.AutoMirrored.Filled.KeyboardArrowLeft,
          contentDescription = "Влево",
          tint = if (activeDir < -0.15f) TearCyan else TextSecondary,
          modifier = Modifier.size(28.dp),
        )
      }

      // Sliding thumb puck indicator
      Box(
        modifier = Modifier
          .offset { IntOffset((activeDir * 16.dp.toPx()).roundToInt(), 0) }
          .size(14.dp)
          .clip(CircleShape)
          .background(if (activeDir != 0f) TearCyan else DungeonBorder),
      )

      Box(
        modifier = Modifier
          .size(44.dp)
          .clip(CircleShape)
          .background(if (activeDir > 0.15f) TearCyan.copy(alpha = 0.35f) else StormVoid.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          Icons.AutoMirrored.Filled.KeyboardArrowRight,
          contentDescription = "Вправо",
          tint = if (activeDir > 0.15f) TearCyan else TextSecondary,
          modifier = Modifier.size(28.dp),
        )
      }
    }
  }
}

@Composable
fun ShootAimPad(
  onShootChange: (Boolean, Vec2) -> Unit,
  modifier: Modifier = Modifier,
) {
  var pressed by remember { mutableStateOf(false) }
  var aimVec by remember { mutableStateOf(Vec2(0f, 0f)) }

  Box(
    modifier = modifier
      .clip(CircleShape)
      .background(if (pressed) TearCyan.copy(alpha = 0.30f) else DungeonSurface)
      .border(2.dp, TearCyan, CircleShape)
      .pointerInput(Unit) {
        while (true) {
          awaitPointerEventScope {
            val down = awaitFirstDown(requireUnconsumed = false)
            pressed = true
            val cx = size.width * 0.5f
            val cy = size.height * 0.5f
            fun emitAim(px: Float, py: Float) {
              val dx = px - cx
              val dy = py - cy
              val d = hypot(dx, dy)
              val aim = if (d > size.width * 0.15f) Vec2(dx / d, dy / d) else Vec2(0f, 0f)
              aimVec = aim
              onShootChange(true, aim)
            }
            emitAim(down.position.x, down.position.y)
            while (true) {
              val event = awaitPointerEvent(PointerEventPass.Main)
              val tracked = event.changes.firstOrNull { it.id == down.id }
              if (tracked == null || !tracked.pressed) {
                pressed = false
                aimVec = Vec2(0f, 0f)
                onShootChange(false, Vec2(0f, 0f))
                break
              }
              emitAim(tracked.position.x, tracked.position.y)
            }
          }
        }
      },
    contentAlignment = Alignment.Center,
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Icon(Icons.Filled.MyLocation, contentDescription = stringResource(R.string.ctrl_shoot), tint = TearCyan, modifier = Modifier.size(22.dp))
      Text(
        text = stringResource(R.string.ctrl_shoot),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
        color = TearCyan,
      )
    }
    if (pressed && (aimVec.x != 0f || aimVec.y != 0f)) {
      Box(
        modifier = Modifier
          .offset { IntOffset((aimVec.x * 22.dp.toPx()).roundToInt(), (aimVec.y * 22.dp.toPx()).roundToInt()) }
          .size(10.dp)
          .clip(CircleShape)
          .background(TearCyan),
      )
    }
  }
}

@Composable
fun TapActionButton(
  label: String,
  icon: ImageVector,
  accent: Color,
  enabled: Boolean,
  tag: String,
  horizontal: Boolean = false,
  onPress: () -> Unit,
  onRelease: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var isDown by remember { mutableStateOf(false) }

  Box(
    modifier = modifier
      .testTag(tag)
      .clip(RoundedCornerShape(16.dp))
      .background(
        when {
          !enabled -> StormVoid.copy(alpha = 0.55f)
          isDown -> accent.copy(alpha = 0.42f)
          else -> DungeonSurface
        }
      )
      .border(1.8.dp, if (enabled) accent else DungeonBorder, RoundedCornerShape(16.dp))
      .pointerInput(enabled) {
        while (true) {
          awaitPointerEventScope {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (enabled) {
              isDown = true
              onPress()
            }
            while (true) {
              val event = awaitPointerEvent(PointerEventPass.Main)
              val tracked = event.changes.firstOrNull { it.id == down.id }
              if (tracked == null || !tracked.pressed) {
                if (isDown) {
                  isDown = false
                  onRelease()
                }
                break
              }
            }
          }
        }
      },
    contentAlignment = Alignment.Center,
  ) {
    if (horizontal) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.padding(horizontal = 6.dp),
      ) {
        Icon(icon, contentDescription = label, tint = if (enabled) accent else TextSecondary, modifier = Modifier.size(18.dp))
        Text(
          text = label,
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
          color = if (enabled) TextPrimary else TextSecondary,
          maxLines = 1,
        )
      }
    } else {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(4.dp),
      ) {
        Icon(icon, contentDescription = label, tint = if (enabled) accent else TextSecondary, modifier = Modifier.size(22.dp))
        Text(
          text = label,
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
          color = if (enabled) TextPrimary else TextSecondary,
          maxLines = 1,
        )
      }
    }
  }
}
