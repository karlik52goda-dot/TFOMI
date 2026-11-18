package com.example

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ScreenMode
import com.example.game.model.PlayerSkin
import com.example.game.model.RemotePlayerState
import com.example.game.ui.*
import com.example.multiplayer.MatchMode
import com.example.multiplayer.MultiplayerHubScreen
import com.example.multiplayer.MultiplayerRepository
import com.example.multiplayer.RoomDoc
import com.example.multiplayer.RoomPlayer
import com.example.multiplayer.RoomStatus
import com.example.ui.theme.MeatDropTheme
import com.example.viewmodel.AppScreen
import com.example.viewmodel.GameViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  private val viewModel: GameViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MeatDropTheme {
        val stats by viewModel.playerStats.collectAsStateWithLifecycle()
        LaunchedEffect(stats.screenMode) {
          requestedOrientation = when (stats.screenMode) {
            ScreenMode.BOOK -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            ScreenMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            ScreenMode.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
          }
        }
        MeatDropApp(viewModel = viewModel)
      }
    }
  }

  override fun onPause() {
    super.onPause()
    viewModel.pauseGame()
  }
}

@Composable
fun MeatDropApp(viewModel: GameViewModel) {
  val screen by viewModel.screen.collectAsStateWithLifecycle()
  val stats by viewModel.playerStats.collectAsStateWithLifecycle()
  val hud by viewModel.hud.collectAsStateWithLifecycle()
  val frameTick by viewModel.frameTick.collectAsStateWithLifecycle()
  val hapticPulse by viewModel.hapticPulse.collectAsStateWithLifecycle()
  val activeMp by viewModel.activeMultiplayer.collectAsStateWithLifecycle()

  val context = LocalContext.current
  val currentStats by rememberUpdatedState(stats)
  val haptics = LocalHapticFeedback.current
  LaunchedEffect(hapticPulse) {
    if (hapticPulse > 0L) {
      haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
  }

  // Live Multiplayer State Synchronization (Firestore) while in an active authenticated session
  LaunchedEffect(activeMp?.roomId, activeMp?.localUid) {
    val session = activeMp ?: return@LaunchedEffect
    if (Firebase.auth.currentUser == null) return@LaunchedEffect

    val repo = MultiplayerRepository(context)
    var latestRoom: RoomDoc? = null
    var latestPlayers: List<RoomPlayer> = emptyList()
    var lastPushedFloor = 1
    var lastSentShotSeq = -1L
    var sentGameOver = false

    val roomJob = launch {
      repo.observeRoom(session.roomId)
        .catch { }
        .collect { room ->
          latestRoom = room
        }
    }

    val playersJob = launch {
      repo.observeRoomPlayers(session.roomId, session.localUid)
        .catch { }
        .collect { players ->
          latestPlayers = players
          val remote = players.firstOrNull { it.uid != session.localUid }
          viewModel.engine.updateRemotePlayerState(
            remote?.let {
              RemotePlayerState(
                uid = it.uid,
                displayName = it.displayName,
                skin = PlayerSkin.fromKey(it.skinKey),
                roomGx = it.roomGx.toInt(),
                roomGy = it.roomGy.toInt(),
                x = it.x.toFloat(),
                y = it.y.toFloat(),
                vx = it.vx.toFloat(),
                vy = it.vy.toFloat(),
                hp = it.hp.toInt(),
                maxHp = it.maxHp.toInt(),
                shield = it.shield.toInt(),
                score = it.score.toInt(),
                facingRight = it.facingRight,
                alive = it.alive,
                shotSeq = it.shotSeq,
                shotX = it.shotX.toFloat(),
                shotY = it.shotY.toFloat(),
                shotVx = it.shotVx.toFloat(),
                shotVy = it.shotVy.toFloat(),
              )
            }
          )
        }
    }

    try {
      while (true) {
        if (Firebase.auth.currentUser == null) break
        val eng = viewModel.engine
        val roomCoord = eng.floorMap.currentCoord
        val p = eng.player
        val displayName = Firebase.auth.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Боец TFOMI"
        val isAlive = !eng.isGameOver

        repo.syncPlayerState(
          roomId = session.roomId,
          displayName = displayName,
          skinKey = currentStats.selectedSkin.key,
          roomGx = roomCoord.first,
          roomGy = roomCoord.second,
          x = p.x,
          y = p.y,
          vx = p.vx,
          vy = p.vy,
          hp = p.hp,
          maxHp = p.maxHp,
          shield = p.shield,
          score = eng.score,
          facingRight = p.facingRight,
          alive = isAlive,
          shotSeq = eng.localShotSeq,
          shotX = eng.lastShotX,
          shotY = eng.lastShotY,
          shotVx = eng.lastShotVx,
          shotVy = eng.lastShotVy,
        )
        lastSentShotSeq = eng.localShotSeq

        val curFloor = eng.floorMap.floorNumber
        if (session.isHost && curFloor > lastPushedFloor && latestRoom?.roomStatus() == RoomStatus.PLAYING) {
          lastPushedFloor = curFloor
          repo.updateFloor(session.roomId, curFloor)
        }

        if (eng.isGameOver && !sentGameOver) {
          sentGameOver = true
          if (session.mode == MatchMode.PVP && latestRoom?.roomStatus() == RoomStatus.PLAYING) {
            val remoteUid = latestPlayers.firstOrNull { it.uid != session.localUid }?.uid.orEmpty()
            if (remoteUid.isNotEmpty()) {
              repo.finishMatch(session.roomId, remoteUid)
            }
          }
        }

        var waitedMs = 0
        while (waitedMs < 110) {
          if (eng.localShotSeq != lastSentShotSeq || (eng.isGameOver && !sentGameOver)) break
          delay(25L)
          waitedMs += 25
        }
      }
    } finally {
      roomJob.cancel()
      playersJob.cancel()
    }
  }

  // 60fps Frame Ticker while Playing
  LaunchedEffect(screen) {
    if (screen == AppScreen.PLAYING) {
      var lastNs = 0L
      while (true) {
        withFrameNanos { now ->
          if (lastNs != 0L) {
            val dt = (now - lastNs) / 1_000_000_000f
            viewModel.stepFrame(dt)
          }
          lastNs = now
        }
      }
    }
  }

  var showControlsEditor by remember { mutableStateOf(false) }

  BackHandler(enabled = showControlsEditor || screen != AppScreen.TITLE) {
    if (showControlsEditor) {
      showControlsEditor = false
      return@BackHandler
    }
    when (screen) {
      AppScreen.PLAYING -> viewModel.pauseGame()
      AppScreen.PAUSED -> viewModel.resumeGame()
      AppScreen.GAME_OVER -> viewModel.returnToTitle()
      AppScreen.MULTIPLAYER -> viewModel.returnToTitle()
      AppScreen.TITLE -> Unit
    }
  }

  val focusRequester = remember { FocusRequester() }
  LaunchedEffect(screen) {
    if (screen == AppScreen.PLAYING) {
      runCatching { focusRequester.requestFocus() }
    }
  }

  var keyLeft by remember { mutableStateOf(false) }
  var keyRight by remember { mutableStateOf(false) }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .focusRequester(focusRequester)
      .focusable()
      .onPreviewKeyEvent { event ->
        if (screen != AppScreen.PLAYING) return@onPreviewKeyEvent false
        val isDown = event.type == KeyEventType.KeyDown
        val isUp = event.type == KeyEventType.KeyUp
        when (event.key) {
          Key.A, Key.DirectionLeft -> {
            if (isDown) keyLeft = true else if (isUp) keyLeft = false
            viewModel.moveX = (if (keyRight) 1f else 0f) - (if (keyLeft) 1f else 0f)
            true
          }
          Key.D, Key.DirectionRight -> {
            if (isDown) keyRight = true else if (isUp) keyRight = false
            viewModel.moveX = (if (keyRight) 1f else 0f) - (if (keyLeft) 1f else 0f)
            true
          }
          Key.W, Key.Spacebar, Key.DirectionUp -> {
            if (isDown) viewModel.triggerJumpPress() else if (isUp) viewModel.releaseJump()
            true
          }
          Key.J, Key.Enter -> {
            if (isDown) viewModel.shootHeld = true else if (isUp) viewModel.shootHeld = false
            true
          }
          Key.K, Key.ShiftLeft -> {
            if (isDown) viewModel.triggerDash()
            true
          }
          Key.Q -> {
            if (isDown) viewModel.triggerBuildFloor()
            true
          }
          Key.E -> {
            if (isDown) viewModel.triggerBuildWall()
            true
          }
          else -> false
        }
      },
  ) {
    when (screen) {
      AppScreen.TITLE -> {
        TitleScreen(
          stats = stats,
          onPlayClick = { viewModel.startNewRun() },
          onOpenMultiplayer = { viewModel.openMultiplayer() },
          onSelectOrBuySkin = { viewModel.selectOrBuySkin(it) },
          onUnlockSecretSkin = { skin, password, onResult ->
            viewModel.unlockSecretSkin(skin, password, onResult)
          },
          onToggleSound = { viewModel.toggleSound() },
          onToggleHaptics = { viewModel.toggleHaptics() },
          onSelectScreenMode = { viewModel.setScreenMode(it) },
          onUpdateControlsLayout = { viewModel.updateControlsLayout(it) },
          onOpenControlsEditor = { showControlsEditor = true },
        )
      }

      AppScreen.MULTIPLAYER -> {
        MultiplayerHubScreen(
          selectedSkin = stats.selectedSkin,
          onClose = { viewModel.returnToTitle() },
          onMatchStarted = { session -> viewModel.startMultiplayerRun(session) },
        )
      }

      AppScreen.PLAYING, AppScreen.PAUSED, AppScreen.GAME_OVER -> {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
          val useBookSpread = maxWidth >= 600.dp && maxHeight >= 520.dp && maxWidth < maxHeight * 1.38f

          if (stats.controlsLayout.customEnabled) {
            Box(modifier = Modifier.fillMaxSize()) {
              GameCanvas(
                engine = viewModel.engine,
                frameTick = frameTick,
                onTapAimChange = { viewModel.tapAimWorldPos = it },
              )
              GameHud(
                hud = hud,
                onPauseClick = { viewModel.pauseGame() },
                onToggleAutoAim = { viewModel.toggleAutoAim() },
              )
              FloatingCustomTouchControls(
                mats = hud.mats,
                buildCost = hud.buildCost,
                dashReadyFraction = hud.dashReadyFraction,
                layoutConfig = stats.controlsLayout,
                onMoveXChange = { viewModel.moveX = it },
                onJumpPress = { viewModel.triggerJumpPress() },
                onJumpRelease = { viewModel.releaseJump() },
                onShootChange = { held, aim ->
                  viewModel.shootHeld = held
                  viewModel.aimDir = aim
                },
                onDashPress = { viewModel.triggerDash() },
                onBuildFloor = { viewModel.triggerBuildFloor() },
                onBuildWall = { viewModel.triggerBuildWall() },
              )
            }
          } else if (useBookSpread) {
            BookSpreadLayout(
              hud = hud,
              arenaContent = {
                GameCanvas(
                  engine = viewModel.engine,
                  frameTick = frameTick,
                  onTapAimChange = { viewModel.tapAimWorldPos = it },
                )
                GameHud(
                  hud = hud,
                  onPauseClick = { viewModel.pauseGame() },
                  onToggleAutoAim = { viewModel.toggleAutoAim() },
                )
              },
              onMoveXChange = { viewModel.moveX = it },
              onJumpPress = { viewModel.triggerJumpPress() },
              onJumpRelease = { viewModel.releaseJump() },
              onShootChange = { held, aim ->
                viewModel.shootHeld = held
                viewModel.aimDir = aim
              },
              onDashPress = { viewModel.triggerDash() },
              onBuildFloor = { viewModel.triggerBuildFloor() },
              onBuildWall = { viewModel.triggerBuildWall() },
              layoutConfig = stats.controlsLayout,
            )
          } else {
            Column(modifier = Modifier.fillMaxSize()) {
              Box(
                modifier = Modifier
                  .weight(1f)
                  .fillMaxWidth(),
              ) {
                GameCanvas(
                  engine = viewModel.engine,
                  frameTick = frameTick,
                  onTapAimChange = { viewModel.tapAimWorldPos = it },
                )
                GameHud(
                  hud = hud,
                  onPauseClick = { viewModel.pauseGame() },
                  onToggleAutoAim = { viewModel.toggleAutoAim() },
                )
              }

              TouchControls(
                mats = hud.mats,
                buildCost = hud.buildCost,
                dashReadyFraction = hud.dashReadyFraction,
                layoutConfig = stats.controlsLayout,
                onMoveXChange = { viewModel.moveX = it },
                onJumpPress = { viewModel.triggerJumpPress() },
                onJumpRelease = { viewModel.releaseJump() },
                onShootChange = { held, aim ->
                  viewModel.shootHeld = held
                  viewModel.aimDir = aim
                },
                onDashPress = { viewModel.triggerDash() },
                onBuildFloor = { viewModel.triggerBuildFloor() },
                onBuildWall = { viewModel.triggerBuildWall() },
              )
            }
          }
        }

        if (screen == AppScreen.PAUSED) {
          PauseOverlay(
            soundEnabled = stats.soundEnabled,
            hapticsEnabled = stats.hapticsEnabled,
            screenMode = stats.screenMode,
            onSelectScreenMode = { viewModel.setScreenMode(it) },
            onResume = { viewModel.resumeGame() },
            onRestart = {
              if (activeMp != null) viewModel.openMultiplayer() else viewModel.startNewRun()
            },
            onToggleSound = { viewModel.toggleSound() },
            onToggleHaptics = { viewModel.toggleHaptics() },
            onOpenControlsEditor = { showControlsEditor = true },
            onMainMenu = { viewModel.returnToTitle() },
          )
        }

        if (screen == AppScreen.GAME_OVER) {
          GameOverOverlay(
            hud = hud,
            bestScore = stats.bestScore,
            onRestart = {
              if (activeMp != null) viewModel.openMultiplayer() else viewModel.startNewRun()
            },
            onMainMenu = { viewModel.returnToTitle() },
          )
        }
      }
    }

    if (showControlsEditor) {
      ControlsEditorOverlay(
        initialConfig = stats.controlsLayout,
        onSave = {
          viewModel.updateControlsLayout(it)
          showControlsEditor = false
        },
        onCancel = { showControlsEditor = false },
      )
    }
  }
}
