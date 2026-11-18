package com.example.game.model

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.LootCommon
import com.example.ui.theme.LootEpic
import com.example.ui.theme.LootLegendary
import com.example.ui.theme.LootRare

object WorldConst {
  const val TILE_SIZE = 48f
  const val GRID_COLS = 20
  const val GRID_ROWS = 12
  const val ROOM_WIDTH = GRID_COLS * TILE_SIZE   // 960f
  const val ROOM_HEIGHT = GRID_ROWS * TILE_SIZE  // 576f
}

data class Vec2(val x: Float = 0f, val y: Float = 0f)

enum class Direction(val dx: Int, val dy: Int) {
  NORTH(0, -1),
  SOUTH(0, 1),
  EAST(1, 0),
  WEST(-1, 0),
}

enum class RoomType {
  START,
  COMBAT,
  TREASURE,
  BOSS,
}

enum class TileKind {
  EMPTY,
  SOLID,
  SPIKE,
  CRATE,
  ONE_WAY,
}

data class BuildPiece(
  val id: Int,
  val x: Float,
  val y: Float,
  val width: Float,
  val height: Float,
  val isWall: Boolean,
  val isMetal: Boolean,
  var hp: Int,
  val maxHp: Int,
  var animProgress: Float = 0f,
)

data class BuzzSaw(
  val id: Int,
  val startX: Float,
  val startY: Float,
  val endX: Float,
  val endY: Float,
  val radius: Float = 22f,
  val speed: Float = 1.4f,
  val phase: Float = 0f,
  var x: Float = startX,
  var y: Float = startY,
  var angle: Float = 0f,
)

enum class WeaponRarity(val labelRu: String, val color: Color, val dmgMult: Float, val fireRateMult: Float) {
  COMMON("Обычное", LootCommon, 1.0f, 1.0f),
  RARE("Редкое", LootRare, 1.25f, 1.12f),
  EPIC("Эпик", LootEpic, 1.55f, 1.25f),
  LEGENDARY("Легендарка", LootLegendary, 1.95f, 1.4f),
}

enum class WeaponType(
  val titleRu: String,
  val baseDamage: Float,
  val fireInterval: Float,
  val tearSpeed: Float,
  val tearRadius: Float,
) {
  TEAR_BLASTER("Слёзомет", 16f, 0.24f, 640f, 9f),
  TACTICAL_SHOTGUN("Тактический дробовик", 12f, 0.44f, 590f, 8f),
  RAPID_SMG("Буря-ПП", 9f, 0.11f, 710f, 7f),
  BRIM_SNIPER("Брим-Снайпер", 38f, 0.56f, 980f, 12f),
}

data class WeaponLoadout(
  val type: WeaponType = WeaponType.TEAR_BLASTER,
  val rarity: WeaponRarity = WeaponRarity.COMMON,
) {
  val damage: Float get() = type.baseDamage * rarity.dmgMult
  val fireInterval: Float get() = type.fireInterval / rarity.fireRateMult
}

enum class RelicType(val titleRu: String, val descRu: String, val badgeColor: Color) {
  MUTANT_SPLIT("Тройные слёзы", "+2 боковых выстрела", Color(0xFF38E8FF)),
  RUBBER_BOUNCE("Рикошет", "Слёзы отскакивают от стен", Color(0xFFFF7AD9)),
  CHUG_SHIELD("Большой щит", "+2 синих щита сразу", Color(0xFF2B9BFF)),
  DOUBLE_WINGS("Крылья рывка", "Двойной прыжок в воздухе", Color(0xFFFFD166)),
  MEAT_VAMPIRISM("Кровавый пир", "Шанс лечения за убийство", Color(0xFFFF2E4D)),
  TURBO_BUILD("Металл-чертёж", "Стройка в 2 раза дешевле", Color(0xFF7DA2C4)),
  HOMING_EYE("Всевидящее око", "Слёзы наводятся на врагов", Color(0xFFC084FC)),
}

enum class EnemyType {
  MEAT_HOPPER,
  BLOOD_FLY,
  SAW_TURRET,
  BOSS_STORM_MONSTRO,
  BOSS_DUKE_OF_MEAT,
  BOSS_SAW_MECH,
  BOSS_STORM_CUBE;

  val isBoss: Boolean
    get() = this == BOSS_STORM_MONSTRO ||
      this == BOSS_DUKE_OF_MEAT ||
      this == BOSS_SAW_MECH ||
      this == BOSS_STORM_CUBE

  val bossTitleRu: String?
    get() = when (this) {
      BOSS_STORM_MONSTRO -> "МОНСТРО БУРИ"
      BOSS_DUKE_OF_MEAT -> "ГЕРЦОГ МЯСА"
      BOSS_SAW_MECH -> "МЕГА-ПИЛА X9"
      BOSS_STORM_CUBE -> "КУБ КЕВИН"
      else -> null
    }
}

data class Enemy(
  val id: Int,
  val type: EnemyType,
  var x: Float,
  var y: Float,
  val width: Float,
  val height: Float,
  var vx: Float = 0f,
  var vy: Float = 0f,
  var hp: Float,
  val maxHp: Float,
  var aiTimer: Float = 0f,
  var hitFlash: Float = 0f,
  var facingRight: Boolean = false,
  var onGround: Boolean = false,
  var aiPhase: Int = 0,
  var moveTimer: Float = 0f,
)

data class Projectile(
  val id: Int,
  var x: Float,
  var y: Float,
  var vx: Float,
  var vy: Float,
  val radius: Float,
  val damage: Float,
  val isPlayer: Boolean,
  var life: Float,
  var bouncesLeft: Int = 0,
  val piercing: Boolean = false,
  val homing: Boolean = false,
  val isRemotePlayerShot: Boolean = false,
  val isRemoteCoopAlly: Boolean = false,
)

enum class PickupType {
  RED_HEART,
  SHIELD_POTION,
  MATS_PACK,
  LOOT_CHEST,
  RELIC_PEDESTAL,
  EXIT_PORTAL,
}

data class Pickup(
  val id: Int,
  val type: PickupType,
  val x: Float,
  val y: Float,
  val rarity: WeaponRarity = WeaponRarity.RARE,
  val relic: RelicType? = null,
  val weapon: WeaponLoadout? = null,
  var bobPhase: Float = 0f,
)

data class Particle(
  var x: Float,
  var y: Float,
  var vx: Float,
  var vy: Float,
  var life: Float,
  val maxLife: Float,
  val size: Float,
  val color: Color,
  val gravity: Float = 450f,
)

data class MeatStain(
  val x: Float,
  val y: Float,
  val width: Float,
  val height: Float,
  val alpha: Float = 0.75f,
)

data class PlayerState(
  var x: Float = 120f,
  var y: Float = 430f,
  val width: Float = 32f,
  val height: Float = 32f,
  var vx: Float = 0f,
  var vy: Float = 0f,
  var facingRight: Boolean = true,
  var onGround: Boolean = false,
  var wallSlideDir: Int = 0, // -1 left wall, 1 right wall, 0 none
  var coyoteTimer: Float = 0f,
  var jumpBufferTimer: Float = 0f,
  var airJumpsLeft: Int = 0,
  var dashTimer: Float = 0f,
  var dashCooldown: Float = 0f,
  var fireCooldown: Float = 0f,
  var invulnTimer: Float = 0f,
  var hp: Int = 6,      // 6 half-hearts = 3 full red hearts
  var maxHp: Int = 6,
  var shield: Int = 2,  // Blue Fortnite/Soul shield points
  val maxShield: Int = 6,
  var mats: Int = 50,   // Fortnite building materials
  var weapon: WeaponLoadout = WeaponLoadout(),
  val relics: MutableList<RelicType> = mutableListOf(),
  var squashX: Float = 1f,
  var squashY: Float = 1f,
)

enum class PlayerSkin(
  val key: String,
  val titleRu: String,
  val subtitleRu: String,
  val price: Int,
  val accentColor: Color,
  val requiresPassword: Boolean = false,
) {
  MEAT_BOY(
    key = "meat_boy",
    titleRu = "Мясной Бой",
    subtitleRu = "Классический герой",
    price = 0,
    accentColor = Color(0xFFFF2E4D),
  ),
  BANANA(
    key = "banana",
    titleRu = "Банан",
    subtitleRu = "Спелый боец Бури",
    price = 10,
    accentColor = Color(0xFFFFD93D),
  ),
  ISAAC(
    key = "isaac",
    titleRu = "Айзек",
    subtitleRu = "Плачущий скиталец",
    price = 20,
    accentColor = Color(0xFFF5C6BA),
  ),
  MR_BEAST(
    key = "mr_beast",
    titleRu = "Mr. Beast",
    subtitleRu = "Король челленджей",
    price = 30,
    accentColor = Color(0xFF00D4FF),
  ),
  OLIVER_200(
    key = "oliver_200",
    titleRu = "Оливер200",
    subtitleRu = "Стиль Оливера Три",
    price = 40,
    accentColor = Color(0xFFFF2E93),
  ),
  CHEKUSHKA(
    key = "chekushka",
    titleRu = "Чекушка",
    subtitleRu = "Душевная №3 (0.25)",
    price = 50,
    accentColor = Color(0xFF3B82F6),
  ),
  MELLSTROY(
    key = "mellstroy",
    titleRu = "Мелстрой",
    subtitleRu = "Кепка назад и золотой бомбер",
    price = 60,
    accentColor = Color(0xFFEAB308),
  ),
  MAMA_PTITSA(
    key = "mama_ptitsa",
    titleRu = "Мама птица",
    subtitleRu = "Цыплёнок-мем с лицом",
    price = 70,
    accentColor = Color(0xFFF4A261),
  ),
  ADMIN(
    key = "admin",
    titleRu = "Админ",
    subtitleRu = "Секретный скин (пароль)",
    price = 0,
    accentColor = Color(0xFF00FFA3),
    requiresPassword = true,
  );

  companion object {
    fun fromKey(key: String?): PlayerSkin = entries.firstOrNull { it.key == key } ?: MEAT_BOY
  }
}

data class RemotePlayerState(
  val uid: String = "",
  val displayName: String = "Игрок",
  val skin: PlayerSkin = PlayerSkin.MEAT_BOY,
  val roomGx: Int = 0,
  val roomGy: Int = 0,
  val x: Float = 120f,
  val y: Float = 420f,
  val vx: Float = 0f,
  val vy: Float = 0f,
  val hp: Int = 6,
  val maxHp: Int = 6,
  val shield: Int = 2,
  val score: Int = 0,
  val facingRight: Boolean = true,
  val alive: Boolean = true,
  val shotSeq: Long = 0L,
  val shotX: Float = 0f,
  val shotY: Float = 0f,
  val shotVx: Float = 0f,
  val shotVy: Float = 0f,
)

data class DungeonRoom(
  val gx: Int,
  val gy: Int,
  val type: RoomType,
  val tiles: Array<Array<TileKind>>,
  val doors: Set<Direction>,
  val saws: MutableList<BuzzSaw> = mutableListOf(),
  val enemies: MutableList<Enemy> = mutableListOf(),
  val pickups: MutableList<Pickup> = mutableListOf(),
  val builds: MutableList<BuildPiece> = mutableListOf(),
  val stains: MutableList<MeatStain> = mutableListOf(),
  var cleared: Boolean = false,
  var visited: Boolean = false,
  var stormTimer: Float = 26f,
  val stormMaxTime: Float = 26f,
  var stormInset: Float = 0f,
) {
  override fun equals(other: Any?): Boolean = other is DungeonRoom && gx == other.gx && gy == other.gy
  override fun hashCode(): Int = gx * 31 + gy
}

data class FloorMap(
  val floorNumber: Int,
  val rooms: Map<Pair<Int, Int>, DungeonRoom>,
  var currentCoord: Pair<Int, Int>,
) {
  val currentRoom: DungeonRoom get() = rooms.getValue(currentCoord)
}

data class InputState(
  val moveX: Float = 0f,
  val jumpPressed: Boolean = false,
  val jumpHeld: Boolean = false,
  val shootHeld: Boolean = false,
  val aimDir: Vec2 = Vec2(0f, 0f),
  val targetWorldPos: Vec2? = null,
  val dashPressed: Boolean = false,
  val buildFloorPressed: Boolean = false,
  val buildWallPressed: Boolean = false,
)

data class LootToast(
  val title: String,
  val subtitle: String,
  val accentColor: Color,
  val timer: Float = 2.6f,
)

data class GameHudSnapshot(
  val floor: Int = 1,
  val score: Int = 0,
  val kills: Int = 0,
  val hp: Int = 6,
  val maxHp: Int = 6,
  val shield: Int = 2,
  val maxShield: Int = 6,
  val mats: Int = 50,
  val buildCost: Int = 10,
  val weapon: WeaponLoadout = WeaponLoadout(),
  val relics: List<RelicType> = emptyList(),
  val roomType: RoomType = RoomType.START,
  val roomCleared: Boolean = true,
  val stormSecondsLeft: Int = 26,
  val stormActive: Boolean = false,
  val bossHpRatio: Float? = null,
  val bossTitle: String? = null,
  val dashReadyFraction: Float = 1f,
  val autoAimEnabled: Boolean = true,
  val coinsEarned: Int = 0,
  val toast: LootToast? = null,
  val minimapRooms: List<MinimapNode> = emptyList(),
  val multiplayerModeLabel: String? = null,
  val remotePlayerName: String? = null,
  val remotePlayerHp: Int = 0,
  val remotePlayerMaxHp: Int = 6,
  val remotePlayerAlive: Boolean = true,
  val multiplayerResultText: String? = null,
)

data class MinimapNode(
  val gx: Int,
  val gy: Int,
  val type: RoomType,
  val visited: Boolean,
  val cleared: Boolean,
  val isCurrent: Boolean,
  val hasRemotePlayer: Boolean = false,
)
