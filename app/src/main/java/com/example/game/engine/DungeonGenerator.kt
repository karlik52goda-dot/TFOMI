package com.example.game.engine

import com.example.game.model.*
import kotlin.math.abs
import kotlin.random.Random

object DungeonGenerator {
  private var nextEntityId = 1
  fun nextId(): Int = nextEntityId++

  fun generateFloor(floorNumber: Int, rng: Random = Random.Default): FloorMap {
    val targetRooms = (6 + floorNumber).coerceAtMost(9)
    val start = 0 to 0

    // Build a clean Isaac-style branching tree where START branches in at least 2 directions,
    // mathematically guaranteeing >= 2 non-start leaf rooms for BOSS and TREASURE.
    val coords = linkedSetOf(start)
    val frontier = mutableListOf<Pair<Int, Int>>()
    val initialDirs = Direction.entries.shuffled(rng).take(2)
    for (d in initialDirs) {
      val c = (start.first + d.dx) to (start.second + d.dy)
      coords.add(c)
      frontier.add(c)
    }

    while (coords.size < targetRooms && frontier.isNotEmpty()) {
      val base = frontier[rng.nextInt(frontier.size)]
      val candidates = Direction.entries
        .map { base.first + it.dx to base.second + it.dy }
        .filter { (x, y) ->
          x in -2..2 && y in -2..2 &&
            (x to y) !in coords &&
            Direction.entries.count { d -> (x + d.dx to y + d.dy) in coords } == 1
        }
      if (candidates.isEmpty()) {
        frontier.remove(base)
        continue
      }
      val chosen = candidates[rng.nextInt(candidates.size)]
      coords.add(chosen)
      frontier.add(chosen)
    }

    // Fallback if frontier stalled early: branch from any existing cell using the 1-neighbor tree rule
    var guard = 0
    while (coords.size < 6 && guard++ < 50) {
      val base = coords.elementAt(rng.nextInt(coords.size))
      val candidates = Direction.entries
        .map { base.first + it.dx to base.second + it.dy }
        .filter { (x, y) ->
          x in -2..2 && y in -2..2 &&
            (x to y) !in coords &&
            Direction.entries.count { d -> (x + d.dx to y + d.dy) in coords } == 1
        }
      if (candidates.isNotEmpty()) {
        coords.add(candidates[rng.nextInt(candidates.size)])
      }
    }

    val nonStart = coords.filter { it != start }
    val leaves = nonStart.filter { c ->
      Direction.entries.count { d -> (c.first + d.dx to c.second + d.dy) in coords } == 1
    }

    val bossCoord = (leaves.ifEmpty { nonStart })
      .maxByOrNull { abs(it.first) + abs(it.second) } ?: nonStart.last()

    val treasureCoord = leaves
      .filter { it != bossCoord }
      .minByOrNull { abs(it.first) + abs(it.second) }
      ?: nonStart.firstOrNull { it != bossCoord }
      ?: nonStart.first()

    val rooms = mutableMapOf<Pair<Int, Int>, DungeonRoom>()
    for (c in coords) {
      val type = when (c) {
        start -> RoomType.START
        bossCoord -> RoomType.BOSS
        treasureCoord -> RoomType.TREASURE
        else -> RoomType.COMBAT
      }
      val doors = Direction.entries
        .filter { dir -> (c.first + dir.dx to c.second + dir.dy) in coords }
        .toSet()
      rooms[c] = buildRoom(c.first, c.second, type, doors, floorNumber, rng)
    }

    return FloorMap(floorNumber = floorNumber, rooms = rooms, currentCoord = start)
  }

  private fun buildRoom(
    gx: Int,
    gy: Int,
    type: RoomType,
    doors: Set<Direction>,
    floor: Int,
    rng: Random,
  ): DungeonRoom {
    val tiles = Array(WorldConst.GRID_ROWS) { Array(WorldConst.GRID_COLS) { TileKind.EMPTY } }

    // Outer solid border
    for (col in 0 until WorldConst.GRID_COLS) {
      tiles[0][col] = TileKind.SOLID
      tiles[WorldConst.GRID_ROWS - 1][col] = TileKind.SOLID
    }
    for (row in 0 until WorldConst.GRID_ROWS) {
      tiles[row][0] = TileKind.SOLID
      tiles[row][WorldConst.GRID_COLS - 1] = TileKind.SOLID
    }

    // Carve doorways & guaranteed 2-tile-stepped climbable platforms (max jump = 2.3 tiles)
    if (Direction.NORTH in doors) {
      tiles[0][9] = TileKind.EMPTY
      tiles[0][10] = TileKind.EMPTY
      // Clean 2-tile-step staircase up to NORTH ceiling portal (rows 9 -> 7 -> 5 -> 3 -> 0)
      tiles[9][7] = TileKind.ONE_WAY
      tiles[9][8] = TileKind.ONE_WAY
      tiles[9][11] = TileKind.ONE_WAY
      tiles[9][12] = TileKind.ONE_WAY
      tiles[7][9] = TileKind.ONE_WAY
      tiles[7][10] = TileKind.ONE_WAY
      tiles[5][7] = TileKind.ONE_WAY
      tiles[5][8] = TileKind.ONE_WAY
      tiles[5][11] = TileKind.ONE_WAY
      tiles[5][12] = TileKind.ONE_WAY
      tiles[3][9] = TileKind.ONE_WAY
      tiles[3][10] = TileKind.ONE_WAY
    }
    if (Direction.SOUTH in doors) {
      // Open floor hatch when room is cleared (sealed SOLID by GameEngine while combat is active)
      tiles[WorldConst.GRID_ROWS - 1][9] = TileKind.EMPTY
      tiles[WorldConst.GRID_ROWS - 1][10] = TileKind.EMPTY
    }
    if (Direction.WEST in doors) {
      tiles[8][0] = TileKind.EMPTY
      tiles[9][0] = TileKind.EMPTY
      tiles[10][0] = TileKind.EMPTY
    }
    if (Direction.EAST in doors) {
      tiles[8][WorldConst.GRID_COLS - 1] = TileKind.EMPTY
      tiles[9][WorldConst.GRID_COLS - 1] = TileKind.EMPTY
      tiles[10][WorldConst.GRID_COLS - 1] = TileKind.EMPTY
    }

    val room = DungeonRoom(
      gx = gx,
      gy = gy,
      type = type,
      tiles = tiles,
      doors = doors,
      cleared = (type == RoomType.START || type == RoomType.TREASURE),
      visited = (type == RoomType.START),
      stormTimer = (28f - floor * 1.5f).coerceAtLeast(16f),
      stormMaxTime = (28f - floor * 1.5f).coerceAtLeast(16f),
    )

    when (type) {
      RoomType.START -> populateStartRoom(room)
      RoomType.TREASURE -> populateTreasureRoom(room, floor, rng)
      RoomType.BOSS -> populateBossRoom(room, floor, rng)
      RoomType.COMBAT -> populateCombatRoom(room, floor, rng)
    }

    // Safety pass: NEVER allow spikes, crates, or blocking solids right in front of any doorway!
    clearDoorSafeZones(room)

    return room
  }

  private fun clearDoorSafeZones(room: DungeonRoom) {
    val t = room.tiles
    if (Direction.WEST in room.doors) {
      for (r in 8..10) for (c in 1..2) if (t[r][c] != TileKind.ONE_WAY) t[r][c] = TileKind.EMPTY
    }
    if (Direction.EAST in room.doors) {
      for (r in 8..10) for (c in 17..18) if (t[r][c] != TileKind.ONE_WAY) t[r][c] = TileKind.EMPTY
    }
    if (Direction.SOUTH in room.doors) {
      for (r in 9..10) for (c in 8..11) if (t[r][c] != TileKind.ONE_WAY) t[r][c] = TileKind.EMPTY
    }
    if (Direction.NORTH in room.doors) {
      for (r in 1..2) for (c in 8..11) t[r][c] = TileKind.EMPTY
    }
  }

  private fun populateStartRoom(room: DungeonRoom) {
    val t = room.tiles
    t[9][4] = TileKind.ONE_WAY
    t[9][5] = TileKind.ONE_WAY
    t[9][14] = TileKind.ONE_WAY
    t[9][15] = TileKind.ONE_WAY
    t[10][5] = TileKind.CRATE
    t[10][14] = TileKind.CRATE
    room.pickups.add(
      Pickup(
        id = nextId(),
        type = PickupType.MATS_PACK,
        x = 7.2f * WorldConst.TILE_SIZE,
        y = 10f * WorldConst.TILE_SIZE,
      )
    )
  }

  private fun populateTreasureRoom(room: DungeonRoom, floor: Int, rng: Random) {
    val t = room.tiles
    t[9][4] = TileKind.SOLID
    t[9][5] = TileKind.SOLID
    t[9][14] = TileKind.SOLID
    t[9][15] = TileKind.SOLID
    t[10][3] = TileKind.CRATE
    t[10][16] = TileKind.CRATE

    val rarity = if (floor >= 2 || rng.nextFloat() < 0.45f) WeaponRarity.EPIC else WeaponRarity.RARE
    val weaponType = WeaponType.entries.filter { it != WeaponType.TEAR_BLASTER }[rng.nextInt(3)]
    room.pickups.add(
      Pickup(
        id = nextId(),
        type = PickupType.LOOT_CHEST,
        x = 5.0f * WorldConst.TILE_SIZE,
        y = 8.3f * WorldConst.TILE_SIZE,
        rarity = rarity,
        weapon = WeaponLoadout(weaponType, rarity),
      )
    )
    val relic = RelicType.entries[rng.nextInt(RelicType.entries.size)]
    room.pickups.add(
      Pickup(
        id = nextId(),
        type = PickupType.RELIC_PEDESTAL,
        x = 15.0f * WorldConst.TILE_SIZE,
        y = 8.3f * WorldConst.TILE_SIZE,
        relic = relic,
      )
    )
  }

  private fun populateBossRoom(room: DungeonRoom, floor: Int, rng: Random) {
    val t = room.tiles
    t[9][3] = TileKind.ONE_WAY
    t[9][4] = TileKind.ONE_WAY
    t[9][5] = TileKind.ONE_WAY
    t[9][14] = TileKind.ONE_WAY
    t[9][15] = TileKind.ONE_WAY
    t[9][16] = TileKind.ONE_WAY
    t[7][7] = TileKind.ONE_WAY
    t[7][8] = TileKind.ONE_WAY
    t[7][11] = TileKind.ONE_WAY
    t[7][12] = TileKind.ONE_WAY
    t[10][3] = TileKind.CRATE
    t[10][16] = TileKind.CRATE

    if (floor >= 2) {
      room.saws.add(
        BuzzSaw(
          id = nextId(),
          startX = 4.5f * WorldConst.TILE_SIZE,
          startY = 10.6f * WorldConst.TILE_SIZE,
          endX = 15.5f * WorldConst.TILE_SIZE,
          endY = 10.6f * WorldConst.TILE_SIZE,
          radius = 18f,
          speed = 0.85f,
        )
      )
    }

    val bossRoster = listOf(
      EnemyType.BOSS_STORM_MONSTRO,
      EnemyType.BOSS_DUKE_OF_MEAT,
      EnemyType.BOSS_SAW_MECH,
      EnemyType.BOSS_STORM_CUBE,
    )
    val bossType = bossRoster[(floor - 1 + rng.nextInt(bossRoster.size)) % bossRoster.size]
    val (bw, bh, baseHp, spawnTileY) = when (bossType) {
      EnemyType.BOSS_STORM_MONSTRO -> listOf(76f, 68f, 260f, 8.0f)
      EnemyType.BOSS_DUKE_OF_MEAT -> listOf(72f, 72f, 250f, 4.2f)
      EnemyType.BOSS_SAW_MECH -> listOf(80f, 62f, 290f, 8.2f)
      EnemyType.BOSS_STORM_CUBE -> listOf(70f, 70f, 270f, 4.0f)
      else -> listOf(76f, 68f, 260f, 8.0f)
    }
    val bossHp = baseHp + (floor - 1) * 135f
    room.enemies.add(
      Enemy(
        id = nextId(),
        type = bossType,
        x = WorldConst.ROOM_WIDTH * 0.5f - bw * 0.5f,
        y = spawnTileY * WorldConst.TILE_SIZE,
        width = bw,
        height = bh,
        hp = bossHp,
        maxHp = bossHp,
        aiTimer = 0.7f,
      )
    )
  }

  private fun populateCombatRoom(room: DungeonRoom, floor: Int, rng: Random) {
    val t = room.tiles
    val template = rng.nextInt(5)
    val hpScale = 1f + (floor - 1) * 0.35f

    when (template) {
      0 -> { // Side Spike Traps + Overpass Bridge (safe around center SOUTH door!)
        t[10][5] = TileKind.SPIKE
        t[10][6] = TileKind.SPIKE
        t[10][13] = TileKind.SPIKE
        t[10][14] = TileKind.SPIKE
        for (c in 5..7) t[9][c] = TileKind.ONE_WAY
        for (c in 12..14) t[9][c] = TileKind.ONE_WAY
        for (c in 8..11) t[7][c] = TileKind.ONE_WAY
        t[10][4] = TileKind.CRATE
        t[10][15] = TileKind.CRATE
        room.saws.add(
          BuzzSaw(
            id = nextId(),
            startX = 6f * WorldConst.TILE_SIZE,
            startY = 5.2f * WorldConst.TILE_SIZE,
            endX = 14f * WorldConst.TILE_SIZE,
            endY = 5.2f * WorldConst.TILE_SIZE,
            speed = 1.3f,
          )
        )
        spawnEnemy(room, EnemyType.MEAT_HOPPER, 7.5f, 9f, hpScale)
        spawnEnemy(room, EnemyType.MEAT_HOPPER, 12.0f, 9f, hpScale)
        spawnEnemy(room, EnemyType.BLOOD_FLY, 10f, 3.2f, hpScale)
      }

      1 -> { // Wall-Jump Pillars (walk-under arches at floor level so you never get stuck!)
        for (r in 5..8) {
          t[r][6] = TileKind.SOLID
          t[r][13] = TileKind.SOLID
        }
        t[9][3] = TileKind.ONE_WAY
        t[9][4] = TileKind.ONE_WAY
        t[9][15] = TileKind.ONE_WAY
        t[9][16] = TileKind.ONE_WAY
        t[7][4] = TileKind.ONE_WAY
        t[7][5] = TileKind.ONE_WAY
        t[7][14] = TileKind.ONE_WAY
        t[7][15] = TileKind.ONE_WAY
        t[5][8] = TileKind.ONE_WAY
        t[5][9] = TileKind.ONE_WAY
        t[5][10] = TileKind.ONE_WAY
        t[5][11] = TileKind.ONE_WAY
        room.saws.add(
          BuzzSaw(
            id = nextId(),
            startX = 10f * WorldConst.TILE_SIZE,
            startY = 3.2f * WorldConst.TILE_SIZE,
            endX = 10f * WorldConst.TILE_SIZE,
            endY = 7.5f * WorldConst.TILE_SIZE,
            speed = 1.15f,
          )
        )
        spawnEnemy(room, EnemyType.SAW_TURRET, 9.6f, 4.1f, hpScale)
        spawnEnemy(room, EnemyType.BLOOD_FLY, 5f, 3.8f, hpScale)
        spawnEnemy(room, EnemyType.BLOOD_FLY, 14.5f, 3.8f, hpScale)
      }

      2 -> { // Fortress Tiers
        for (c in 3..6) t[9][c] = TileKind.ONE_WAY
        for (c in 13..16) t[9][c] = TileKind.ONE_WAY
        for (c in 7..12) t[7][c] = TileKind.ONE_WAY
        t[10][7] = TileKind.CRATE
        t[10][12] = TileKind.CRATE
        t[6][9] = TileKind.CRATE
        room.saws.add(
          BuzzSaw(
            id = nextId(),
            startX = 5f * WorldConst.TILE_SIZE,
            startY = 10.3f * WorldConst.TILE_SIZE,
            endX = 15f * WorldConst.TILE_SIZE,
            endY = 10.3f * WorldConst.TILE_SIZE,
            speed = 0.95f,
          )
        )
        spawnEnemy(room, EnemyType.SAW_TURRET, 4.5f, 8.1f, hpScale)
        spawnEnemy(room, EnemyType.SAW_TURRET, 15f, 8.1f, hpScale)
        spawnEnemy(room, EnemyType.MEAT_HOPPER, 9.5f, 6f, hpScale)
      }

      3 -> { // Twin Buzzsaw Gauntlet
        t[9][4] = TileKind.SOLID
        t[9][5] = TileKind.SOLID
        t[9][14] = TileKind.SOLID
        t[9][15] = TileKind.SOLID
        t[7][8] = TileKind.ONE_WAY
        t[7][9] = TileKind.ONE_WAY
        t[7][10] = TileKind.ONE_WAY
        t[7][11] = TileKind.ONE_WAY
        room.saws.add(
          BuzzSaw(
            id = nextId(),
            startX = 6.5f * WorldConst.TILE_SIZE,
            startY = 8.4f * WorldConst.TILE_SIZE,
            endX = 13.5f * WorldConst.TILE_SIZE,
            endY = 8.4f * WorldConst.TILE_SIZE,
            speed = 1.45f,
          )
        )
        room.saws.add(
          BuzzSaw(
            id = nextId(),
            startX = 13.5f * WorldConst.TILE_SIZE,
            startY = 4.6f * WorldConst.TILE_SIZE,
            endX = 6.5f * WorldConst.TILE_SIZE,
            endY = 4.6f * WorldConst.TILE_SIZE,
            speed = 1.45f,
          )
        )
        spawnEnemy(room, EnemyType.MEAT_HOPPER, 4.5f, 8f, hpScale)
        spawnEnemy(room, EnemyType.MEAT_HOPPER, 14.5f, 8f, hpScale)
        spawnEnemy(room, EnemyType.BLOOD_FLY, 10f, 3f, hpScale)
      }

      else -> { // Crate Bunker
        t[10][5] = TileKind.CRATE
        t[9][5] = TileKind.CRATE
        t[10][14] = TileKind.CRATE
        t[9][14] = TileKind.CRATE
        for (c in 7..12) t[7][c] = TileKind.ONE_WAY
        t[9][6] = TileKind.ONE_WAY
        t[9][13] = TileKind.ONE_WAY
        t[10][7] = TileKind.SPIKE
        t[10][12] = TileKind.SPIKE
        spawnEnemy(room, EnemyType.MEAT_HOPPER, 6.8f, 9f, hpScale)
        spawnEnemy(room, EnemyType.MEAT_HOPPER, 12.2f, 9f, hpScale)
        spawnEnemy(room, EnemyType.BLOOD_FLY, 10f, 3.8f, hpScale)
        if (floor >= 2) {
          spawnEnemy(room, EnemyType.SAW_TURRET, 9.5f, 6.1f, hpScale)
        }
      }
    }
  }

  private fun spawnEnemy(room: DungeonRoom, type: EnemyType, tileX: Float, tileY: Float, hpScale: Float) {
    val (w, h, baseHp) = when (type) {
      EnemyType.MEAT_HOPPER -> Triple(34f, 34f, 36f)
      EnemyType.BLOOD_FLY -> Triple(30f, 26f, 26f)
      EnemyType.SAW_TURRET -> Triple(36f, 38f, 44f)
      else -> Triple(76f, 68f, 260f)
    }
    room.enemies.add(
      Enemy(
        id = nextId(),
        type = type,
        x = tileX * WorldConst.TILE_SIZE,
        y = tileY * WorldConst.TILE_SIZE,
        width = w,
        height = h,
        hp = baseHp * hpScale,
        maxHp = baseHp * hpScale,
        aiTimer = Random.nextFloat() * 0.8f,
      )
    )
  }
}
