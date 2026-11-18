package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.SoundEffect
import com.example.audio.SoundEngine
import com.example.data.PlayerPrefsRepository
import com.example.data.PlayerStats
import com.example.game.engine.GameEngine
import com.example.game.model.GameHudSnapshot
import com.example.game.model.InputState
import com.example.game.model.PlayerSkin
import com.example.game.model.Vec2
import com.example.multiplayer.ActiveMultiplayerSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppScreen {
  TITLE,
  MULTIPLAYER,
  PLAYING,
  PAUSED,
  GAME_OVER,
}

class GameViewModel(application: Application) : AndroidViewModel(application) {
  private val prefs = PlayerPrefsRepository(application)
  val soundEngine = SoundEngine()

  val playerStats: StateFlow<PlayerStats> = prefs.statsFlow
    .stateIn(viewModelScope, SharingStarted.Eagerly, PlayerStats())

  private val _screen = MutableStateFlow(AppScreen.TITLE)
  val screen: StateFlow<AppScreen> = _screen.asStateFlow()

  private val _activeMultiplayer = MutableStateFlow<ActiveMultiplayerSession?>(null)
  val activeMultiplayer: StateFlow<ActiveMultiplayerSession?> = _activeMultiplayer.asStateFlow()

  private val _frameTick = MutableStateFlow(0L)
  val frameTick: StateFlow<Long> = _frameTick.asStateFlow()

  private val _hud = MutableStateFlow(GameHudSnapshot())
  val hud: StateFlow<GameHudSnapshot> = _hud.asStateFlow()

  private val _hapticPulse = MutableStateFlow(0L)
  val hapticPulse: StateFlow<Long> = _hapticPulse.asStateFlow()

  // Live mutable input state updated at zero latency from touch/keyboard
  @Volatile var moveX: Float = 0f
  @Volatile var jumpHeld: Boolean = false
  @Volatile var shootHeld: Boolean = false
  @Volatile var aimDir: Vec2 = Vec2(0f, 0f)
  @Volatile var tapAimWorldPos: Vec2? = null
  @Volatile private var pendingJumpPress = false
  @Volatile private var pendingDashPress = false
  @Volatile private var pendingBuildFloorPress = false
  @Volatile private var pendingBuildWallPress = false

  val engine = GameEngine(
    onSound = { sfx -> soundEngine.play(sfx) },
    onHapticLight = {
      if (playerStats.value.hapticsEnabled) _hapticPulse.value = _hapticPulse.value + 1L
    },
    onHapticHeavy = {
      if (playerStats.value.hapticsEnabled) _hapticPulse.value = _hapticPulse.value + 2L
    },
    onFloorCleared = { _, coins ->
      viewModelScope.launch {
        prefs.addCoins(coins)
      }
    },
    onGameOver = { score, floor, kills ->
      viewModelScope.launch {
        prefs.recordRunEnd(score, floor, kills)
      }
      _screen.value = AppScreen.GAME_OVER
    },
  )

  init {
    viewModelScope.launch {
      playerStats.collect { stats ->
        soundEngine.enabled = stats.soundEnabled
        engine.autoAimEnabled = stats.autoAimEnabled
        engine.playerSkin = stats.selectedSkin
      }
    }
  }

  fun openMultiplayer() {
    clearInputs()
    _activeMultiplayer.value = null
    engine.remotePlayer = null
    _screen.value = AppScreen.MULTIPLAYER
  }

  fun startNewRun() {
    clearInputs()
    _activeMultiplayer.value = null
    engine.startNewRun()
    _hud.value = engine.snapshotHud()
    _screen.value = AppScreen.PLAYING
  }

  fun startMultiplayerRun(session: ActiveMultiplayerSession) {
    clearInputs()
    _activeMultiplayer.value = session
    engine.startNewRun(
      seed = session.seed,
      mpMode = session.mode.key,
      isMpGuest = !session.isHost,
    )
    _hud.value = engine.snapshotHud()
    _screen.value = AppScreen.PLAYING
  }

  fun pauseGame() {
    if (_screen.value == AppScreen.PLAYING) {
      clearInputs()
      _screen.value = AppScreen.PAUSED
    }
  }

  fun resumeGame() {
    if (_screen.value == AppScreen.PAUSED) {
      clearInputs()
      _screen.value = AppScreen.PLAYING
    }
  }

  fun returnToTitle() {
    clearInputs()
    _activeMultiplayer.value = null
    engine.remotePlayer = null
    _screen.value = AppScreen.TITLE
  }

  fun toggleSound() {
    viewModelScope.launch {
      prefs.setSoundEnabled(!playerStats.value.soundEnabled)
    }
  }

  fun toggleHaptics() {
    viewModelScope.launch {
      prefs.setHapticsEnabled(!playerStats.value.hapticsEnabled)
    }
  }

  fun setScreenMode(mode: com.example.data.ScreenMode) {
    viewModelScope.launch {
      prefs.setScreenMode(mode)
    }
  }

  fun updateControlsLayout(config: com.example.data.ControlsLayoutConfig) {
    viewModelScope.launch {
      prefs.setControlsLayout(config)
    }
  }

  fun selectOrBuySkin(skin: PlayerSkin) {
    viewModelScope.launch {
      prefs.selectOrBuySkin(skin)
    }
  }

  fun unlockSecretSkin(skin: PlayerSkin, password: String, onResult: (Boolean) -> Unit) {
    viewModelScope.launch {
      val unlocked = prefs.unlockSecretSkin(skin, password)
      onResult(unlocked)
    }
  }

  fun toggleAutoAim() {
    val next = !playerStats.value.autoAimEnabled
    engine.autoAimEnabled = next
    viewModelScope.launch {
      prefs.setAutoAimEnabled(next)
    }
    _hud.value = engine.snapshotHud()
  }

  fun triggerJumpPress() {
    jumpHeld = true
    pendingJumpPress = true
  }

  fun releaseJump() {
    jumpHeld = false
  }

  fun triggerDash() {
    pendingDashPress = true
  }

  fun triggerBuildFloor() {
    pendingBuildFloorPress = true
  }

  fun triggerBuildWall() {
    pendingBuildWallPress = true
  }

  fun stepFrame(dt: Float) {
    if (_screen.value != AppScreen.PLAYING) return
    val input = InputState(
      moveX = moveX,
      jumpPressed = pendingJumpPress,
      jumpHeld = jumpHeld,
      shootHeld = shootHeld,
      aimDir = aimDir,
      targetWorldPos = tapAimWorldPos,
      dashPressed = pendingDashPress,
      buildFloorPressed = pendingBuildFloorPress,
      buildWallPressed = pendingBuildWallPress,
    )
    pendingJumpPress = false
    pendingDashPress = false
    pendingBuildFloorPress = false
    pendingBuildWallPress = false

    engine.tick(dt, input)
    val tick = _frameTick.value + 1L
    _frameTick.value = tick
    if (tick % 2L == 0L || engine.isGameOver) {
      _hud.value = engine.snapshotHud()
    }
  }

  private fun clearInputs() {
    moveX = 0f
    jumpHeld = false
    shootHeld = false
    aimDir = Vec2(0f, 0f)
    tapAimWorldPos = null
    pendingJumpPress = false
    pendingDashPress = false
    pendingBuildFloorPress = false
    pendingBuildWallPress = false
  }

  override fun onCleared() {
    super.onCleared()
    soundEngine.release()
  }
}
