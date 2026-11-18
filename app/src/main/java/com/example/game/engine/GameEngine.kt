package com.example.game.engine

import androidx.compose.ui.graphics.Color
import com.example.audio.SoundEffect
import com.example.game.model.*
import com.example.ui.theme.*
import kotlin.math.*
import kotlin.random.Random

class GameEngine(
  private val onSound: (SoundEffect) -> Unit = {},
  private val onHapticLight: () -> Unit = {},
  private val onHapticHeavy: () -> Unit = {},
  private val onFloorCleared: (floor: Int, coinsAwarded: Int) -> Unit = { _, _ -> },
  private val onGameOver: (score: Int, floor: Int, kills: Int) -> Unit = { _, _, _ -> },
) {
  var floorMap: FloorMap = DungeonGenerator.generateFloor(1)
    private set
  val player = PlayerState()
  val projectiles = mutableListOf<Projectile>()
  val particles = mutableListOf<Particle>()

  var score: Int = 0
    private set
  var kills: Int = 0
    private set
  var screenShake: Float = 0f
    private set
  var isGameOver: Boolean = false
    private set
  var activeToast: LootToast? = null
    private set
  @Volatile var autoAimEnabled: Boolean = true
  @Volatile var playerSkin: PlayerSkin = PlayerSkin.MEAT_BOY
  var coinsEarnedThisRun: Int = 0
    private set
  var lockedEnemyId: Int? = null
    private set
  var manualTargetPos: Vec2? = null
    private set

  private var safeSpawnX: Float = 110f
  private var safeSpawnY: Float = 420f
  private var stormDamageTimer: Float = 0f
  private var doorCooldown: Float = 0f

  var runSeed: Long? = null
    private set
  var multiplayerMode: String? = null
    private set
  @Volatile var remotePlayer: RemotePlayerState? = null
  var multiplayerResultText: String? = null
    private set
  var localShotSeq: Long = 0L
    private set
  var lastShotX: Float = 0f
    private set
  var lastShotY: Float = 0f
    private set
  var lastShotVx: Float = 0f
    private set
  var lastShotVy: Float = 0f
    private set
  private var lastSeenRemoteShotSeq: Long = 0L
  private var pvpVictoryAwarded: Boolean = false

  fun startNewRun(
    seed: Long? = null,
    mpMode: String? = null,
    isMpGuest: Boolean = false,
  ) {
    runSeed = seed
    multiplayerMode = mpMode
    remotePlayer = null
    multiplayerResultText = null
    localShotSeq = 0L
    lastShotX = 0f
    lastShotY = 0f
    lastShotVx = 0f
    lastShotVy = 0f
    lastSeenRemoteShotSeq = 0L
    pvpVictoryAwarded = false
    score = 0
    kills = 0
    coinsEarnedThisRun = 0
    isGameOver = false
    screenShake = 0f
    activeToast = when (mpMode) {
      "coop" -> LootToast(
        title = "КООП-ШТУРМ!",
        subtitle = "Подземелье общее: прикрывайте друг друга и бейте босса!",
        accentColor = TearCyan,
        timer = 3.5f,
      )
      "pvp" -> LootToast(
        title = "ДУЭЛЬ 1 НА 1!",
        subtitle = "Сразите соперника слёзами и прикрывайтесь стройкой!",
        accentColor = MeatCrimson,
        timer = 3.5f,
      )
      else -> LootToast(
        title = "ВЫСАДКА В ПОДЗЕМЕЛЬЕ!",
        subtitle = "Жми ОГОНЬ (авто-наводка) или тапай по экрану, куда стрелять!",
        accentColor = TearCyan,
        timer = 3.5f,
      )
    }
    val rng = if (seed != null) Random(seed + 7919L) else Random.Default
    floorMap = DungeonGenerator.generateFloor(1, rng)
    player.apply {
      x = if (mpMode == "pvp" && isMpGuest) WorldConst.ROOM_WIDTH - 152f else 120f
      y = 420f
      vx = 0f
      vy = 0f
      facingRight = !(mpMode == "pvp" && isMpGuest)
      hp = 6
      maxHp = 6
      shield = 2
      mats = 60
      weapon = WeaponLoadout()
      relics.clear()
      invulnTimer = 0.8f
    }
    safeSpawnX = player.x
    safeSpawnY = player.y
    projectiles.clear()
    particles.clear()
  }

  fun updateRemotePlayerState(next: RemotePlayerState?) {
    if (next == null) {
      remotePlayer = null
      return
    }
    val prev = remotePlayer
    remotePlayer = if (prev != null &&
      prev.roomGx == next.roomGx &&
      prev.roomGy == next.roomGy &&
      hypot(next.x - prev.x, next.y - prev.y) < 180f
    ) {
      next.copy(
        x = prev.x + (next.x - prev.x) * 0.65f,
        y = prev.y + (next.y - prev.y) * 0.65f,
      )
    } else {
      next
    }

    val room = floorMap.currentRoom
    if (next.shotSeq > lastSeenRemoteShotSeq) {
      lastSeenRemoteShotSeq = next.shotSeq
      if (next.roomGx == room.gx && next.roomGy == room.gy && next.alive) {
        val isCoopAlly = multiplayerMode != "pvp"
        projectiles.add(
          Projectile(
            id = DungeonGenerator.nextId(),
            x = next.shotX,
            y = next.shotY,
            vx = next.shotVx,
            vy = next.shotVy,
            radius = 9f,
            damage = if (isCoopAlly) 14f else 1f,
            isPlayer = isCoopAlly,
            life = 1.45f,
            isRemotePlayerShot = true,
            isRemoteCoopAlly = isCoopAlly,
          )
        )
      }
    }

    if (multiplayerMode == "pvp" && !next.alive && !pvpVictoryAwarded && !isGameOver) {
      pvpVictoryAwarded = true
      multiplayerResultText = "ПОБЕДА В ДУЭЛИ!"
      val coinsReward = 10
      coinsEarnedThisRun += coinsReward
      onFloorCleared(floorMap.floorNumber, coinsReward)
      onSound(SoundEffect.CHEST)
      activeToast = LootToast(
        title = "ПОБЕДА! +$coinsReward МОНЕТ",
        subtitle = "${next.displayName} повержен в дуэли!",
        accentColor = LootLegendary,
        timer = 4.0f,
      )
    }
  }

  fun tick(dtRaw: Float, input: InputState) {
    if (isGameOver) return
    val dt = dtRaw.coerceIn(0.001f, 0.035f)
    val room = floorMap.currentRoom

    // Decay timers
    doorCooldown = (doorCooldown - dt).coerceAtLeast(0f)
    screenShake = (screenShake - dt * 28f).coerceAtLeast(0f)
    activeToast?.let { t ->
      val next = t.timer - dt
      activeToast = if (next <= 0f) null else t.copy(timer = next)
    }

    updatePlayer(dt, input, room)
    updateBuilds(dt, room)
    updateSaws(dt, room)
    updateEnemies(dt, room)
    updateProjectiles(dt, room)
    updatePickups(dt, room)
    updateStorm(dt, room)
    updateParticles(dt)
    checkRoomClear(room)
    checkDoorTransitions(room)
  }

  private fun updatePlayer(dt: Float, input: InputState, room: DungeonRoom) {
    player.invulnTimer = (player.invulnTimer - dt).coerceAtLeast(0f)
    player.fireCooldown = (player.fireCooldown - dt).coerceAtLeast(0f)
    player.dashCooldown = (player.dashCooldown - dt).coerceAtLeast(0f)
    player.coyoteTimer = (player.coyoteTimer - dt).coerceAtLeast(0f)
    player.jumpBufferTimer = (player.jumpBufferTimer - dt).coerceAtLeast(0f)

    // Relax squash-and-stretch
    player.squashX += (1f - player.squashX) * (14f * dt).coerceAtMost(1f)
    player.squashY += (1f - player.squashY) * (14f * dt).coerceAtMost(1f)

    if (input.jumpPressed) {
      player.jumpBufferTimer = 0.13f
    }

    if (abs(input.moveX) > 0.12f) {
      player.facingRight = input.moveX > 0f
    }

    // Fortnite Quick-Build
    val buildCost = if (RelicType.TURBO_BUILD in player.relics) 5 else 10
    if (input.buildFloorPressed && player.mats >= buildCost) {
      placeBuildFloor(room, buildCost)
    }
    if (input.buildWallPressed && player.mats >= buildCost) {
      placeBuildWall(room, buildCost)
    }

    // Air Dash
    if (input.dashPressed && player.dashCooldown <= 0f && player.dashTimer <= 0f) {
      player.dashTimer = 0.16f
      player.dashCooldown = 1.05f
      player.invulnTimer = max(player.invulnTimer, 0.18f)
      val dir = if (abs(input.moveX) > 0.15f) sign(input.moveX) else if (player.facingRight) 1f else -1f
      player.vx = dir * 680f
      player.vy = 0f
      player.squashX = 1.38f
      player.squashY = 0.72f
      onSound(SoundEffect.DASH)
      onHapticLight()
      spawnBurst(player.x + player.width / 2f, player.y + player.height / 2f, MeatCrimson, 10, 220f)
    }

    if (player.dashTimer > 0f) {
      player.dashTimer -= dt
      spawnParticle(
        player.x + player.width / 2f,
        player.y + player.height / 2f,
        0f,
        0f,
        0.16f,
        10f,
        MeatCrimson.copy(alpha = 0.5f),
        gravity = 0f,
      )
    } else {
      // Meat Boy horizontal movement
      val maxSpeed = 350f
      val accel = if (player.onGround) 2600f else 1850f
      val targetVx = input.moveX * maxSpeed
      if (abs(input.moveX) > 0.08f) {
        val diff = targetVx - player.vx
        val step = accel * dt
        player.vx += diff.coerceIn(-step, step)
      } else {
        val friction = if (player.onGround) 2400f else 650f
        if (abs(player.vx) <= friction * dt) player.vx = 0f
        else player.vx -= sign(player.vx) * friction * dt
      }

      // Gravity + Wall Slide
      val gravity = 1480f
      player.vy += gravity * dt
      if (player.wallSlideDir != 0 && player.vy > 140f && !player.onGround) {
        player.vy = 140f
        // Meat smear on wall!
        if (Random.nextFloat() < 0.35f) {
          val wx = if (player.wallSlideDir < 0) player.x - 2f else player.x + player.width - 2f
          room.stains.add(MeatStain(wx, player.y + Random.nextFloat() * 18f, 4f, 10f, 0.55f))
          if (room.stains.size > 110) room.stains.removeAt(0)
        }
      }
      if (player.vy > 780f) player.vy = 780f
    }

    // Jump / Wall Jump / Double Jump
    if (player.jumpBufferTimer > 0f) {
      when {
        player.coyoteTimer > 0f -> {
          player.vy = -575f
          player.onGround = false
          player.coyoteTimer = 0f
          player.jumpBufferTimer = 0f
          player.squashX = 0.74f
          player.squashY = 1.32f
          onSound(SoundEffect.JUMP)
          spawnBurst(player.x + player.width / 2f, player.y + player.height, Color.White, 6, 120f)
        }
        player.wallSlideDir != 0 -> {
          player.vy = -550f
          player.vx = -player.wallSlideDir * 390f
          player.facingRight = player.wallSlideDir < 0
          player.wallSlideDir = 0
          player.jumpBufferTimer = 0f
          player.squashX = 0.72f
          player.squashY = 1.34f
          onSound(SoundEffect.WALL_JUMP)
          onHapticLight()
          spawnBurst(player.x + player.width / 2f, player.y + player.height / 2f, MeatCrimson, 8, 160f)
        }
        player.airJumpsLeft > 0 -> {
          player.airJumpsLeft--
          player.vy = -530f
          player.jumpBufferTimer = 0f
          player.squashX = 0.78f
          player.squashY = 1.26f
          onSound(SoundEffect.JUMP)
          spawnBurst(player.x + player.width / 2f, player.y + player.height, LootLegendary, 8, 150f)
        }
      }
    }

    // Variable jump height cut
    if (!input.jumpHeld && player.vy < -220f && player.dashTimer <= 0f) {
      player.vy *= 0.88f
    }

    moveAndCollidePlayer(dt, room)

    // Update Auto-Aim Lock-On target & manual screen-tap target
    val originX = player.x + player.width * 0.5f
    val originY = player.y + player.height * 0.45f
    val nearestEnemy = room.enemies.minByOrNull { e ->
      val dx = (e.x + e.width * 0.5f) - originX
      val dy = (e.y + e.height * 0.5f) - originY
      dx * dx + dy * dy
    }
    lockedEnemyId = nearestEnemy?.id
    manualTargetPos = input.targetWorldPos

    // Shooting: ONLY when pressing/holding Shoot button OR tapping/holding on screen
    val wantsToShoot = input.shootHeld || input.targetWorldPos != null
    if (wantsToShoot && player.fireCooldown <= 0f) {
      firePlayerTears(input, room, nearestEnemy)
    }
  }

  private fun moveAndCollidePlayer(dt: Float, room: DungeonRoom) {
    val prevBottom = player.y + player.height

    // Horizontal
    player.x += player.vx * dt
    player.wallSlideDir = 0
    val leftTile = floor(player.x / WorldConst.TILE_SIZE).toInt()
    val rightTile = floor((player.x + player.width - 0.01f) / WorldConst.TILE_SIZE).toInt()
    val topTile = floor((player.y + 2f) / WorldConst.TILE_SIZE).toInt()
    val botTile = floor((player.y + player.height - 2f) / WorldConst.TILE_SIZE).toInt()

    for (r in topTile..botTile) {
      for (c in leftTile..rightTile) {
        val kind = getTile(room, r, c)
        if (kind == TileKind.SOLID || kind == TileKind.CRATE) {
          val tx = c * WorldConst.TILE_SIZE
          if (player.vx > 0f) {
            player.x = tx - player.width
            player.vx = 0f
            player.wallSlideDir = 1
          } else if (player.vx < 0f) {
            player.x = tx + WorldConst.TILE_SIZE
            player.vx = 0f
            player.wallSlideDir = -1
          }
        }
      }
    }

    // Also collide horizontally with player-built walls
    for (b in room.builds) {
      if (b.isWall && rectsOverlap(player.x, player.y, player.width, player.height, b.x, b.y, b.width, b.height)) {
        if (player.vx > 0f) {
          player.x = b.x - player.width
          player.vx = 0f
          player.wallSlideDir = 1
        } else if (player.vx < 0f) {
          player.x = b.x + b.width
          player.vx = 0f
          player.wallSlideDir = -1
        }
      }
    }

    // Clamp to room edges except open cleared doors
    player.x = player.x.coerceIn(0f, WorldConst.ROOM_WIDTH - player.width)

    // Vertical
    val wasOnGround = player.onGround
    player.onGround = false
    player.y += player.vy * dt
    val lCol = floor((player.x + 2f) / WorldConst.TILE_SIZE).toInt()
    val rCol = floor((player.x + player.width - 2f) / WorldConst.TILE_SIZE).toInt()
    val tRow = floor(player.y / WorldConst.TILE_SIZE).toInt()
    val bRow = floor((player.y + player.height - 0.01f) / WorldConst.TILE_SIZE).toInt()

    for (r in tRow..bRow) {
      for (c in lCol..rCol) {
        val kind = getTile(room, r, c)
        val ty = r * WorldConst.TILE_SIZE
        when (kind) {
          TileKind.SOLID, TileKind.CRATE -> {
            if (player.vy > 0f) {
              player.y = ty - player.height
              player.vy = 0f
              player.onGround = true
            } else if (player.vy < 0f) {
              player.y = ty + WorldConst.TILE_SIZE
              player.vy = 0f
            }
          }
          TileKind.ONE_WAY -> {
            if (player.vy >= 0f && prevBottom <= ty + 10f) {
              player.y = ty - player.height
              player.vy = 0f
              player.onGround = true
            }
          }
          TileKind.SPIKE -> {
            val kVx = if (player.vx != 0f) -sign(player.vx) * 200f else 0f
            damagePlayer(1, knockVx = kVx, knockVy = -380f)
          }
          TileKind.EMPTY -> Unit
        }
      }
    }

    // Built floors (act as solid one-way platforms)
    for (b in room.builds) {
      if (!b.isWall && player.vy >= 0f && prevBottom <= b.y + 12f &&
        rectsOverlap(player.x, player.y, player.width, player.height, b.x, b.y, b.width, b.height)
      ) {
        player.y = b.y - player.height
        player.vy = 0f
        player.onGround = true
      }
    }

    if (player.onGround) {
      if (!wasOnGround) {
        player.squashX = 1.25f
        player.squashY = 0.78f
        room.stains.add(MeatStain(player.x + 4f, player.y + player.height - 3f, 22f, 4f, 0.5f))
        if (room.stains.size > 110) room.stains.removeAt(0)
      }
      player.coyoteTimer = 0.11f
      player.airJumpsLeft = if (RelicType.DOUBLE_WINGS in player.relics) 1 else 0
    }
  }

  private fun placeBuildFloor(room: DungeonRoom, cost: Int) {
    val isMetal = RelicType.TURBO_BUILD in player.relics
    val bw = 82f
    val bh = 16f
    val offsetX = if (player.facingRight) 10f else -bw + player.width - 10f
    val bx = (player.x + offsetX).coerceIn(WorldConst.TILE_SIZE, WorldConst.ROOM_WIDTH - WorldConst.TILE_SIZE - bw)
    val by = (player.y + player.height + 2f).coerceIn(WorldConst.TILE_SIZE * 2f, WorldConst.ROOM_HEIGHT - WorldConst.TILE_SIZE - bh)
    player.mats -= cost
    room.builds.add(
      BuildPiece(
        id = DungeonGenerator.nextId(),
        x = bx,
        y = by,
        width = bw,
        height = bh,
        isWall = false,
        isMetal = isMetal,
        hp = if (isMetal) 8 else 4,
        maxHp = if (isMetal) 8 else 4,
      )
    )
    if (player.vy > 0f) player.vy = 0f
    onSound(SoundEffect.BUILD)
    onHapticLight()
    spawnBurst(bx + bw / 2f, by + bh / 2f, if (isMetal) BuildMetal else BuildWood, 8, 140f)
  }

  private fun placeBuildWall(room: DungeonRoom, cost: Int) {
    val isMetal = RelicType.TURBO_BUILD in player.relics
    val bw = 18f
    val bh = 86f
    val bx = if (player.facingRight) player.x + player.width + 8f else player.x - bw - 8f
    val clampedX = bx.coerceIn(WorldConst.TILE_SIZE, WorldConst.ROOM_WIDTH - WorldConst.TILE_SIZE - bw)
    val by = (player.y - 26f).coerceIn(WorldConst.TILE_SIZE, WorldConst.ROOM_HEIGHT - WorldConst.TILE_SIZE - bh)
    player.mats -= cost
    room.builds.add(
      BuildPiece(
        id = DungeonGenerator.nextId(),
        x = clampedX,
        y = by,
        width = bw,
        height = bh,
        isWall = true,
        isMetal = isMetal,
        hp = if (isMetal) 10 else 5,
        maxHp = if (isMetal) 10 else 5,
      )
    )
    onSound(SoundEffect.BUILD)
    onHapticLight()
    spawnBurst(clampedX + bw / 2f, by + bh / 2f, if (isMetal) BuildMetal else BuildWood, 8, 140f)
  }

  private fun firePlayerTears(input: InputState, room: DungeonRoom, nearest: Enemy?) {
    val loadout = player.weapon
    player.fireCooldown = loadout.fireInterval
    val originX = player.x + player.width / 2f
    val originY = player.y + player.height * 0.45f

    // Priority: 1) Screen-tap world target  2) Auto-Aim locked enemy (if enabled)  3) Aim stick  4) Facing dir
    var ax: Float
    var ay: Float
    var directAim = false
    var usedAutoAim = false
    val tapTarget = input.targetWorldPos
    if (tapTarget != null) {
      directAim = true
      ax = tapTarget.x - originX
      ay = tapTarget.y - originY
      if (abs(ax) > 4f) player.facingRight = ax >= 0f
    } else if (autoAimEnabled && nearest != null) {
      directAim = true
      usedAutoAim = true
      val ex = nearest.x + nearest.width / 2f
      val ey = nearest.y + nearest.height / 2f
      val dist = hypot(ex - originX, ey - originY)
      val leadT = (dist / loadout.type.tearSpeed).coerceAtMost(0.28f)
      ax = (ex + nearest.vx * leadT * 0.35f) - originX
      ay = (ey + nearest.vy * leadT * 0.15f) - originY
      if (abs(ax) > 4f) player.facingRight = ax >= 0f
    } else if (hypot(input.aimDir.x, input.aimDir.y) >= 0.2f) {
      ax = input.aimDir.x
      ay = input.aimDir.y
    } else {
      ax = if (player.facingRight) 1f else -1f
      ay = 0f
    }
    if (hypot(ax, ay) < 0.01f) {
      ax = if (player.facingRight) 1f else -1f
      ay = 0f
    }
    val baseAngle = atan2(ay, ax)
    val bounces = if (RelicType.RUBBER_BOUNCE in player.relics) 2 else 0
    val homing = (RelicType.HOMING_EYE in player.relics) || usedAutoAim
    val mutantCount = if (RelicType.MUTANT_SPLIT in player.relics) 3 else 1

    val angles = mutableListOf<Float>()
    when (loadout.type) {
      WeaponType.TACTICAL_SHOTGUN -> {
        for (i in -1..2) angles.add(baseAngle + (i - 0.5f) * 0.14f)
      }
      else -> {
        if (mutantCount == 3) {
          angles.add(baseAngle - 0.16f)
          angles.add(baseAngle)
          angles.add(baseAngle + 0.16f)
        } else {
          angles.add(baseAngle)
        }
      }
    }

    val speed = loadout.type.tearSpeed
    val piercing = loadout.type == WeaponType.BRIM_SNIPER
    val inheritX = if (directAim) 0f else player.vx * 0.22f
    val inheritY = if (directAim) 0f else player.vy * 0.10f
    var recordedShot = false
    for (ang in angles) {
      val vx = cos(ang) * speed + inheritX
      val vy = sin(ang) * speed + inheritY
      if (!recordedShot) {
        recordedShot = true
        localShotSeq += 1L
        lastShotX = originX
        lastShotY = originY
        lastShotVx = vx
        lastShotVy = vy
      }
      projectiles.add(
        Projectile(
          id = DungeonGenerator.nextId(),
          x = originX,
          y = originY,
          vx = vx,
          vy = vy,
          radius = loadout.type.tearRadius,
          damage = loadout.damage,
          isPlayer = true,
          life = if (loadout.type == WeaponType.TACTICAL_SHOTGUN) 0.65f else 1.45f,
          bouncesLeft = bounces,
          piercing = piercing,
          homing = homing,
        )
      )
    }
    onSound(SoundEffect.SHOOT)
  }

  private fun updateBuilds(dt: Float, room: DungeonRoom) {
    room.builds.forEach { b ->
      if (b.animProgress < 1f) b.animProgress = (b.animProgress + dt * 8f).coerceAtMost(1f)
    }
    room.builds.removeAll { it.hp <= 0 }
  }

  private fun updateSaws(dt: Float, room: DungeonRoom) {
    val px = player.x + player.width / 2f
    val py = player.y + player.height / 2f
    for (saw in room.saws) {
      saw.angle += dt * 420f
      val t = (sin(saw.phase + System.nanoTime() / 1_000_000_000.0 * saw.speed).toFloat() + 1f) * 0.5f
      // Deterministic progress using angle-based timer
      val prog = (sin((saw.angle / 220f) * saw.speed + saw.phase) + 1f) * 0.5f
      saw.x = saw.startX + (saw.endX - saw.startX) * prog
      saw.y = saw.startY + (saw.endY - saw.startY) * prog
      val dist = hypot(px - saw.x, py - saw.y)
      if (dist < saw.radius + 13f) {
        damagePlayer(1, knockVx = sign(px - saw.x) * 260f, knockVy = -360f)
      }
    }
  }

  private fun updateEnemies(dt: Float, room: DungeonRoom) {
    val px = player.x + player.width / 2f
    val py = player.y + player.height / 2f
    val spawnedMinions = mutableListOf<Enemy>()
    val iterator = room.enemies.iterator()
    while (iterator.hasNext()) {
      val e = iterator.next()
      e.hitFlash = (e.hitFlash - dt).coerceAtLeast(0f)
      e.aiTimer -= dt
      e.moveTimer += dt

      val ex = e.x + e.width / 2f
      val ey = e.y + e.height / 2f
      val dx = px - ex
      val dy = py - ey
      val dirX = if (dx >= 0f) 1f else -1f
      e.facingRight = dx >= 0f

      when (e.type) {
        EnemyType.MEAT_HOPPER -> {
          if (e.aiTimer <= 0f && e.onGround) {
            e.aiTimer = 0.85f + Random.nextFloat() * 0.55f
            e.vy = -430f
            e.vx = dirX * (165f + Random.nextFloat() * 65f)
            e.onGround = false
          }
          e.vy += 1200f * dt
          moveEnemyWithTiles(e, dt, room)
        }
        EnemyType.BLOOD_FLY -> {
          val dist = hypot(dx, dy).coerceAtLeast(1f)
          val wobble = sin(e.moveTimer * 7f) * 60f
          e.vx = (dx / dist) * 145f - (dy / dist) * wobble * 0.3f
          e.vy = (dy / dist) * 145f + (dx / dist) * wobble * 0.3f
          e.x = (e.x + e.vx * dt).coerceIn(WorldConst.TILE_SIZE, WorldConst.ROOM_WIDTH - WorldConst.TILE_SIZE - e.width)
          e.y = (e.y + e.vy * dt).coerceIn(WorldConst.TILE_SIZE, WorldConst.ROOM_HEIGHT - WorldConst.TILE_SIZE - e.height)
        }
        EnemyType.SAW_TURRET -> {
          if (e.aiTimer <= 0f) {
            e.aiTimer = 1.65f
            val ang = atan2(dy, dx)
            for (spread in -1..1) {
              val a = ang + spread * 0.22f
              projectiles.add(
                Projectile(
                  id = DungeonGenerator.nextId(),
                  x = ex,
                  y = ey,
                  vx = cos(a) * 280f,
                  vy = sin(a) * 280f,
                  radius = 8f,
                  damage = 1f,
                  isPlayer = false,
                  life = 2.6f,
                )
              )
            }
          }
        }
        EnemyType.BOSS_STORM_MONSTRO -> {
          val wasGrounded = e.onGround
          if (e.aiTimer <= 0f && e.onGround) {
            e.aiTimer = 1.35f
            e.vy = -475f
            e.vx = dirX * 215f
            e.onGround = false
            onSound(SoundEffect.BOSS_ROAR)
            screenShake = 8f
            val baseA = atan2(dy, dx)
            for (k in -3..3) {
              val a = baseA + k * 0.23f
              projectiles.add(
                Projectile(
                  id = DungeonGenerator.nextId(),
                  x = ex,
                  y = ey,
                  vx = cos(a) * 320f,
                  vy = sin(a) * 320f,
                  radius = 10f,
                  damage = 1f,
                  isPlayer = false,
                  life = 2.8f,
                )
              )
            }
          }
          e.vy += 1150f * dt
          moveEnemyWithTiles(e, dt, room)
          // Slam shockwave on landing
          if (!wasGrounded && e.onGround) {
            screenShake = 7f
            spawnBurst(ex, e.y + e.height, MeatCrimson, 14, 240f)
            val slamAngles = listOf(0f, PI.toFloat(), -0.35f, -PI.toFloat() + 0.35f)
            for (a in slamAngles) {
              projectiles.add(
                Projectile(
                  id = DungeonGenerator.nextId(),
                  x = ex,
                  y = e.y + e.height * 0.65f,
                  vx = cos(a) * 290f,
                  vy = sin(a) * 290f,
                  radius = 9f,
                  damage = 1f,
                  isPlayer = false,
                  life = 2.0f,
                )
              )
            }
          }
        }
        EnemyType.BOSS_DUKE_OF_MEAT -> {
          val targetY = (py - 90f).coerceIn(WorldConst.TILE_SIZE * 1.4f, WorldConst.ROOM_HEIGHT * 0.66f)
          val dist = hypot(dx, targetY - ey).coerceAtLeast(1f)
          if (e.aiPhase == 1) {
            // Dash burst toward player
            if (e.aiTimer <= 0f) {
              e.aiPhase = 0
              e.aiTimer = 1.35f
            }
          } else {
            // Hover pursuit with figure-8 swoop
            e.vx = (dx / dist) * 145f + cos(e.moveTimer * 2.4f) * 55f
            e.vy = ((targetY - ey) / dist) * 125f + sin(e.moveTimer * 3.6f) * 65f
            if (e.aiTimer <= 0f) {
              e.aiTimer = 1.55f
              val activeFlies = room.enemies.count { it.type == EnemyType.BLOOD_FLY } + spawnedMinions.size
              if (activeFlies < 2 && Random.nextFloat() < 0.45f) {
                onSound(SoundEffect.BOSS_ROAR)
                spawnedMinions.add(
                  Enemy(
                    id = DungeonGenerator.nextId(),
                    type = EnemyType.BLOOD_FLY,
                    x = ex - 15f,
                    y = ey - 13f,
                    width = 30f,
                    height = 26f,
                    hp = 24f + floorMap.floorNumber * 6f,
                    maxHp = 24f + floorMap.floorNumber * 6f,
                  )
                )
                val baseA = atan2(dy, dx)
                for (s in -1..1) {
                  val a = baseA + s * 0.25f
                  projectiles.add(
                    Projectile(
                      id = DungeonGenerator.nextId(),
                      x = ex,
                      y = ey,
                      vx = cos(a) * 300f,
                      vy = sin(a) * 300f,
                      radius = 9f,
                      damage = 1f,
                      isPlayer = false,
                      life = 2.6f,
                    )
                  )
                }
              } else {
                // Winged Dash + 8-way radial tear burst!
                e.aiPhase = 1
                e.aiTimer = 0.45f
                val dLen = hypot(dx, dy).coerceAtLeast(1f)
                e.vx = (dx / dLen) * 390f
                e.vy = (dy / dLen) * 280f
                onSound(SoundEffect.BOSS_ROAR)
                for (k in 0 until 8) {
                  val a = (k * (2f * PI / 8f)).toFloat()
                  projectiles.add(
                    Projectile(
                      id = DungeonGenerator.nextId(),
                      x = ex,
                      y = ey,
                      vx = cos(a) * 270f,
                      vy = sin(a) * 270f,
                      radius = 9f,
                      damage = 1f,
                      isPlayer = false,
                      life = 2.5f,
                    )
                  )
                }
              }
            }
          }
          e.x = (e.x + e.vx * dt).coerceIn(WorldConst.TILE_SIZE, WorldConst.ROOM_WIDTH - WorldConst.TILE_SIZE - e.width)
          e.y = (e.y + e.vy * dt).coerceIn(WorldConst.TILE_SIZE, WorldConst.ROOM_HEIGHT - WorldConst.TILE_SIZE - e.height)
        }
        EnemyType.BOSS_SAW_MECH -> {
          // Rolls aggressively across the arena & leaps periodically while firing rapid triple volleys
          val desiredVx = dirX * 210f
          e.vx += (desiredVx - e.vx) * (3.5f * dt).coerceAtMost(1f)
          if (e.aiTimer <= 0f) {
            e.aiTimer = 1.25f
            e.aiPhase = (e.aiPhase + 1) % 3
            if (e.aiPhase == 0 && e.onGround) {
              e.vy = -450f
              e.onGround = false
            }
            onSound(SoundEffect.BOSS_ROAR)
            val baseA = atan2(dy, dx)
            for (k in -1..1) {
              val a = baseA + k * 0.16f
              projectiles.add(
                Projectile(
                  id = DungeonGenerator.nextId(),
                  x = ex,
                  y = ey - 8f,
                  vx = cos(a) * 365f,
                  vy = sin(a) * 365f,
                  radius = 9f,
                  damage = 1f,
                  isPlayer = false,
                  life = 2.6f,
                )
              )
            }
            // Twin bouncing-style horizontal saw sparks
            projectiles.add(
              Projectile(
                id = DungeonGenerator.nextId(),
                x = ex,
                y = ey + 10f,
                vx = -310f,
                vy = -90f,
                radius = 8f,
                damage = 1f,
                isPlayer = false,
                life = 2.2f,
              )
            )
            projectiles.add(
              Projectile(
                id = DungeonGenerator.nextId(),
                x = ex,
                y = ey + 10f,
                vx = 310f,
                vy = -90f,
                radius = 8f,
                damage = 1f,
                isPlayer = false,
                life = 2.2f,
              )
            )
          }
          e.vy += 1200f * dt
          moveEnemyWithTiles(e, dt, room, dampVxOnLand = false)
        }
        EnemyType.BOSS_STORM_CUBE -> {
          // Levitates & strafes smoothly across upper/mid arena, firing rotating storm spirals + aimed bursts
          val orbitX = WorldConst.ROOM_WIDTH * 0.5f + sin(e.moveTimer * 1.15f) * (WorldConst.ROOM_WIDTH * 0.30f) - e.width * 0.5f
          val orbitY = WorldConst.ROOM_HEIGHT * 0.38f + cos(e.moveTimer * 1.7f) * 95f - e.height * 0.5f
          e.vx = (orbitX - e.x) * 3.0f
          e.vy = (orbitY - e.y) * 3.0f
          e.x = (e.x + e.vx * dt).coerceIn(WorldConst.TILE_SIZE, WorldConst.ROOM_WIDTH - WorldConst.TILE_SIZE - e.width)
          e.y = (e.y + e.vy * dt).coerceIn(WorldConst.TILE_SIZE, WorldConst.ROOM_HEIGHT - WorldConst.TILE_SIZE - e.height)
          if (e.aiTimer <= 0f) {
            e.aiTimer = 1.15f
            e.aiPhase++
            onSound(SoundEffect.BOSS_ROAR)
            val rotOffset = e.aiPhase * 0.42f
            for (k in 0 until 6) {
              val a = rotOffset + k * ((2f * PI.toFloat()) / 6f)
              projectiles.add(
                Projectile(
                  id = DungeonGenerator.nextId(),
                  x = ex,
                  y = ey,
                  vx = cos(a) * 285f,
                  vy = sin(a) * 285f,
                  radius = 9f,
                  damage = 1f,
                  isPlayer = false,
                  life = 2.8f,
                )
              )
            }
            // Also fire 2 fast aimed storm bolts directly at the player
            val aimA = atan2(dy, dx)
            for (s in listOf(-0.08f, 0.08f)) {
              val a = aimA + s
              projectiles.add(
                Projectile(
                  id = DungeonGenerator.nextId(),
                  x = ex,
                  y = ey,
                  vx = cos(a) * 380f,
                  vy = sin(a) * 380f,
                  radius = 8f,
                  damage = 1f,
                  isPlayer = false,
                  life = 2.4f,
                )
              )
            }
          }
        }
      }

      if (rectsOverlap(player.x, player.y, player.width, player.height, e.x, e.y, e.width, e.height)) {
        damagePlayer(1, knockVx = sign(px - ex) * 240f, knockVy = -300f)
      }

      if (e.hp <= 0f) {
        iterator.remove()
        handleEnemyDeath(e, room)
      }
    }
    if (spawnedMinions.isNotEmpty()) {
      room.enemies.addAll(spawnedMinions)
    }
  }

  private fun moveEnemyWithTiles(e: Enemy, dt: Float, room: DungeonRoom, dampVxOnLand: Boolean = true) {
    val minX = WorldConst.TILE_SIZE
    val maxX = WorldConst.ROOM_WIDTH - WorldConst.TILE_SIZE - e.width
    val minY = WorldConst.TILE_SIZE
    val maxY = WorldConst.ROOM_HEIGHT - WorldConst.TILE_SIZE - e.height

    e.x = (e.x + e.vx * dt).coerceIn(minX, maxX)
    if (e.x <= minX || e.x >= maxX) {
      e.vx = -e.vx * if (dampVxOnLand) 0.4f else 0.85f
    }

    val prevBottom = e.y + e.height
    e.y += e.vy * dt
    e.onGround = false

    val lCol = floor((e.x + 2f) / WorldConst.TILE_SIZE).toInt()
    val rCol = floor((e.x + e.width - 2f) / WorldConst.TILE_SIZE).toInt()

    if (e.vy < 0f) {
      val tRow = floor(e.y / WorldConst.TILE_SIZE).toInt()
      for (c in lCol..rCol) {
        if (getTile(room, tRow, c) == TileKind.SOLID) {
          e.y = (tRow + 1) * WorldConst.TILE_SIZE
          e.vy = 0f
          break
        }
      }
    } else if (e.vy >= 0f) {
      val bRow = floor((e.y + e.height) / WorldConst.TILE_SIZE).toInt()
      for (c in lCol..rCol) {
        val kind = getTile(room, bRow, c)
        val ty = bRow * WorldConst.TILE_SIZE
        val hitFloor = kind == TileKind.SOLID || kind == TileKind.CRATE ||
          (kind == TileKind.ONE_WAY && prevBottom <= ty + 14f)
        if (hitFloor && e.y + e.height >= ty) {
          e.y = ty - e.height
          e.vy = 0f
          e.onGround = true
          if (dampVxOnLand) e.vx *= 0.78f
          break
        }
      }
    }

    // Strict arena boundary clamp so Boss or enemies can NEVER escape the map
    if (e.y < minY) {
      e.y = minY
      if (e.vy < 0f) e.vy = 0f
    } else if (e.y >= maxY) {
      e.y = maxY
      if (e.vy >= 0f) {
        e.vy = 0f
        e.onGround = true
        if (dampVxOnLand) e.vx *= 0.78f
      }
    }
  }

  private fun handleEnemyDeath(e: Enemy, room: DungeonRoom) {
    kills++
    val isBoss = e.type.isBoss
    score += if (isBoss) 500 else 65
    screenShake = if (isBoss) 16f else 5f
    onSound(SoundEffect.SPLAT)
    spawnBurst(e.x + e.width / 2f, e.y + e.height / 2f, MeatCrimson, if (isBoss) 32 else 14, 280f)
    room.stains.add(MeatStain(e.x, e.y + e.height - 6f, e.width, 8f, 0.7f))

    // Drop Fortnite Mats & chance for Heart/Shield
    room.pickups.add(
      Pickup(id = DungeonGenerator.nextId(), type = PickupType.MATS_PACK, x = e.x + e.width / 2f, y = e.y + e.height / 2f)
    )
    if (RelicType.MEAT_VAMPIRISM in player.relics && Random.nextFloat() < 0.35f) {
      room.pickups.add(Pickup(id = DungeonGenerator.nextId(), type = PickupType.RED_HEART, x = e.x + 10f, y = e.y))
    } else if (Random.nextFloat() < 0.25f) {
      val pType = if (Random.nextBoolean()) PickupType.RED_HEART else PickupType.SHIELD_POTION
      room.pickups.add(Pickup(id = DungeonGenerator.nextId(), type = pType, x = e.x + 12f, y = e.y))
    }

    if (isBoss) {
      val relic = RelicType.entries[Random.nextInt(RelicType.entries.size)]
      room.pickups.add(
        Pickup(
          id = DungeonGenerator.nextId(),
          type = PickupType.RELIC_PEDESTAL,
          x = WorldConst.ROOM_WIDTH * 0.38f,
          y = 9.8f * WorldConst.TILE_SIZE,
          relic = relic,
        )
      )
      room.pickups.add(
        Pickup(
          id = DungeonGenerator.nextId(),
          type = PickupType.EXIT_PORTAL,
          x = WorldConst.ROOM_WIDTH * 0.62f,
          y = 9.7f * WorldConst.TILE_SIZE,
        )
      )
    }
  }

  private fun updateProjectiles(dt: Float, room: DungeonRoom) {
    val iter = projectiles.iterator()
    while (iter.hasNext()) {
      val p = iter.next()
      p.life -= dt
      if (p.life <= 0f) {
        iter.remove()
        continue
      }

      if (p.isPlayer && p.homing && room.enemies.isNotEmpty()) {
        val target = room.enemies.minByOrNull { hypot((it.x + it.width / 2f) - p.x, (it.y + it.height / 2f) - p.y) }
        if (target != null) {
          val dx = (target.x + target.width / 2f) - p.x
          val dy = (target.y + target.height / 2f) - p.y
          val dist = hypot(dx, dy).coerceAtLeast(1f)
          val speed = hypot(p.vx, p.vy)
          val steer = 12f * dt
          val nx = p.vx / speed + (dx / dist) * steer
          val ny = p.vy / speed + (dy / dist) * steer
          val nLen = hypot(nx, ny).coerceAtLeast(0.01f)
          p.vx = (nx / nLen) * speed
          p.vy = (ny / nLen) * speed
        }
      }

      p.x += p.vx * dt
      p.y += p.vy * dt

      // Tile collision (also break Crates for Mats!)
      val col = floor(p.x / WorldConst.TILE_SIZE).toInt()
      val row = floor(p.y / WorldConst.TILE_SIZE).toInt()
      val tile = getTile(room, row, col)
      if (tile == TileKind.CRATE && p.isPlayer) {
        room.tiles[row][col] = TileKind.EMPTY
        player.mats += 15
        score += 15
        spawnBurst(col * WorldConst.TILE_SIZE + 24f, row * WorldConst.TILE_SIZE + 24f, BuildWood, 10, 180f)
        if (!p.piercing) {
          iter.remove()
          continue
        }
      } else if (tile == TileKind.SOLID) {
        if (p.bouncesLeft > 0) {
          p.bouncesLeft--
          p.vx = -p.vx
          p.vy = -p.vy * 0.85f
        } else {
          spawnBurst(p.x, p.y, if (p.isPlayer) TearCyan else MeatCrimson, 4, 100f)
          iter.remove()
          continue
        }
      }

      // Enemy projectiles blocked by Fortnite built walls!
      if (!p.isPlayer) {
        val hitBuild = room.builds.firstOrNull { b ->
          p.x in b.x..(b.x + b.width) && p.y in b.y..(b.y + b.height)
        }
        if (hitBuild != null) {
          hitBuild.hp--
          spawnBurst(p.x, p.y, BuildWood, 5, 110f)
          iter.remove()
          continue
        }
        if (rectsOverlap(p.x - p.radius, p.y - p.radius, p.radius * 2, p.radius * 2, player.x, player.y, player.width, player.height)) {
          damagePlayer(1, knockVx = sign(p.vx) * 180f, knockVy = -220f)
          iter.remove()
          continue
        }
      } else {
        // Player tear vs Enemies
        var removed = false
        for (e in room.enemies) {
          if (rectsOverlap(p.x - p.radius, p.y - p.radius, p.radius * 2, p.radius * 2, e.x, e.y, e.width, e.height)) {
            e.hp -= p.damage
            e.hitFlash = 0.12f
            onSound(SoundEffect.HIT)
            spawnBurst(p.x, p.y, if (p.isRemoteCoopAlly) Color(0xFF00FFA3) else TearCyan, 6, 150f)
            if (!p.piercing) {
              iter.remove()
              removed = true
              break
            }
          }
        }
        if (removed) continue

        // PvP hit feedback when local player tear strikes remote opponent in same room
        val rp = remotePlayer
        if (!p.isRemotePlayerShot && multiplayerMode == "pvp" && rp != null && rp.alive &&
          rp.roomGx == room.gx && rp.roomGy == room.gy &&
          rectsOverlap(p.x - p.radius, p.y - p.radius, p.radius * 2, p.radius * 2, rp.x, rp.y, 32f, 32f)
        ) {
          score += 25
          onSound(SoundEffect.HIT)
          spawnBurst(p.x, p.y, LootLegendary, 8, 170f)
          if (!p.piercing) {
            iter.remove()
            continue
          }
        }
      }
    }
  }

  private fun updatePickups(dt: Float, room: DungeonRoom) {
    val iter = room.pickups.iterator()
    while (iter.hasNext()) {
      val pk = iter.next()
      pk.bobPhase += dt * 4f
      val hit = rectsOverlap(player.x, player.y, player.width, player.height, pk.x - 20f, pk.y - 20f, 40f, 40f)
      if (!hit) continue

      when (pk.type) {
        PickupType.RED_HEART -> {
          if (player.hp < player.maxHp) {
            player.hp = (player.hp + 2).coerceAtMost(player.maxHp)
            onSound(SoundEffect.CHEST)
            spawnBurst(pk.x, pk.y, MeatCrimson, 10, 140f)
            iter.remove()
          }
        }
        PickupType.SHIELD_POTION -> {
          if (player.shield < player.maxShield) {
            player.shield = (player.shield + 2).coerceAtMost(player.maxShield)
            onSound(SoundEffect.CHEST)
            spawnBurst(pk.x, pk.y, ShieldBlue, 10, 140f)
            iter.remove()
          }
        }
        PickupType.MATS_PACK -> {
          player.mats += 20
          score += 10
          spawnBurst(pk.x, pk.y, BuildWood, 7, 110f)
          iter.remove()
        }
        PickupType.LOOT_CHEST -> {
          val w = pk.weapon ?: WeaponLoadout(WeaponType.RAPID_SMG, pk.rarity)
          player.weapon = w
          player.mats += 30
          score += 120
          onSound(SoundEffect.CHEST)
          onHapticHeavy()
          activeToast = LootToast(
            title = "${w.rarity.labelRu}: ${w.type.titleRu}",
            subtitle = "Урон x${w.rarity.dmgMult} • Скорость x${w.rarity.fireRateMult} • +30 мат.",
            accentColor = w.rarity.color,
          )
          spawnBurst(pk.x, pk.y, w.rarity.color, 20, 220f)
          iter.remove()
        }
        PickupType.RELIC_PEDESTAL -> {
          val relic = pk.relic ?: RelicType.MUTANT_SPLIT
          if (relic !in player.relics) {
            player.relics.add(relic)
            if (relic == RelicType.CHUG_SHIELD) {
              player.shield = (player.shield + 2).coerceAtMost(player.maxShield)
            }
          } else {
            player.maxHp += 2
            player.hp += 2
          }
          score += 150
          onSound(SoundEffect.CHEST)
          onHapticHeavy()
          activeToast = LootToast(
            title = "РЕЛИКВИЯ: ${relic.titleRu}",
            subtitle = relic.descRu,
            accentColor = relic.badgeColor,
          )
          spawnBurst(pk.x, pk.y, relic.badgeColor, 20, 220f)
          iter.remove()
        }
        PickupType.EXIT_PORTAL -> {
          advanceToNextFloor()
          return
        }
      }
    }
  }

  private fun advanceToNextFloor() {
    val clearedFloor = floorMap.floorNumber
    val coinsReward = 10
    coinsEarnedThisRun += coinsReward
    onFloorCleared(clearedFloor, coinsReward)

    val nextFloor = clearedFloor + 1
    score += 300
    val nextRng = runSeed?.let { Random(it + nextFloor * 7919L) } ?: Random.Default
    floorMap = DungeonGenerator.generateFloor(nextFloor, nextRng)
    player.x = 120f
    player.y = 420f
    player.vx = 0f
    player.vy = 0f
    player.invulnTimer = 1f
    safeSpawnX = player.x
    safeSpawnY = player.y
    projectiles.clear()
    particles.clear()
    onSound(SoundEffect.CHEST)
    activeToast = LootToast(
      title = "ЭТАЖ $clearedFloor ПРОЙДЕН! +$coinsReward МОНЕТ",
      subtitle = "Этаж $nextFloor: враги опаснее, но и лут круче!",
      accentColor = LootLegendary,
    )
  }

  private fun updateStorm(dt: Float, room: DungeonRoom) {
    if (room.cleared) {
      room.stormInset = (room.stormInset - dt * 160f).coerceAtLeast(0f)
      return
    }
    if (room.stormTimer > 0f) {
      room.stormTimer = (room.stormTimer - dt).coerceAtLeast(0f)
    } else {
      room.stormInset = (room.stormInset + dt * 28f).coerceAtMost(WorldConst.ROOM_WIDTH * 0.34f)
      val px = player.x + player.width / 2f
      if (px < room.stormInset || px > WorldConst.ROOM_WIDTH - room.stormInset) {
        stormDamageTimer += dt
        if (stormDamageTimer >= 1.1f) {
          stormDamageTimer = 0f
          damagePlayer(1, knockVx = if (px < room.stormInset) 220f else -220f, knockVy = -180f)
        }
      }
    }
  }

  private fun checkRoomClear(room: DungeonRoom) {
    if (!room.cleared && room.enemies.isEmpty()) {
      room.cleared = true
      score += 100
      onSound(SoundEffect.CHEST)
      // Drop Fortnite supply chest on cleared combat room sometimes
      if (room.type == RoomType.COMBAT && Random.nextFloat() < 0.55f) {
        val rarity = listOf(WeaponRarity.COMMON, WeaponRarity.RARE, WeaponRarity.EPIC).random()
        val wType = WeaponType.entries.random()
        room.pickups.add(
          Pickup(
            id = DungeonGenerator.nextId(),
            type = PickupType.LOOT_CHEST,
            x = 6.2f * WorldConst.TILE_SIZE,
            y = 10.1f * WorldConst.TILE_SIZE,
            rarity = rarity,
            weapon = WeaponLoadout(wType, rarity),
          )
        )
      }
    }
  }

  private fun checkDoorTransitions(room: DungeonRoom) {
    if (!room.cleared || doorCooldown > 0f) return
    val cx = player.x + player.width / 2f
    val cy = player.y + player.height / 2f

    val dir: Direction? = when {
      Direction.WEST in room.doors && player.x <= 10f && cy > WorldConst.TILE_SIZE * 7f -> Direction.WEST
      Direction.EAST in room.doors && player.x + player.width >= WorldConst.ROOM_WIDTH - 10f && cy > WorldConst.TILE_SIZE * 7f -> Direction.EAST
      Direction.NORTH in room.doors && player.y <= WorldConst.TILE_SIZE + 10f && cx in (8.5f * WorldConst.TILE_SIZE)..(11.5f * WorldConst.TILE_SIZE) -> Direction.NORTH
      Direction.SOUTH in room.doors && player.y + player.height >= WorldConst.ROOM_HEIGHT - 12f && cx in (8.8f * WorldConst.TILE_SIZE)..(11.2f * WorldConst.TILE_SIZE) -> Direction.SOUTH
      else -> null
    }

    if (dir != null) {
      val nextCoord = (room.gx + dir.dx) to (room.gy + dir.dy)
      if (nextCoord in floorMap.rooms) {
        floorMap.currentCoord = nextCoord
        val nextRoom = floorMap.currentRoom
        nextRoom.visited = true
        doorCooldown = 0.75f
        projectiles.clear()
        particles.clear()
        when (dir) {
          Direction.EAST -> {
            player.x = WorldConst.TILE_SIZE + 26f
            player.y = 9.5f * WorldConst.TILE_SIZE
          }
          Direction.WEST -> {
            player.x = WorldConst.ROOM_WIDTH - WorldConst.TILE_SIZE - player.width - 26f
            player.y = 9.5f * WorldConst.TILE_SIZE
          }
          Direction.NORTH -> {
            player.x = if (Direction.SOUTH in nextRoom.doors) {
              7.4f * WorldConst.TILE_SIZE
            } else {
              WorldConst.ROOM_WIDTH * 0.5f - player.width / 2f
            }
            player.y = WorldConst.ROOM_HEIGHT - WorldConst.TILE_SIZE - player.height - 8f
          }
          Direction.SOUTH -> {
            player.x = WorldConst.ROOM_WIDTH * 0.5f - player.width / 2f
            player.y = 3.0f * WorldConst.TILE_SIZE - player.height - 4f
          }
        }
        player.vx = 0f
        player.vy = 0f
        player.invulnTimer = 0.5f
        safeSpawnX = player.x
        safeSpawnY = player.y
      }
    }
  }

  private fun damagePlayer(amount: Int, knockVx: Float, knockVy: Float) {
    if (player.invulnTimer > 0f || isGameOver) return
    player.invulnTimer = 1.05f
    player.vx = knockVx
    player.vy = knockVy
    screenShake = 11f
    onSound(SoundEffect.SPLAT)
    onHapticHeavy()
    spawnBurst(player.x + player.width / 2f, player.y + player.height / 2f, MeatCrimson, 16, 240f)

    if (player.shield > 0) {
      val absorbed = min(player.shield, amount)
      player.shield -= absorbed
      val rem = amount - absorbed
      if (rem > 0) player.hp -= rem
    } else {
      player.hp -= amount
    }

    if (player.hp <= 0) {
      player.hp = 0
      isGameOver = true
      onGameOver(score, floorMap.floorNumber, kills)
    }
  }

  private fun updateParticles(dt: Float) {
    val iter = particles.iterator()
    while (iter.hasNext()) {
      val p = iter.next()
      p.life -= dt
      if (p.life <= 0f) {
        iter.remove()
        continue
      }
      p.vy += p.gravity * dt
      p.x += p.vx * dt
      p.y += p.vy * dt
    }
  }

  private fun spawnBurst(x: Float, y: Float, color: Color, count: Int, speed: Float) {
    repeat(count) {
      val a = Random.nextFloat() * (2f * PI.toFloat())
      val s = speed * (0.35f + Random.nextFloat() * 0.65f)
      spawnParticle(x, y, cos(a) * s, sin(a) * s - 60f, 0.35f + Random.nextFloat() * 0.25f, 4f + Random.nextFloat() * 4f, color)
    }
  }

  private fun spawnParticle(
    x: Float,
    y: Float,
    vx: Float,
    vy: Float,
    life: Float,
    size: Float,
    color: Color,
    gravity: Float = 450f,
  ) {
    if (particles.size > 220) return
    particles.add(Particle(x, y, vx, vy, life, life, size, color, gravity))
  }

  private fun getTile(room: DungeonRoom, r: Int, c: Int): TileKind {
    if (r !in 0 until WorldConst.GRID_ROWS || c !in 0 until WorldConst.GRID_COLS) return TileKind.SOLID
    if (!room.cleared && (r == 0 || r == WorldConst.GRID_ROWS - 1 || c == 0 || c == WorldConst.GRID_COLS - 1)) return TileKind.SOLID
    return room.tiles[r][c]
  }

  private fun rectsOverlap(x1: Float, y1: Float, w1: Float, h1: Float, x2: Float, y2: Float, w2: Float, h2: Float): Boolean {
    return x1 < x2 + w2 && x1 + w1 > x2 && y1 < y2 + h2 && y1 + h1 > y2
  }

  fun snapshotHud(): GameHudSnapshot {
    val room = floorMap.currentRoom
    val boss = room.enemies.firstOrNull { it.type.isBoss }
    val buildCost = if (RelicType.TURBO_BUILD in player.relics) 5 else 10
    val rp = remotePlayer
    val minimap = floorMap.rooms.values.map { r ->
      MinimapNode(
        gx = r.gx,
        gy = r.gy,
        type = r.type,
        visited = r.visited,
        cleared = r.cleared,
        isCurrent = (r.gx to r.gy) == floorMap.currentCoord,
        hasRemotePlayer = rp != null && rp.alive && rp.roomGx == r.gx && rp.roomGy == r.gy,
      )
    }
    val modeLabel = when (multiplayerMode) {
      "coop" -> "КООП"
      "pvp" -> "ДУЭЛЬ PvP"
      else -> null
    }
    return GameHudSnapshot(
      floor = floorMap.floorNumber,
      score = score,
      kills = kills,
      hp = player.hp,
      maxHp = player.maxHp,
      shield = player.shield,
      maxShield = player.maxShield,
      mats = player.mats,
      buildCost = buildCost,
      weapon = player.weapon,
      relics = player.relics.toList(),
      roomType = room.type,
      roomCleared = room.cleared,
      stormSecondsLeft = ceil(room.stormTimer).toInt(),
      stormActive = !room.cleared && room.stormTimer <= 0f,
      bossHpRatio = boss?.let { (it.hp / it.maxHp).coerceIn(0f, 1f) },
      bossTitle = boss?.type?.bossTitleRu,
      dashReadyFraction = (1f - (player.dashCooldown / 1.05f)).coerceIn(0f, 1f),
      autoAimEnabled = autoAimEnabled,
      coinsEarned = coinsEarnedThisRun,
      toast = activeToast,
      minimapRooms = minimap,
      multiplayerModeLabel = modeLabel,
      remotePlayerName = rp?.displayName,
      remotePlayerHp = rp?.hp ?: 0,
      remotePlayerMaxHp = rp?.maxHp ?: 6,
      remotePlayerAlive = rp?.alive ?: true,
      multiplayerResultText = multiplayerResultText,
    )
  }
}
