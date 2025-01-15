package com.example.models

import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

class TTTGame {

    private val state = MutableStateFlow(GameState())

    private val playerSockets = ConcurrentHashMap<Char, WebSocketSession>()

    private val gameScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var delayGameJob: Job? = null

    init {
        // on each generation we call broadcast func
        // i.e, whenever our state changes we call our broadcast func with that corresponding state
        //  gameScope is coroutine where this broadcast is running
        state.onEach(::broadcast).launchIn(gameScope)
    }

    fun connectPlayer(session: WebSocketSession): Char? {
        val isPlayerA = state.value.connectedPlayers.any { it == 'X' }
        val player = if (isPlayerA) 'O' else 'X'

        state.update {
            if (state.value.connectedPlayers.contains(player)) { // this will make sure we have only 2 players
                return null
            }
            // if above condition would not be made we could have connected 3 players, first x then o and in else x again
            if (!playerSockets.containsKey(player)) {
                playerSockets[player] = session
            }

            it.copy(connectedPlayers = it.connectedPlayers + player)
        }
        return player
    }

    fun disconnectPlayer(player: Char) {
        playerSockets.remove(player)
        state.update {
            it.copy(connectedPlayers = it.connectedPlayers - player)
        }
    }

    // fun to send game state to players
    suspend fun broadcast(state: GameState) {
        playerSockets.values.forEach { socket ->
            socket.send(
                Json.encodeToString(state)
            )
        }
    }

    fun finishTurn(player: Char, x: Int, y: Int) {
        if (state.value.field[y][x] != null  ||  state.value.winningPlayer != null) return

        if (state.value.playerAtTurn != player) return

        val currentPlayer = state.value.playerAtTurn
        state.update {
            // updated the specific field to the player that make that turn and saved it to newField
            val newField = it.field.also { field ->
                field[y][x] = currentPlayer
            }
            // if all field in newField is not null so board is full
            val isBoardFull = newField.all { it != null }
            if (isBoardFull) {
                startNewRoundDelay()
            }
            it.copy(
                playerAtTurn = if (currentPlayer == 'X') 'O' else 'X',
                field = newField,
                isBoardFull = isBoardFull,
                winningPlayer = getWinningPlayer()?.also {
                    startNewRoundDelay()
                }
            )
        }
    }

    private fun getWinningPlayer(): Char? {
        val filed = state.value.field
        return if (filed[0][0] != null && filed[0][0] == filed[0][1] && filed[0][1] == filed[0][2]) {
            filed[0][0]
        } else if (filed[1][0] != null && filed[1][0] == filed[1][1] && filed[1][1] == filed[1][2]) {
            filed[1][0]
        } else if (filed[2][0] != null && filed[2][0] == filed[2][1] && filed[2][1] == filed[2][2]) {
            filed[2][0]
        } else if (filed[0][0] != null && filed[0][0] == filed[1][0] && filed[1][0] == filed[2][0]) {
            filed[0][0]
        } else if (filed[0][1] != null && filed[0][1] == filed[1][1] && filed[1][1] == filed[2][1]) {
            filed[0][1]
        } else if (filed[0][2] != null && filed[0][2] == filed[1][2] && filed[1][2] == filed[2][2]) {
            filed[0][2]
        } else if (filed[0][0] != null && filed[0][0] == filed[1][1] && filed[1][1] == filed[2][2]) {
            filed[0][0]
        } else if (filed[0][2] != null && filed[0][2] == filed[1][1] && filed[1][1] == filed[2][0]) {
            filed[0][2]
        } else null
    }

    private fun startNewRoundDelay() {
        delayGameJob?.cancel()
        delayGameJob = gameScope.launch {
            delay(5000L)
            state.update {
                it.copy(
                    playerAtTurn = 'X',
                    field = GameState.emptyField(),
                    winningPlayer = null,
                    isBoardFull = false 
                )
            }
        }
    }
}