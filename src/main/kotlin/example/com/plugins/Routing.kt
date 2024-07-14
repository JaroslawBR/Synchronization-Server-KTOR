package example.com.plugins

import example.com.data.Task
import example.com.data.TaskStorage
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        get("/") {
            println("Test")
            call.respondText("Hello Users!!")
        }
        get("/checkTaskList"){
            val receivedHash = call.request.queryParameters["hash"] ?: ""
            val result = TaskStorage.checkReceiveHash(receivedHash)
            call.respond(result)
        }



        post("/updateTaskList") {
            val newTaskList = call.receive<MutableMap<String, Task>>()
            val receivedHash = call.request.headers["Hash"] ?: ""
            val result = TaskStorage.addEditNewTask(newTaskList, receivedHash)
            call.respond(result)
        }

        post("/clearUpdateTaskList") {
            val newTaskList = call.receive<MutableMap<String, Task>>()
            val result = TaskStorage.forceClearUpdate(newTaskList)
            call.respond(result)
        }

        get("/downlandTaskList") {
            val taskList = TaskStorage.returnListSynchronizedList()
            call.respond(taskList)
        }

        get("/downlandIgnoreTask"){
            val idListToIgnore = TaskStorage.returnIdToIgnore()
            call.respond(idListToIgnore)
        }
    }
}
