package com.example

import com.example.models.TTTGame
import io.ktor.server.application.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    val game = TTTGame()

    configureMonitoring()
    configureSockets()
    configureSerialization()
    configureRouting(game)
}
