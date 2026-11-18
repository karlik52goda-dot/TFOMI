package com.example.multiplayer

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class MultiplayerViewModel(
  private val repository: MultiplayerRepository,
  val currentUserId: String,
) : ViewModel() {

  private val _activeRoomId = MutableStateFlow<String?>(null)
  val activeRoomId: StateFlow<String?> = _activeRoomId.asStateFlow()

  private val _isBusy = MutableStateFlow(false)
  val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

  private val _errorBanner = MutableStateFlow<String?>(null)
  val errorBanner: StateFlow<String?> = _errorBanner.asStateFlow()

  val waitingRoomsState: StateFlow<UiState<List<RoomDoc>>> = repository.observeWaitingRooms()
    .map<List<RoomDoc>, UiState<List<RoomDoc>>> { UiState.Success(it) }
    .catch { error ->
      val firestoreError = generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
      if (firestoreError == null && error is CancellationException) throw error
      val rootError = firestoreError ?: error
      Log.w(TAG, "Error observing waiting rooms", rootError)
      emit(UiState.Error(rootError.message ?: "Не удалось загрузить список комнат"))
    }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
      initialValue = UiState.Loading,
    )

  val activeRoomState: StateFlow<UiState<RoomDoc?>> = _activeRoomId
    .flatMapLatest { roomId ->
      if (roomId == null) flowOf<UiState<RoomDoc?>>(UiState.Success(null))
      else repository.observeRoom(roomId)
        .map<RoomDoc?, UiState<RoomDoc?>> { UiState.Success(it) }
        .catch { error ->
          val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val rootError = firestoreError ?: error
          Log.w(TAG, "Error observing room $roomId", rootError)
          emit(UiState.Error(rootError.message ?: "Ошибка подключения к комнате"))
        }
    }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
      initialValue = UiState.Success(null),
    )

  val roomPlayersState: StateFlow<UiState<List<RoomPlayer>>> = _activeRoomId
    .flatMapLatest { roomId ->
      if (roomId == null) flowOf<UiState<List<RoomPlayer>>>(UiState.Success(emptyList()))
      else repository.observeRoomPlayers(roomId, currentUserId)
        .map<List<RoomPlayer>, UiState<List<RoomPlayer>>> { UiState.Success(it) }
        .catch { error ->
          val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val rootError = firestoreError ?: error
          Log.w(TAG, "Error observing room players", rootError)
          emit(UiState.Error(rootError.message ?: "Ошибка синхронизации игроков"))
        }
    }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
      initialValue = UiState.Success(emptyList()),
    )

  fun clearError() {
    _errorBanner.value = null
  }

  fun createRoom(displayName: String, skinKey: String, mode: MatchMode) {
    if (_isBusy.value) return
    _isBusy.value = true
    _errorBanner.value = null
    viewModelScope.launch {
      val result = repository.createRoom(displayName, skinKey, mode)
      _isBusy.value = false
      result.onSuccess { room ->
        _activeRoomId.value = room.roomCode
      }.onFailure { err ->
        _errorBanner.value = err.localizedMessage ?: "Не удалось создать комнату"
      }
    }
  }

  fun joinRoom(code: String, displayName: String, skinKey: String) {
    if (_isBusy.value) return
    _isBusy.value = true
    _errorBanner.value = null
    viewModelScope.launch {
      val result = repository.joinRoom(code, displayName, skinKey)
      _isBusy.value = false
      result.onSuccess { room ->
        _activeRoomId.value = room.roomCode
      }.onFailure { err ->
        _errorBanner.value = err.localizedMessage ?: "Не удалось войти в комнату"
      }
    }
  }

  fun setRoomMode(roomId: String, mode: MatchMode) {
    viewModelScope.launch {
      repository.updateMode(roomId, mode).onFailure { err ->
        _errorBanner.value = err.localizedMessage ?: "Не удалось сменить режим"
      }
    }
  }

  fun startMatch(room: RoomDoc) {
    if (_isBusy.value) return
    _isBusy.value = true
    _errorBanner.value = null
    val freshSeed = Random.nextInt(10_000, 999_999).toLong()
    viewModelScope.launch {
      val result = repository.startMatch(room.roomCode, room.matchMode(), freshSeed)
      _isBusy.value = false
      result.onFailure { err ->
        _errorBanner.value = err.localizedMessage ?: "Не удалось запустить матч"
      }
    }
  }

  fun updateSkinInLobby(roomId: String, displayName: String, skinKey: String) {
    viewModelScope.launch {
      repository.syncPlayerState(
        roomId = roomId,
        displayName = displayName,
        skinKey = skinKey,
        roomGx = 0,
        roomGy = 0,
        x = 120f,
        y = 420f,
        vx = 0f,
        vy = 0f,
        hp = 6,
        maxHp = 6,
        shield = 2,
        score = 0,
        facingRight = true,
        alive = true,
        shotSeq = 0L,
        shotX = 0f,
        shotY = 0f,
        shotVx = 0f,
        shotVy = 0f,
      )
    }
  }

  fun leaveActiveRoom(room: RoomDoc?) {
    val targetRoom = room
    _activeRoomId.value = null
    if (targetRoom != null) {
      viewModelScope.launch {
        repository.leaveRoom(targetRoom)
      }
    }
  }

  suspend fun leaveRoomBlocking(room: RoomDoc?) {
    _activeRoomId.value = null
    if (room != null) {
      repository.leaveRoom(room)
    }
  }

  fun detachRoomLocally() {
    _activeRoomId.value = null
  }

  companion object {
    private const val TAG = "MultiplayerVM"
    private const val STOP_TIMEOUT_MILLIS = 5000L

    fun factory(currentUserId: String) = viewModelFactory {
      initializer {
        val app = checkNotNull(this[APPLICATION_KEY]) {
          "APPLICATION_KEY missing from CreationExtras"
        }
        val databaseId = app.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)
        MultiplayerViewModel(MultiplayerRepository(db), currentUserId)
      }
    }
  }
}
