package example.com

import example.com.data.Authentication1
import example.com.data.TaskStorage
import example.com.plugins.*
import io.ktor.server.application.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    println("serwer wystartował")
    TaskStorage.start()
    Authentication1.start()
    configureSerialization()
    configureRouting()
}
