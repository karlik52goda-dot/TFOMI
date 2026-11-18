package com.example

import com.example.game.engine.DungeonGenerator
import com.example.game.engine.GameEngine
import com.example.game.model.InputState
import com.example.game.model.RoomType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GameEngineTest {
  @Test
  fun dungeonGeneratorCreatesCleanBranchingTreeWithDeadEndBossAndTreasure() {
    for (floorNum in 1..4) {
      for (seed in 1..25) {
        val floor = DungeonGenerator.generateFloor(floorNum, Random(seed))
        val types = floor.rooms.values.map { it.type }
        assertTrue(RoomType.START in types)
        assertTrue(RoomType.TREASURE in types)
        assertTrue(RoomType.BOSS in types)
        assertTrue(floor.rooms.size >= 6)

        val bossRoom = floor.rooms.values.first { it.type == RoomType.BOSS }
        val treasureRoom = floor.rooms.values.first { it.type == RoomType.TREASURE }
        assertEquals("Boss room must be a 1-door dead end", 1, bossRoom.doors.size)
        assertEquals("Treasure room must be a 1-door dead end", 1, treasureRoom.doors.size)

        // Verify symmetric doorway connections and clear doorway safe zones
        for ((coord, room) in floor.rooms) {
          for (dir in room.doors) {
            val neighbor = floor.rooms[(coord.first + dir.dx) to (coord.second + dir.dy)]
            assertTrue("Neighbor must exist for $dir", neighbor != null)
            val opposite = when (dir) {
              com.example.game.model.Direction.NORTH -> com.example.game.model.Direction.SOUTH
              com.example.game.model.Direction.SOUTH -> com.example.game.model.Direction.NORTH
              com.example.game.model.Direction.EAST -> com.example.game.model.Direction.WEST
              com.example.game.model.Direction.WEST -> com.example.game.model.Direction.EAST
            }
            assertTrue("Doorway $dir must be symmetric", opposite in neighbor!!.doors)
          }

          if (com.example.game.model.Direction.SOUTH in room.doors) {
            for (r in 9..10) {
              for (c in 8..11) {
                val tk = room.tiles[r][c]
                assertTrue(
                  "No spike/crate/solid may block SOUTH doorway",
                  tk == com.example.game.model.TileKind.EMPTY || tk == com.example.game.model.TileKind.ONE_WAY,
                )
              }
            }
          }
          if (com.example.game.model.Direction.WEST in room.doors) {
            for (r in 8..10) {
              for (c in 1..2) {
                val tk = room.tiles[r][c]
                assertTrue(
                  "No spike/crate/solid may block WEST doorway",
                  tk == com.example.game.model.TileKind.EMPTY || tk == com.example.game.model.TileKind.ONE_WAY,
                )
              }
            }
          }
          if (com.example.game.model.Direction.EAST in room.doors) {
            for (r in 8..10) {
              for (c in 17..18) {
                val tk = room.tiles[r][c]
                assertTrue(
                  "No spike/crate/solid may block EAST doorway",
                  tk == com.example.game.model.TileKind.EMPTY || tk == com.example.game.model.TileKind.ONE_WAY,
                )
              }
            }
          }
        }
      }
    }
  }

  @Test
  fun playerCanJumpBuildAndShootTears() {
    val engine = GameEngine()
    engine.startNewRun()
    val startMats = engine.player.mats
    engine.player.onGround = true
    engine.player.coyoteTimer = 0.1f

    engine.tick(
      0.016f,
      InputState(
        moveX = 1f,
        jumpPressed = true,
        jumpHeld = true,
        shootHeld = true,
        buildFloorPressed = true,
      ),
    )

    assertTrue(engine.player.vy < 0f)
    assertEquals(startMats - 10, engine.player.mats)
    assertEquals(1, engine.floorMap.currentRoom.builds.size)
    assertTrue(engine.projectiles.isNotEmpty())
  }

  @Test
  fun screenTapAimFiresTowardTappedWorldPoint() {
    val engine = GameEngine()
    engine.startNewRun()
    engine.autoAimEnabled = false
    engine.tick(0.016f, InputState(targetWorldPos = com.example.game.model.Vec2(600f, 100f)))
    assertTrue(engine.projectiles.isNotEmpty())
    val proj = engine.projectiles.first()
    assertTrue(proj.vx > 0f)
    assertTrue(proj.vy < 0f)
  }

  @Test
  fun autoAimSteersShotsTowardEnemyOnlyWhenShootPressed() {
    val engine = GameEngine()
    engine.startNewRun()
    engine.autoAimEnabled = true
    val enemy = com.example.game.model.Enemy(
      id = 999,
      type = com.example.game.model.EnemyType.MEAT_HOPPER,
      x = 500f,
      y = 140f,
      width = 34f,
      height = 34f,
      hp = 30f,
      maxHp = 30f,
    )
    engine.floorMap.currentRoom.enemies.add(enemy)

    // Without pressing shoot, no projectiles should be fired
    engine.tick(0.016f, InputState(shootHeld = false))
    assertEquals(999, engine.lockedEnemyId)
    assertTrue(engine.projectiles.isEmpty())

    // Pressing shoot with auto-aim enabled fires toward the enemy even if stick aimDir is off-center
    engine.tick(0.016f, InputState(shootHeld = true, aimDir = com.example.game.model.Vec2(-1f, 0f)))
    assertTrue(engine.projectiles.isNotEmpty())
    val proj = engine.projectiles.first()
    assertTrue(proj.vx > 0f)
    assertTrue(proj.vy < 0f)
  }

  @Test
  fun allBossTypesMoveShootAndStayInsideArenaBounds() {
    val bossTypes = listOf(
      com.example.game.model.EnemyType.BOSS_STORM_MONSTRO,
      com.example.game.model.EnemyType.BOSS_DUKE_OF_MEAT,
      com.example.game.model.EnemyType.BOSS_SAW_MECH,
      com.example.game.model.EnemyType.BOSS_STORM_CUBE,
    )
    for (bossType in bossTypes) {
      val engine = GameEngine()
      engine.startNewRun()
      val room = engine.floorMap.currentRoom
      val startX = 450f
      val startY = 350f
      val boss = com.example.game.model.Enemy(
        id = 777 + bossType.ordinal,
        type = bossType,
        x = startX,
        y = startY,
        width = 76f,
        height = 68f,
        hp = 300f,
        maxHp = 300f,
        aiTimer = 0.1f,
      )
      room.enemies.add(boss)

      var sawEnemyProjectile = false
      var movedSignificantly = false
      repeat(180) {
        engine.tick(0.016f, InputState())
        if (engine.projectiles.any { !it.isPlayer }) {
          sawEnemyProjectile = true
        }
        if (kotlin.math.abs(boss.x - startX) > 25f || kotlin.math.abs(boss.y - startY) > 25f) {
          movedSignificantly = true
        }
        assertTrue("${bossType.name} left left bound", boss.x >= com.example.game.model.WorldConst.TILE_SIZE)
        assertTrue("${bossType.name} left right bound", boss.x + boss.width <= com.example.game.model.WorldConst.ROOM_WIDTH - com.example.game.model.WorldConst.TILE_SIZE)
        assertTrue("${bossType.name} left top bound", boss.y >= com.example.game.model.WorldConst.TILE_SIZE)
        assertTrue("${bossType.name} left bottom bound", boss.y + boss.height <= com.example.game.model.WorldConst.ROOM_HEIGHT - com.example.game.model.WorldConst.TILE_SIZE)
      }
      assertTrue("${bossType.name} must shoot projectiles!", sawEnemyProjectile)
      assertTrue("${bossType.name} must actively move!", movedSignificantly)
      assertEquals(bossType.bossTitleRu, engine.snapshotHud().bossTitle)
    }
  }

  @Test
  fun dungeonGeneratesMultipleBossVarietiesAcrossFloorsAndSeeds() {
    val seenBosses = mutableSetOf<com.example.game.model.EnemyType>()
    for (floorNum in 1..4) {
      for (seed in 1..15) {
        val floor = DungeonGenerator.generateFloor(floorNum, Random(seed))
        val bossRoom = floor.rooms.values.first { it.type == RoomType.BOSS }
        seenBosses.addAll(bossRoom.enemies.filter { it.type.isBoss }.map { it.type })
      }
    }
    assertEquals(4, seenBosses.size)
  }

  @Test
  fun clearingFloorPortalAwardsTenCoinsAndAdvancesFloor() {
    var callbackFloor = 0
    var callbackCoins = 0
    val engine = GameEngine(
      onFloorCleared = { f, c ->
        callbackFloor = f
        callbackCoins = c
      }
    )
    engine.startNewRun()
    val room = engine.floorMap.currentRoom
    room.pickups.add(
      com.example.game.model.Pickup(
        id = 888,
        type = com.example.game.model.PickupType.EXIT_PORTAL,
        x = engine.player.x + engine.player.width * 0.5f,
        y = engine.player.y + engine.player.height * 0.5f,
      )
    )
    engine.tick(0.016f, InputState())
    assertEquals(2, engine.floorMap.floorNumber)
    assertEquals(1, callbackFloor)
    assertEquals(10, callbackCoins)
    assertEquals(10, engine.coinsEarnedThisRun)
    assertEquals(10, engine.snapshotHud().coinsEarned)
  }

  @Test
  fun playerSkinsIncludeAllRequestedCharacters() {
    val titles = com.example.game.model.PlayerSkin.entries.map { it.titleRu }
    assertTrue("Банан" in titles)
    assertTrue("Айзек" in titles)
    assertTrue("Mr. Beast" in titles)
    assertTrue("Оливер200" in titles)
    assertTrue("Чекушка" in titles)
    assertTrue("Мелстрой" in titles)
    assertTrue("Мама птица" in titles)
    assertTrue("Админ" in titles)
    assertEquals(10, com.example.game.model.PlayerSkin.BANANA.price)
    assertEquals(20, com.example.game.model.PlayerSkin.ISAAC.price)
    assertEquals(30, com.example.game.model.PlayerSkin.MR_BEAST.price)
    assertEquals(40, com.example.game.model.PlayerSkin.OLIVER_200.price)
    assertEquals(50, com.example.game.model.PlayerSkin.CHEKUSHKA.price)
    assertEquals(60, com.example.game.model.PlayerSkin.MELLSTROY.price)
    assertEquals(70, com.example.game.model.PlayerSkin.MAMA_PTITSA.price)
    assertTrue(com.example.game.model.PlayerSkin.ADMIN.requiresPassword)
    assertEquals(0, com.example.game.model.PlayerSkin.ADMIN.price)
  }

  @Test
  fun seededMultiplayerRunIsDeterministicAcrossHostAndGuest() {
    val hostEngine = GameEngine()
    val guestEngine = GameEngine()
    hostEngine.startNewRun(seed = 424242L, mpMode = "pvp", isMpGuest = false)
    guestEngine.startNewRun(seed = 424242L, mpMode = "pvp", isMpGuest = true)

    assertEquals(hostEngine.floorMap.rooms.keys, guestEngine.floorMap.rooms.keys)
    assertEquals("ДУЭЛЬ PvP", hostEngine.snapshotHud().multiplayerModeLabel)
    assertTrue(guestEngine.player.x > hostEngine.player.x)
    assertTrue(hostEngine.player.facingRight)
    assertTrue(!guestEngine.player.facingRight)
  }

  @Test
  fun controlsLayoutConfigEncodesDecodesAndClampsCoordinates() {
    val custom = com.example.data.ControlsLayoutConfig(
      customEnabled = true,
      swapSides = true,
      swapJumpShoot = true,
      positions = mapOf(
        com.example.data.ControlId.MOVE to com.example.data.ControlNormPos(0.75f, 0.80f),
        com.example.data.ControlId.JUMP to com.example.data.ControlNormPos(0.15f, 0.72f),
        com.example.data.ControlId.SHOOT to com.example.data.ControlNormPos(0.32f, 0.85f),
      ),
    )
    val encoded = custom.encode()
    val decoded = com.example.data.ControlsLayoutConfig.decode(encoded)
    assertTrue(decoded.customEnabled)
    assertTrue(decoded.swapSides)
    assertTrue(decoded.swapJumpShoot)
    assertEquals(0.75f, decoded.posFor(com.example.data.ControlId.MOVE).x, 0.001f)
    assertEquals(0.80f, decoded.posFor(com.example.data.ControlId.MOVE).y, 0.001f)
    assertEquals(0.15f, decoded.posFor(com.example.data.ControlId.JUMP).x, 0.001f)

    // Verify out-of-range coordinates get safely clamped
    val clamped = com.example.data.ControlsLayoutConfig.decode("true|false|false|move:2.5,-0.5")
    assertEquals(0.92f, clamped.posFor(com.example.data.ControlId.MOVE).x, 0.001f)
    assertEquals(0.18f, clamped.posFor(com.example.data.ControlId.MOVE).y, 0.001f)
  }
}
