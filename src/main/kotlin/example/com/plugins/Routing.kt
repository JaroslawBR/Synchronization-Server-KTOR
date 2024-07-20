package example.com.plugins

import example.com.data.Access
import example.com.data.DataStorage
import example.com.data.DataStorage.authenticate
import example.com.data.GcsUtils.downloadIcoFromGcs
import example.com.data.Task
import example.com.data.User
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    install(Authentication) {
        basic("auth-basic") {
            realm = "Access to the '/tasks' path"
            validate { credentials ->
                val user = authenticate(credentials.name, credentials.password)
                if (user != null) {
                    UserIdPrincipal(credentials.name)
                } else {
                    null
                }
            }
        }
    }

    routing {

        get("/") {
            call.respondText("Witaj użytkowniku!!")
        }

        get("favicon.ico") {
            val fullPath = "static/favicon.ico"
            val content = downloadIcoFromGcs(fullPath)
            if (content != null) {
                call.respondBytes(content, ContentType.Image.XIcon)
            } else {
                call.respond(HttpStatusCode.NotFound, "Favicon not found")
            }
        }

        staticResources("/", "static")

        authenticate("auth-basic") {


            get("/checkTaskList") {
                authorize(call, Access.SPECTATOR) {
                    val receivedHash = call.request.queryParameters["hash"] ?: ""
                    val result = DataStorage.checkReceiveHash(receivedHash)
                    call.respond(result)
                }
            }

            post("/updateTaskList") {
                authorize(call, Access.EDITOR) {
                    val newTaskList = call.receive<MutableMap<String, Task>>()
                    val receivedHash = call.request.headers["Hash"] ?: ""
                    val result = DataStorage.addEditNewTask(newTaskList, receivedHash)
                    call.respond(result)
                }
            }

            post("/clearUpdateTaskList") {
                authorize(call, Access.MODERATOR) {
                    val newTaskList = call.receive<MutableMap<String, Task>>()
                    val result = DataStorage.forceClearUpdate(newTaskList)
                    call.respond(result)
                }
            }

            get("/downlandTaskList") {
                authorize(call, Access.SPECTATOR) {
                    val taskList = DataStorage.returnListSynchronizedList()
                    call.respond(taskList)
                }
            }

            get("/downlandIgnoreTask") {
                authorize(call, Access.EDITOR) {
                    val idListToIgnore = DataStorage.returnIdToIgnore()
                    call.respond(idListToIgnore)
                }
            }

            post("/updateIgnoreID") {
                authorize(call, Access.EDITOR) {
                    val idListToIgnore = call.receive<List<String>>()
                    val receivedHash = call.request.headers["Hash"] ?: ""
                    val result = DataStorage.removeFromList(idListToIgnore, receivedHash)
                    call.respond(result)
                }
            }

            post("/addNewUsers"){
                authorize(call, Access.ADMIN){
                    val user = call.receive<User>()
                    val result = DataStorage.addUser(user)
                    call.respond(result)


                }

            }
        }
    }
}

suspend inline fun authorize(call: ApplicationCall, requiredAccess: Access, crossinline block: suspend () -> Unit) {
    val principal = call.principal<UserIdPrincipal>() ?: return call.respondText("Unauthorized", status = HttpStatusCode.Unauthorized)
    val user = DataStorage.getUser(principal.name)
    if (DataStorage.hasAccess(user, requiredAccess)) {
        block()
    } else {
        call.respondText("Forbidden", status = HttpStatusCode.Forbidden)
    }
}
