package example.com.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

data class User(
    val name: String = "",
    val password: String = "",
    val access: Access = Access.SPECTATOR
)

enum class Access {
    ADMIN,      // dostęp do wszystkiego
    MODERATOR,  // dostęp do większości
    EDITOR,     // wysyłanie + pobieranie
    SPECTATOR,  // pobieranie
    GUEST       // brak
}

object Authentication1 {


    private val usersList: MutableList<User> = mutableListOf()
    val gson = Gson()
    internal val lock = Any()


    private const val folderPath = "file"
    private const val usersListFile = "${TaskStorage.folderPath}/usersList.json"

    fun start() {
        val folder = File(folderPath)
        if (!folder.exists()) {
            folder.mkdirs()
        }
        loadUsersFromFile()
    }

    private fun saveUsersToFile() {
        synchronized(lock) {
            File(usersListFile).writeText(TaskStorage.gson.toJson(usersList))
        }
    }

    fun addUser(user: User): String {
        synchronized(lock) {
            usersList.add(user)
            saveUsersToFile()
            return "user name:${user.name}, access: ${user.access} was added to database"
        }
    }

    fun authenticate(name: String, password: String): User? {
        return usersList.find { it.name == name && it.password == password }
    }

    fun getUser(name: String): User? {
        return usersList.find { it.name == name }
    }



    private fun loadUsersFromFile() {
        synchronized(lock) {
            try {
                val file = File(usersListFile)
                if (file.exists()) {
                    val json = file.readText()
                    val type = object : TypeToken<List<User>>() {}.type
                    val loadedUsers: List<User> = gson.fromJson(json, type)
                    usersList.clear()
                    usersList.addAll(loadedUsers)
                } else {
                    // Dodaj przykładowych użytkowników, jeśli plik nie istnieje
                    addUser(User("Admin", "adminpass", Access.ADMIN))
                }
            } catch (e: Exception) {
                // Jeśli wystąpił błąd, zapisz pustą listę
                usersList.clear()
                saveUsersToFile()
            }
        }
    }



    fun hasAccess(user: User?, requiredAccess: Access): Boolean {
        if (user == null) return false
        return when (requiredAccess) {
            Access.ADMIN -> user.access == Access.ADMIN
            Access.MODERATOR -> user.access == Access.ADMIN || user.access == Access.MODERATOR
            Access.EDITOR -> user.access == Access.ADMIN || user.access == Access.MODERATOR || user.access == Access.EDITOR
            Access.SPECTATOR -> user.access == Access.ADMIN || user.access == Access.MODERATOR || user.access == Access.EDITOR || user.access == Access.SPECTATOR
            Access.GUEST -> user.access == Access.ADMIN || user.access == Access.MODERATOR || user.access == Access.EDITOR || user.access == Access.SPECTATOR || user.access == Access.GUEST
        }
    }
}