package com.example.multiplayer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsKabaddi
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.game.model.PlayerSkin
import com.example.game.ui.drawSkinCharacter
import com.example.ui.theme.*
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

@Composable
fun MultiplayerHubScreen(
  selectedSkin: PlayerSkin,
  onClose: () -> Unit,
  onMatchStarted: (ActiveMultiplayerSession) -> Unit,
  modifier: Modifier = Modifier,
) {
  val auth = remember { Firebase.auth }
  val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
  val context = LocalContext.current
  val credentialManager = remember(context) { CredentialManager.create(context) }
  val scope = rememberCoroutineScope()

  LaunchedEffect(Unit) {
    if (auth.currentUser == null) {
      attemptAutoSignIn(
        context = context,
        credentialManager = credentialManager,
        onAuthSuccess = {},
        onUnauthenticated = {},
        scope = scope,
      )
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(listOf(Color(0xFF140924), StormVoid, Color(0xFF1B0D2C)))
      )
      .windowInsetsPadding(WindowInsets.safeDrawing)
      .padding(horizontal = 16.dp, vertical = 12.dp),
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 620.dp)
        .align(Alignment.TopCenter),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // Top Header Bar
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(
          onClick = onClose,
          modifier = Modifier
            .size(48.dp)
            .testTag("mp_back_button")
            .clip(RoundedCornerShape(14.dp))
            .background(DungeonSurface)
            .border(1.dp, DungeonBorder, RoundedCornerShape(14.dp)),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.btn_main_menu),
            tint = TextPrimary,
          )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
            text = stringResource(R.string.title_multiplayer),
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
            color = TearCyan,
          )
          Text(
            text = "TFOMI • КООП И ДУЭЛИ",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
          )
        }

        Spacer(Modifier.size(48.dp))
      }

      val user = currentUser
      if (user == null) {
        MultiplayerAuthScreen(
          credentialManager = credentialManager,
          onAuthSuccess = {},
          modifier = Modifier.weight(1f),
        )
      } else {
        key(user.uid) {
          val displayName = user.displayName?.takeIf { it.isNotBlank() } ?: "Боец TFOMI"
          MultiplayerSignedInLobby(
            currentUserId = user.uid,
            displayName = displayName,
            selectedSkin = selectedSkin,
            credentialManager = credentialManager,
            onMatchStarted = onMatchStarted,
          )
        }
      }
    }
  }
}

@Composable
private fun MultiplayerAuthScreen(
  credentialManager: CredentialManager,
  onAuthSuccess: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var isLoading by remember { mutableStateOf(false) }
  var errorMsg by remember { mutableStateOf<String?>(null) }

  Box(
    modifier = modifier.fillMaxWidth(),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(22.dp))
        .background(DungeonSurface.copy(alpha = 0.94f))
        .border(1.5.dp, StormViolet, RoundedCornerShape(22.dp))
        .padding(22.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Box(
        modifier = Modifier
          .size(64.dp)
          .clip(CircleShape)
          .background(TearCyan.copy(alpha = 0.16f))
          .border(1.5.dp, TearCyan, CircleShape),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Filled.Groups,
          contentDescription = null,
          tint = TearCyan,
          modifier = Modifier.size(34.dp),
        )
      }

      Text(
        text = "ВХОД В МУЛЬТИПЛЕЕР",
        style = MaterialTheme.typography.titleLarge,
        color = TextPrimary,
      )
      Text(
        text = "Войдите через Google, чтобы создавать комнаты по коду, играть с друзьями в Кооп или сражаться в Дуэли PvP!",
        style = MaterialTheme.typography.bodyMedium,
        color = TextSecondary,
      )

      errorMsg?.let { err ->
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MeatCrimson.copy(alpha = 0.18f))
            .border(1.dp, MeatCrimson, RoundedCornerShape(12.dp))
            .padding(10.dp),
        ) {
          Text(text = err, style = MaterialTheme.typography.bodyMedium, color = MeatCrimson)
        }
      }

      Button(
        onClick = {
          isLoading = true
          errorMsg = null
          onGoogleSignInClicked(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
              isLoading = false
              onAuthSuccess()
            },
            onAuthError = { msg ->
              isLoading = false
              errorMsg = msg
            },
            scope = scope,
            onAuthCancelled = {
              isLoading = false
            },
          )
        },
        enabled = !isLoading,
        colors = ButtonDefaults.buttonColors(containerColor = TearCyan, contentColor = StormVoid),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("google_sign_in_button"),
      ) {
        if (isLoading) {
          CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            color = StormVoid,
            strokeWidth = 2.dp,
          )
        } else {
          Icon(Icons.Filled.Login, contentDescription = null, modifier = Modifier.size(20.dp))
          Spacer(Modifier.width(8.dp))
          Text(
            text = stringResource(R.string.btn_sign_in_google),
            style = MaterialTheme.typography.labelLarge,
          )
        }
      }
    }
  }
}

@Composable
private fun MultiplayerSignedInLobby(
  currentUserId: String,
  displayName: String,
  selectedSkin: PlayerSkin,
  credentialManager: CredentialManager,
  onMatchStarted: (ActiveMultiplayerSession) -> Unit,
) {
  val mpViewModel: MultiplayerViewModel = viewModel(
    key = currentUserId,
    factory = MultiplayerViewModel.factory(currentUserId),
  )

  val waitingRoomsState by mpViewModel.waitingRoomsState.collectAsStateWithLifecycle()
  val activeRoomState by mpViewModel.activeRoomState.collectAsStateWithLifecycle()
  val roomPlayersState by mpViewModel.roomPlayersState.collectAsStateWithLifecycle()
  val isBusy by mpViewModel.isBusy.collectAsStateWithLifecycle()
  val errorBanner by mpViewModel.errorBanner.collectAsStateWithLifecycle()

  val scope = rememberCoroutineScope()
  var selectedCreateMode by remember { mutableStateOf(MatchMode.COOP) }
  var joinCodeInput by remember { mutableStateOf("") }

  val activeRoom = (activeRoomState as? UiState.Success)?.data
  val players = (roomPlayersState as? UiState.Success)?.data.orEmpty()

  LaunchedEffect(activeRoom?.roomCode, activeRoom?.status, activeRoom?.seed, activeRoom?.mode) {
    val room = activeRoom ?: return@LaunchedEffect
    if (room.roomStatus() == RoomStatus.PLAYING) {
      onMatchStarted(
        ActiveMultiplayerSession(
          roomId = room.roomCode,
          localUid = currentUserId,
          isHost = room.hostUid == currentUserId,
          mode = room.matchMode(),
          seed = room.seed,
        )
      )
    }
  }

  // Account & Skin Strip
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .background(DungeonSurface.copy(alpha = 0.9f))
      .border(1.dp, DungeonBorder, RoundedCornerShape(16.dp))
      .padding(horizontal = 12.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.weight(1f),
    ) {
      Box(
        modifier = Modifier
          .size(34.dp)
          .clip(CircleShape)
          .background(StormVoid)
          .border(1.dp, selectedSkin.accentColor, CircleShape),
        contentAlignment = Alignment.Center,
      ) {
        Canvas(modifier = Modifier.size(24.dp)) {
          drawSkinCharacter(selectedSkin, 0f, 0f, size.width, size.height, facingRight = true)
        }
      }
      Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(13.dp))
          Text(
            text = displayName,
            style = MaterialTheme.typography.labelLarge,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Text(
          text = "Скин: ${selectedSkin.titleRu}",
          style = MaterialTheme.typography.labelSmall,
          color = selectedSkin.accentColor,
        )
      }
    }

    OutlinedButton(
      onClick = {
        val roomToLeave = activeRoom
        signOutFromMultiplayer(
          credentialManager = credentialManager,
          scope = scope,
          onBeforeSignOut = {
            if (roomToLeave != null) {
              mpViewModel.leaveRoomBlocking(roomToLeave)
            }
          },
        )
      },
      border = BorderStroke(1.dp, DungeonBorder),
      contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
      modifier = Modifier
        .height(48.dp)
        .testTag("google_sign_out_button"),
    ) {
      Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
      Spacer(Modifier.width(4.dp))
      Text(stringResource(R.string.btn_sign_out), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
  }

  errorBanner?.let { err ->
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(MeatCrimson.copy(alpha = 0.2f))
        .border(1.dp, MeatCrimson, RoundedCornerShape(12.dp))
        .clickable { mpViewModel.clearError() }
        .padding(10.dp),
    ) {
      Text(text = err, style = MaterialTheme.typography.bodyMedium, color = MeatCrimson)
    }
  }

  if (activeRoom != null) {
    // Active Waiting Room View
    val isHost = activeRoom.hostUid == currentUserId
    val hostPlayer = players.firstOrNull { it.uid == activeRoom.hostUid }
    val guestPlayer = players.firstOrNull { it.uid == activeRoom.guestUid }

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(20.dp))
        .background(DungeonSurface.copy(alpha = 0.94f))
        .border(1.5.dp, TearCyan.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text("КОМНАТА СОЗДАНА", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
          Text(
            text = "КОД: ${activeRoom.roomCode}",
            style = MaterialTheme.typography.headlineMedium,
            color = LootLegendary,
          )
        }
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(StormViolet.copy(alpha = 0.35f))
            .border(1.dp, StormGlow, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
          Text(
            text = activeRoom.matchMode().titleRu,
            style = MaterialTheme.typography.labelLarge,
            color = TextPrimary,
          )
        }
      }

      if (isHost) {
        ModeSelectorRow(
          selected = activeRoom.matchMode(),
          onSelect = { mpViewModel.setRoomMode(activeRoom.roomCode, it) },
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        LobbyPlayerSlotCard(
          roleLabel = "ХОСТ",
          name = activeRoom.hostName,
          skin = hostPlayer?.skin() ?: if (isHost) selectedSkin else PlayerSkin.MEAT_BOY,
          ready = true,
          modifier = Modifier.weight(1f),
        )
        LobbyPlayerSlotCard(
          roleLabel = "ГОСТЬ",
          name = if (activeRoom.guestUid.isNotEmpty()) activeRoom.guestName else "Ожидание игрока...",
          skin = guestPlayer?.skin() ?: PlayerSkin.BANANA,
          ready = activeRoom.guestUid.isNotEmpty(),
          modifier = Modifier.weight(1f),
        )
      }

      if (isHost) {
        Button(
          onClick = { mpViewModel.startMatch(activeRoom) },
          enabled = !isBusy,
          colors = ButtonDefaults.buttonColors(containerColor = MeatCrimson),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .testTag("mp_start_match_button"),
        ) {
          Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = TextPrimary)
          Spacer(Modifier.width(6.dp))
          Text(
            text = if (activeRoom.guestUid.isNotEmpty()) "НАЧАТЬ БОЙ (2/2)!" else "НАЧАТЬ БОЙ (ОЖИДАНИЕ)",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
          )
        }
      } else {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(StormVoid)
            .padding(12.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = "Ждём, пока хост (${activeRoom.hostName}) запустит бой...",
            style = MaterialTheme.typography.bodyMedium,
            color = TearCyan,
          )
        }
      }

      OutlinedButton(
        onClick = { mpViewModel.leaveActiveRoom(activeRoom) },
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("mp_leave_room_button"),
      ) {
        Text("ПОКИНУТЬ КОМНАТУ", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
      }
    }
  } else {
    // Create & Join Controls
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(20.dp))
        .background(DungeonSurface.copy(alpha = 0.92f))
        .border(1.dp, DungeonBorder, RoundedCornerShape(20.dp))
        .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text("СОЗДАТЬ СВОЮ КОМНАТУ", style = MaterialTheme.typography.labelLarge, color = TearCyan)
      ModeSelectorRow(selected = selectedCreateMode, onSelect = { selectedCreateMode = it })

      Button(
        onClick = { mpViewModel.createRoom(displayName, selectedSkin.key, selectedCreateMode) },
        enabled = !isBusy,
        colors = ButtonDefaults.buttonColors(containerColor = MeatCrimson),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(50.dp)
          .testTag("mp_create_room_button"),
      ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = TextPrimary)
        Spacer(Modifier.width(6.dp))
        Text("СОЗДАТЬ КОМНАТУ (${selectedCreateMode.titleRu})", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
      }

      HorizontalDivider(color = DungeonBorder)

      Text("ВОЙТИ ПО КОДУ", style = MaterialTheme.typography.labelLarge, color = LootLegendary)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedTextField(
          value = joinCodeInput,
          onValueChange = { joinCodeInput = it.uppercase().take(10) },
          placeholder = { Text("Например: TF482K", color = TextSecondary) },
          singleLine = true,
          modifier = Modifier
            .weight(1f)
            .testTag("mp_join_code_input"),
        )
        Button(
          onClick = { mpViewModel.joinRoom(joinCodeInput, displayName, selectedSkin.key) },
          enabled = !isBusy && joinCodeInput.trim().length >= 4,
          colors = ButtonDefaults.buttonColors(containerColor = TearCyan, contentColor = StormVoid),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .height(52.dp)
            .testTag("mp_join_code_button"),
        ) {
          Text("ВОЙТИ", style = MaterialTheme.typography.labelLarge)
        }
      }
    }

    // Active Waiting Rooms List
    Text(
      text = "ОТКРЫТЫЕ КОМНАТЫ",
      style = MaterialTheme.typography.labelLarge,
      color = TextPrimary,
    )

    when (val roomsState = waitingRoomsState) {
      is UiState.Loading -> {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
          contentAlignment = Alignment.Center,
        ) {
          CircularProgressIndicator(color = TearCyan)
        }
      }

      is UiState.Error -> {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MeatCrimson.copy(alpha = 0.16f))
            .border(1.dp, MeatCrimson, RoundedCornerShape(14.dp))
            .padding(14.dp),
        ) {
          Text(text = roomsState.message, style = MaterialTheme.typography.bodyMedium, color = MeatCrimson)
        }
      }

      is UiState.Success -> {
        val openRooms = roomsState.data.filter { it.guestUid.isEmpty() }
        if (openRooms.isEmpty()) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(16.dp))
              .background(DungeonSurface.copy(alpha = 0.7f))
              .border(1.dp, DungeonBorder, RoundedCornerShape(16.dp))
              .padding(20.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = "Пока нет открытых комнат — создайте свою и позовите друга по коду!",
              style = MaterialTheme.typography.bodyMedium,
              color = TextSecondary,
            )
          }
        } else {
          LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            items(openRooms, key = { it.roomCode }) { room ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(14.dp))
                  .background(DungeonSurface.copy(alpha = 0.9f))
                  .border(1.dp, DungeonBorder, RoundedCornerShape(14.dp))
                  .clickable(enabled = !isBusy) {
                    mpViewModel.joinRoom(room.roomCode, displayName, selectedSkin.key)
                  }
                  .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "${room.roomCode} • ${room.hostName}",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Text(
                    text = room.matchMode().descRu,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (room.matchMode() == MatchMode.PVP) MeatCrimson else TearCyan,
                  )
                }
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(TearCyan.copy(alpha = 0.2f))
                    .border(1.dp, TearCyan, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                  Text("ВОЙТИ", style = MaterialTheme.typography.labelSmall, color = TearCyan)
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ModeSelectorRow(
  selected: MatchMode,
  onSelect: (MatchMode) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    MatchMode.entries.forEach { mode ->
      val isSel = selected == mode
      val accent = if (mode == MatchMode.PVP) MeatCrimson else TearCyan
      val icon = if (mode == MatchMode.PVP) Icons.Filled.SportsKabaddi else Icons.Filled.Groups
      OutlinedButton(
        onClick = { onSelect(mode) },
        border = BorderStroke(1.2.dp, if (isSel) accent else DungeonBorder),
        colors = ButtonDefaults.outlinedButtonColors(
          containerColor = if (isSel) accent.copy(alpha = 0.18f) else StormVoid,
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
          .weight(1f)
          .height(48.dp),
      ) {
        Icon(icon, contentDescription = null, tint = if (isSel) accent else TextSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
          text = mode.titleRu,
          style = MaterialTheme.typography.labelLarge,
          color = if (isSel) accent else TextPrimary,
        )
      }
    }
  }
}

@Composable
private fun LobbyPlayerSlotCard(
  roleLabel: String,
  name: String,
  skin: PlayerSkin,
  ready: Boolean,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .clip(RoundedCornerShape(14.dp))
      .background(StormVoid.copy(alpha = 0.85f))
      .border(1.dp, if (ready) skin.accentColor else DungeonBorder, RoundedCornerShape(14.dp))
      .padding(10.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Text(text = roleLabel, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    Box(
      modifier = Modifier
        .size(48.dp)
        .clip(CircleShape)
        .background(DungeonSurface),
      contentAlignment = Alignment.Center,
    ) {
      if (ready) {
        Canvas(modifier = Modifier.size(36.dp)) {
          drawSkinCharacter(skin, 0f, 0f, size.width, size.height, facingRight = true)
        }
      } else {
        Text("?", style = MaterialTheme.typography.headlineMedium, color = TextSecondary)
      }
    }
    Text(
      text = name,
      style = MaterialTheme.typography.labelLarge,
      color = if (ready) TextPrimary else TextSecondary,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Text(
      text = if (ready) skin.titleRu else "Свободно",
      style = MaterialTheme.typography.labelSmall,
      color = if (ready) skin.accentColor else TextSecondary,
    )
  }
}
