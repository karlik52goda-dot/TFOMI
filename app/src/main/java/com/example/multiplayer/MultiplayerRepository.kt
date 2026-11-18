package com.example.multiplayer

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

class MultiplayerRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
) {
  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  private fun requireUserId(): String =
    auth.currentUser?.uid
      ?: throw IllegalStateException("Требуется вход через Google для доступа к мультиплееру.")

  fun observeWaitingRooms(): Flow<List<RoomDoc>> {
    val path = "rooms"
    val uid = auth.currentUser?.uid
    return db.collection(path)
      .whereEqualTo("status", RoomStatus.WAITING.key)
      .limit(20)
      .snapshots()
      .map { snapshot ->
        snapshot.toObjects(RoomDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedByDescending { it.createdAt?.seconds ?: 0L }
      }
      .catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
          .filterIsInstance<FirebaseFirestoreException>()
          .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && uid != null && auth.currentUser?.uid == uid) {
          handleFirestoreError(exceptionToLog, OperationType.LIST, path)
        }
        throw error
      }
  }

  fun observeRoom(roomId: String): Flow<RoomDoc?> {
    val path = "rooms/$roomId"
    val uid = auth.currentUser?.uid
    return db.collection("rooms").document(roomId)
      .snapshots()
      .map { snapshot ->
        if (!snapshot.exists()) null
        else snapshot.toObject(RoomDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      }
      .catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
          .filterIsInstance<FirebaseFirestoreException>()
          .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && uid != null && auth.currentUser?.uid == uid) {
          handleFirestoreError(exceptionToLog, OperationType.GET, path)
        }
        throw error
      }
  }

  fun observeRoomPlayers(roomId: String, currentUid: String): Flow<List<RoomPlayer>> {
    val path = "rooms/$roomId/players"
    return db.collection("rooms").document(roomId)
      .collection("players")
      .whereArrayContains("memberUids", currentUid)
      .snapshots()
      .map { snapshot ->
        snapshot.toObjects(RoomPlayer::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      }
      .catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
          .filterIsInstance<FirebaseFirestoreException>()
          .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == currentUid) {
          handleFirestoreError(exceptionToLog, OperationType.LIST, path)
        }
        throw error
      }
  }

  suspend fun createRoom(
    displayName: String,
    skinKey: String,
    mode: MatchMode,
  ): Result<RoomDoc> {
    var docPath = "rooms"
    return try {
      val uid = requireUserId()
      val safeName = sanitizeDisplayName(displayName)
      val safeSkin = sanitizeSkinKey(skinKey)
      val code = generateRoomCode()
      docPath = "rooms/$code"

      val seed = Random.nextInt(10_000, 999_999).toLong()
      val roomRef = db.collection("rooms").document(code)
      val playerRef = roomRef.collection("players").document(uid)

      val roomPayload = mapOf(
        "roomCode" to code,
        "hostUid" to uid,
        "hostName" to safeName,
        "guestUid" to "",
        "guestName" to "",
        "mode" to mode.key,
        "status" to RoomStatus.WAITING.key,
        "floor" to 1L,
        "seed" to seed,
        "winnerUid" to "",
        "memberUids" to listOf(uid),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )

      val playerPayload = initialPlayerPayload(
        uid = uid,
        roomId = code,
        memberUids = listOf(uid),
        displayName = safeName,
        skinKey = safeSkin,
      )

      val batch = db.batch()
      batch.set(roomRef, roomPayload)
      batch.set(playerRef, playerPayload)
      batch.commit().await()

      Result.success(
        RoomDoc(
          id = code,
          roomCode = code,
          hostUid = uid,
          hostName = safeName,
          mode = mode.key,
          status = RoomStatus.WAITING.key,
          floor = 1L,
          seed = seed,
          memberUids = listOf(uid),
        )
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docPath)
      Result.failure(e)
    }
  }

  suspend fun joinRoom(
    rawCode: String,
    displayName: String,
    skinKey: String,
  ): Result<RoomDoc> {
    val code = rawCode.trim().uppercase()
    if (!ROOM_ID_REGEX.matches(code) || code.length !in 4..12) {
      return Result.failure(IllegalArgumentException("Код комнаты должен содержать 4–12 латинских букв/цифр."))
    }
    val docPath = "rooms/$code"
    return try {
      val uid = requireUserId()
      val safeName = sanitizeDisplayName(displayName)
      val safeSkin = sanitizeSkinKey(skinKey)
      val roomRef = db.collection("rooms").document(code)

      val snap = roomRef.get().await()
      if (!snap.exists()) {
        return Result.failure(IllegalStateException("Комната $code не найдена."))
      }
      val existing = snap.toObject(RoomDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        ?: return Result.failure(IllegalStateException("Не удалось прочитать комнату $code."))

      if (existing.hostUid == uid || uid in existing.memberUids) {
        return Result.success(existing)
      }
      if (existing.roomStatus() != RoomStatus.WAITING) {
        return Result.failure(IllegalStateException("Бой в этой комнате уже идёт или завершён."))
      }
      if (existing.guestUid.isNotEmpty()) {
        return Result.failure(IllegalStateException("Комната уже заполнена (2/2 игрока)."))
      }

      val newMembers = listOf(existing.hostUid, uid)
      val guestPlayerRef = roomRef.collection("players").document(uid)
      val hostPlayerRef = roomRef.collection("players").document(existing.hostUid)

      val batch = db.batch()
      batch.update(
        roomRef,
        mapOf(
          "guestUid" to uid,
          "guestName" to safeName,
          "memberUids" to newMembers,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.set(
        guestPlayerRef,
        initialPlayerPayload(
          uid = uid,
          roomId = code,
          memberUids = newMembers,
          displayName = safeName,
          skinKey = safeSkin,
        ),
      )
      batch.update(
        hostPlayerRef,
        mapOf(
          "memberUids" to newMembers,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.commit().await()

      Result.success(
        existing.copy(
          guestUid = uid,
          guestName = safeName,
          memberUids = newMembers,
        )
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docPath)
      Result.failure(e)
    }
  }

  suspend fun updateMode(roomId: String, mode: MatchMode): Result<Unit> {
    val docPath = "rooms/$roomId"
    return try {
      requireUserId()
      require(roomId.isNotBlank())
      db.collection("rooms").document(roomId).update(
        mapOf(
          "mode" to mode.key,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docPath)
      Result.failure(e)
    }
  }

  suspend fun startMatch(roomId: String, mode: MatchMode, seed: Long): Result<Unit> {
    val docPath = "rooms/$roomId"
    return try {
      requireUserId()
      require(roomId.isNotBlank())
      db.collection("rooms").document(roomId).update(
        mapOf(
          "mode" to mode.key,
          "status" to RoomStatus.PLAYING.key,
          "floor" to 1L,
          "seed" to seed.coerceAtLeast(1L),
          "winnerUid" to "",
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docPath)
      Result.failure(e)
    }
  }

  suspend fun updateFloor(roomId: String, floor: Int): Result<Unit> {
    val docPath = "rooms/$roomId"
    return try {
      requireUserId()
      db.collection("rooms").document(roomId).update(
        mapOf(
          "floor" to floor.coerceIn(1, 999).toLong(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docPath)
      Result.failure(e)
    }
  }

  suspend fun finishMatch(roomId: String, winnerUid: String): Result<Unit> {
    val docPath = "rooms/$roomId"
    return try {
      requireUserId()
      db.collection("rooms").document(roomId).update(
        mapOf(
          "status" to RoomStatus.FINISHED.key,
          "winnerUid" to winnerUid.take(128),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docPath)
      Result.failure(e)
    }
  }

  suspend fun syncPlayerState(
    roomId: String,
    displayName: String,
    skinKey: String,
    roomGx: Int,
    roomGy: Int,
    x: Float,
    y: Float,
    vx: Float,
    vy: Float,
    hp: Int,
    maxHp: Int,
    shield: Int,
    score: Int,
    facingRight: Boolean,
    alive: Boolean,
    shotSeq: Long,
    shotX: Float,
    shotY: Float,
    shotVx: Float,
    shotVy: Float,
  ): Result<Unit> {
    val uid = auth.currentUser?.uid
      ?: return Result.failure(IllegalStateException("Unauthenticated"))
    val docPath = "rooms/$roomId/players/$uid"
    return try {
      db.collection("rooms").document(roomId)
        .collection("players").document(uid)
        .update(
          mapOf(
            "displayName" to sanitizeDisplayName(displayName),
            "skinKey" to sanitizeSkinKey(skinKey),
            "roomGx" to roomGx.coerceIn(-10, 10).toLong(),
            "roomGy" to roomGy.coerceIn(-10, 10).toLong(),
            "x" to x.toDouble().coerceIn(-150.0, 1950.0),
            "y" to y.toDouble().coerceIn(-150.0, 1950.0),
            "vx" to vx.toDouble().coerceIn(-2900.0, 2900.0),
            "vy" to vy.toDouble().coerceIn(-2900.0, 2900.0),
            "hp" to hp.coerceIn(0, 100).toLong(),
            "maxHp" to maxHp.coerceIn(2, 100).toLong(),
            "shield" to shield.coerceIn(0, 100).toLong(),
            "score" to score.coerceIn(0, 10_000_000).toLong(),
            "facingRight" to facingRight,
            "alive" to alive,
            "shotSeq" to shotSeq.coerceIn(0L, 10_000_000L),
            "shotX" to shotX.toDouble().coerceIn(-450.0, 2450.0),
            "shotY" to shotY.toDouble().coerceIn(-450.0, 2450.0),
            "shotVx" to shotVx.toDouble().coerceIn(-3900.0, 3900.0),
            "shotVy" to shotVy.toDouble().coerceIn(-3900.0, 3900.0),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docPath)
      Result.failure(e)
    }
  }

  suspend fun leaveRoom(room: RoomDoc): Result<Unit> {
    val uid = auth.currentUser?.uid ?: return Result.success(Unit)
    val roomRef = db.collection("rooms").document(room.roomCode)
    return try {
      if (room.hostUid == uid) {
        // Delete player docs first (host has delete permission), then room
        runCatching {
          if (room.guestUid.isNotEmpty()) {
            roomRef.collection("players").document(room.guestUid).delete().await()
          }
        }
        runCatching {
          roomRef.collection("players").document(uid).delete().await()
        }
        roomRef.delete().await()
      } else if (room.guestUid == uid) {
        if (room.roomStatus() == RoomStatus.WAITING) {
          val hostMembers = listOf(room.hostUid)
          val batch = db.batch()
          batch.update(
            roomRef,
            mapOf(
              "guestUid" to "",
              "guestName" to "",
              "memberUids" to hostMembers,
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
          batch.update(
            roomRef.collection("players").document(room.hostUid),
            mapOf(
              "memberUids" to hostMembers,
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
          batch.delete(roomRef.collection("players").document(uid))
          batch.commit().await()
        } else if (room.roomStatus() == RoomStatus.PLAYING) {
          roomRef.update(
            mapOf(
              "status" to RoomStatus.FINISHED.key,
              "winnerUid" to room.hostUid,
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          ).await()
        }
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, "rooms/${room.roomCode}")
      Result.failure(e)
    }
  }

  private fun initialPlayerPayload(
    uid: String,
    roomId: String,
    memberUids: List<String>,
    displayName: String,
    skinKey: String,
  ): Map<String, Any> = mapOf(
    "uid" to uid,
    "roomId" to roomId,
    "memberUids" to memberUids,
    "displayName" to displayName,
    "skinKey" to skinKey,
    "roomGx" to 0L,
    "roomGy" to 0L,
    "x" to 120.0,
    "y" to 420.0,
    "vx" to 0.0,
    "vy" to 0.0,
    "hp" to 6L,
    "maxHp" to 6L,
    "shield" to 2L,
    "score" to 0L,
    "facingRight" to true,
    "alive" to true,
    "shotSeq" to 0L,
    "shotX" to 0.0,
    "shotY" to 0.0,
    "shotVx" to 0.0,
    "shotVy" to 0.0,
    "createdAt" to FieldValue.serverTimestamp(),
    "updatedAt" to FieldValue.serverTimestamp(),
  )

  private fun sanitizeDisplayName(raw: String): String =
    raw.trim().ifBlank { "Боец TFOMI" }.take(36)

  private fun sanitizeSkinKey(raw: String): String =
    raw.trim().ifBlank { "meat_boy" }.take(30)

  private fun generateRoomCode(): String {
    val chars = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    val suffix = (1..4).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    return "TF$suffix"
  }

  private companion object {
    val ROOM_ID_REGEX = Regex("^[a-zA-Z0-9_\\-]+$")
  }
}
