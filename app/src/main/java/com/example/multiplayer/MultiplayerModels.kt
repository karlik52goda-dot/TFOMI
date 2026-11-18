package com.example.multiplayer

import androidx.annotation.Keep
import com.example.game.model.PlayerSkin
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

enum class MatchMode(val key: String, val titleRu: String, val descRu: String) {
  COOP("coop", "КООП", "Совместный штурм общего подземелья"),
  PVP("pvp", "ДУЭЛЬ PvP", "Кто кого: бой слёзами и стройкой 1 на 1");

  companion object {
    fun fromKey(raw: String?): MatchMode = entries.firstOrNull { it.key == raw } ?: COOP
  }
}

enum class RoomStatus(val key: String) {
  WAITING("waiting"),
  PLAYING("playing"),
  FINISHED("finished");

  companion object {
    fun fromKey(raw: String?): RoomStatus = entries.firstOrNull { it.key == raw } ?: WAITING
  }
}

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val message: String) : UiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class RoomDoc(
  @DocumentId val id: String = "",
  val roomCode: String = "",
  val hostUid: String = "",
  val hostName: String = "",
  val guestUid: String = "",
  val guestName: String = "",
  val mode: String = MatchMode.COOP.key,
  val status: String = RoomStatus.WAITING.key,
  val floor: Long = 1L,
  val seed: Long = 1L,
  val winnerUid: String = "",
  val memberUids: List<String> = emptyList(),
  val createdAt: Timestamp? = null,
  val updatedAt: Timestamp? = null,
) {
  fun matchMode(): MatchMode = MatchMode.fromKey(mode)
  fun roomStatus(): RoomStatus = RoomStatus.fromKey(status)
}

@Keep
@IgnoreExtraProperties
data class RoomPlayer(
  @DocumentId val id: String = "",
  val uid: String = "",
  val roomId: String = "",
  val memberUids: List<String> = emptyList(),
  val displayName: String = "Игрок",
  val skinKey: String = PlayerSkin.MEAT_BOY.key,
  val roomGx: Long = 0L,
  val roomGy: Long = 0L,
  val x: Double = 120.0,
  val y: Double = 420.0,
  val vx: Double = 0.0,
  val vy: Double = 0.0,
  val hp: Long = 6L,
  val maxHp: Long = 6L,
  val shield: Long = 2L,
  val score: Long = 0L,
  val facingRight: Boolean = true,
  val alive: Boolean = true,
  val shotSeq: Long = 0L,
  val shotX: Double = 0.0,
  val shotY: Double = 0.0,
  val shotVx: Double = 0.0,
  val shotVy: Double = 0.0,
  val createdAt: Timestamp? = null,
  val updatedAt: Timestamp? = null,
) {
  fun skin(): PlayerSkin = PlayerSkin.fromKey(skinKey)
}

data class ActiveMultiplayerSession(
  val roomId: String,
  val localUid: String,
  val isHost: Boolean,
  val mode: MatchMode,
  val seed: Long,
)
