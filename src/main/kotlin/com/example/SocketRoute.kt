package com.example

import com.example.models.MakeTurn
import com.example.models.TTTGame
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.serialization.json.Json

fun Route.socket(game: TTTGame) {
    route("/play") {
        webSocket {
            val player = game.connectPlayer(this)

            if (player == null) {
                close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "2 players are already connected!!"))
                return@webSocket
            }
            try {
                incoming.consumeEach { frame ->  
                    if (frame is Frame.Text) {
                        val action =  extractAction(frame.readText())
                        game.finishTurn(player, action.x, action.y)
                    }
                }
            } catch (e:Exception) {
                e.printStackTrace()
            } finally {
                game.disconnectPlayer(player)
            }
        }
    }
}

private fun extractAction(message: String): MakeTurn {
    // make_turn#{...}
    val type = message.substringBefore("#")
    val body = message.substringAfter("#")
    return if (type == "make_turn") {
        Json.decodeFromString(body)
    } else MakeTurn(-1, -1)
}