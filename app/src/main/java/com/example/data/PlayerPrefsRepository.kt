package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.game.model.PlayerSkin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "meatdrop_prefs")

enum class ScreenMode(val key: String) {
  AUTO("auto"),
  BOOK("book"),
  LANDSCAPE("landscape");

  companion object {
    fun fromKey(key: String?): ScreenMode = entries.firstOrNull { it.key == key } ?: BOOK
  }
}

enum class ControlId(val key: String, val labelRu: String) {
  MOVE("move", "ДВИЖЕНИЕ"),
  JUMP("jump", "ПРЫЖОК"),
  SHOOT("shoot", "ОГОНЬ"),
  DASH("dash", "РЫВОК"),
  BUILD_FLOOR("floor", "ПОЛ"),
  BUILD_WALL("wall", "СТЕНА"),
}

data class ControlNormPos(val x: Float, val y: Float)

data class ControlsLayoutConfig(
  val customEnabled: Boolean = false,
  val swapSides: Boolean = false,
  val swapJumpShoot: Boolean = false,
  val positions: Map<ControlId, ControlNormPos> = defaultPositions(swapSides = false),
) {
  fun posFor(id: ControlId): ControlNormPos =
    positions[id] ?: defaultPositions(swapSides).getValue(id)

  fun encode(): String {
    val parts = ControlId.entries.joinToString(";") { id ->
      val p = posFor(id)
      "${id.key}:${p.x},${p.y}"
    }
    return "$customEnabled|$swapSides|$swapJumpShoot|$parts"
  }

  companion object {
    fun defaultPositions(swapSides: Boolean = false): Map<ControlId, ControlNormPos> {
      val base = mapOf(
        ControlId.MOVE to ControlNormPos(0.22f, 0.86f),
        ControlId.BUILD_FLOOR to ControlNormPos(0.50f, 0.72f),
        ControlId.BUILD_WALL to ControlNormPos(0.68f, 0.72f),
        ControlId.DASH to ControlNormPos(0.86f, 0.72f),
        ControlId.SHOOT to ControlNormPos(0.66f, 0.87f),
        ControlId.JUMP to ControlNormPos(0.86f, 0.87f),
      )
      return if (!swapSides) base else base.mapValues { (_, p) -> p.copy(x = (1f - p.x).coerceIn(0.08f, 0.92f)) }
    }

    fun decode(raw: String?): ControlsLayoutConfig {
      if (raw.isNullOrBlank()) return ControlsLayoutConfig()
      return runCatching {
        val main = raw.split("|")
        val custom = main.getOrNull(0)?.toBooleanStrictOrNull() ?: false
        val swapSides = main.getOrNull(1)?.toBooleanStrictOrNull() ?: false
        val swapJumpShoot = main.getOrNull(2)?.toBooleanStrictOrNull() ?: false
        val posMap = defaultPositions(swapSides).toMutableMap()
        val entriesStr = main.getOrNull(3).orEmpty()
        if (entriesStr.isNotBlank()) {
          entriesStr.split(";").forEach { item ->
            val kv = item.split(":")
            if (kv.size == 2) {
              val id = ControlId.entries.firstOrNull { it.key == kv[0] }
              val coords = kv[1].split(",")
              if (id != null && coords.size == 2) {
                val nx = coords[0].toFloatOrNull()?.coerceIn(0.08f, 0.92f)
                val ny = coords[1].toFloatOrNull()?.coerceIn(0.18f, 0.93f)
                if (nx != null && ny != null) {
                  posMap[id] = ControlNormPos(nx, ny)
                }
              }
            }
          }
        }
        ControlsLayoutConfig(
          customEnabled = custom,
          swapSides = swapSides,
          swapJumpShoot = swapJumpShoot,
          positions = posMap,
        )
      }.getOrDefault(ControlsLayoutConfig())
    }
  }
}

data class PlayerStats(
  val bestScore: Int = 0,
  val bestFloor: Int = 1,
  val totalKills: Int = 0,
  val totalRuns: Int = 0,
  val coins: Int = 0,
  val selectedSkin: PlayerSkin = PlayerSkin.MEAT_BOY,
  val unlockedSkins: Set<String> = setOf(PlayerSkin.MEAT_BOY.key),
  val soundEnabled: Boolean = true,
  val hapticsEnabled: Boolean = true,
  val autoAimEnabled: Boolean = true,
  val screenMode: ScreenMode = ScreenMode.BOOK,
  val controlsLayout: ControlsLayoutConfig = ControlsLayoutConfig(),
)

class PlayerPrefsRepository(private val context: Context) {
  private object Keys {
    val BEST_SCORE = intPreferencesKey("best_score")
    val BEST_FLOOR = intPreferencesKey("best_floor")
    val TOTAL_KILLS = intPreferencesKey("total_kills")
    val TOTAL_RUNS = intPreferencesKey("total_runs")
    val COINS = intPreferencesKey("coins")
    val SELECTED_SKIN = stringPreferencesKey("selected_skin")
    val UNLOCKED_SKINS = stringSetPreferencesKey("unlocked_skins")
    val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
    val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
    val AUTO_AIM_ENABLED = booleanPreferencesKey("auto_aim_enabled")
    val SCREEN_MODE = stringPreferencesKey("screen_mode")
    val CONTROLS_LAYOUT = stringPreferencesKey("controls_layout")
  }

  val statsFlow: Flow<PlayerStats> = context.dataStore.data.map { prefs ->
    val unlocked = (prefs[Keys.UNLOCKED_SKINS] ?: emptySet()) + PlayerSkin.MEAT_BOY.key
    PlayerStats(
      bestScore = prefs[Keys.BEST_SCORE] ?: 0,
      bestFloor = prefs[Keys.BEST_FLOOR] ?: 1,
      totalKills = prefs[Keys.TOTAL_KILLS] ?: 0,
      totalRuns = prefs[Keys.TOTAL_RUNS] ?: 0,
      coins = prefs[Keys.COINS] ?: 0,
      selectedSkin = PlayerSkin.fromKey(prefs[Keys.SELECTED_SKIN]),
      unlockedSkins = unlocked,
      soundEnabled = prefs[Keys.SOUND_ENABLED] ?: true,
      hapticsEnabled = prefs[Keys.HAPTICS_ENABLED] ?: true,
      autoAimEnabled = prefs[Keys.AUTO_AIM_ENABLED] ?: true,
      screenMode = ScreenMode.fromKey(prefs[Keys.SCREEN_MODE]),
      controlsLayout = ControlsLayoutConfig.decode(prefs[Keys.CONTROLS_LAYOUT]),
    )
  }

  suspend fun recordRunEnd(score: Int, floor: Int, kills: Int) {
    context.dataStore.edit { prefs ->
      val prevScore = prefs[Keys.BEST_SCORE] ?: 0
      val prevFloor = prefs[Keys.BEST_FLOOR] ?: 1
      val prevKills = prefs[Keys.TOTAL_KILLS] ?: 0
      val prevRuns = prefs[Keys.TOTAL_RUNS] ?: 0
      if (score > prevScore) prefs[Keys.BEST_SCORE] = score
      if (floor > prevFloor) prefs[Keys.BEST_FLOOR] = floor
      prefs[Keys.TOTAL_KILLS] = prevKills + kills
      prefs[Keys.TOTAL_RUNS] = prevRuns + 1
    }
  }

  suspend fun addCoins(amount: Int) {
    if (amount <= 0) return
    context.dataStore.edit { prefs ->
      val prev = prefs[Keys.COINS] ?: 0
      prefs[Keys.COINS] = prev + amount
    }
  }

  suspend fun selectOrBuySkin(skin: PlayerSkin) {
    context.dataStore.edit { prefs ->
      val unlocked = (prefs[Keys.UNLOCKED_SKINS] ?: emptySet()) + PlayerSkin.MEAT_BOY.key
      if (skin.key in unlocked) {
        prefs[Keys.SELECTED_SKIN] = skin.key
      } else if (!skin.requiresPassword) {
        val currentCoins = prefs[Keys.COINS] ?: 0
        if (currentCoins >= skin.price) {
          prefs[Keys.COINS] = currentCoins - skin.price
          prefs[Keys.UNLOCKED_SKINS] = unlocked + skin.key
          prefs[Keys.SELECTED_SKIN] = skin.key
        }
      }
    }
  }

  suspend fun unlockSecretSkin(skin: PlayerSkin, password: String): Boolean {
    if (skin == PlayerSkin.ADMIN && password.trim() == "123580") {
      context.dataStore.edit { prefs ->
        val unlocked = (prefs[Keys.UNLOCKED_SKINS] ?: emptySet()) + PlayerSkin.MEAT_BOY.key
        prefs[Keys.UNLOCKED_SKINS] = unlocked + skin.key
        prefs[Keys.SELECTED_SKIN] = skin.key
      }
      return true
    }
    return false
  }

  suspend fun setSoundEnabled(enabled: Boolean) {
    context.dataStore.edit { it[Keys.SOUND_ENABLED] = enabled }
  }

  suspend fun setHapticsEnabled(enabled: Boolean) {
    context.dataStore.edit { it[Keys.HAPTICS_ENABLED] = enabled }
  }

  suspend fun setScreenMode(mode: ScreenMode) {
    context.dataStore.edit { it[Keys.SCREEN_MODE] = mode.key }
  }

  suspend fun setAutoAimEnabled(enabled: Boolean) {
    context.dataStore.edit { it[Keys.AUTO_AIM_ENABLED] = enabled }
  }

  suspend fun setControlsLayout(config: ControlsLayoutConfig) {
    context.dataStore.edit { it[Keys.CONTROLS_LAYOUT] = config.encode() }
  }
}
