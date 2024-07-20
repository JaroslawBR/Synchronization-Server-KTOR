package example.com.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.security.MessageDigest

object DataStorage {
    private val taskList: MutableMap<String, Task> = mutableMapOf() // id - string
    private var hashTask: String? = null
    private val oldHashList: MutableSet<String> = mutableSetOf()
    private val usersList: MutableList<User> = mutableListOf(User("Admin", "adminpass", Access.ADMIN))

    private val idListToIgnore: MutableSet<String> = mutableSetOf()
    private val lock = Any()
    private val gson = Gson()

    init {
        loadFromFile()
    }

    private fun saveToFile() {
        synchronized(lock) {
            GcsUtils.uploadJsonToGcs("taskList.json", taskList)
            GcsUtils.uploadJsonToGcs("hashTask.json", hashTask)
            GcsUtils.uploadJsonToGcs("oldHashList.json", oldHashList)
            GcsUtils.uploadJsonToGcs("usersList.json", usersList)
        }
    }

    private fun loadFromFile() {
        synchronized(lock) {
            try {
                GcsUtils.downloadJsonFromGcs("taskList.json")?.let {
                    val taskType = object : TypeToken<MutableMap<String, Task>>() {}.type
                    val loadedTaskList: MutableMap<String, Task> = gson.fromJson(it, taskType)
                    taskList.clear()
                    taskList.putAll(loadedTaskList)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            try {
                GcsUtils.downloadJsonFromGcs("hashTask.json")?.let {
                    hashTask = gson.fromJson(it, String::class.java)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            try {
                GcsUtils.downloadJsonFromGcs("oldHashList.json")?.let {
                    val oldHashType = object : TypeToken<MutableSet<String>>() {}.type
                    val loadedOldHashList: MutableSet<String> = gson.fromJson(it, oldHashType)
                    oldHashList.clear()
                    oldHashList.addAll(loadedOldHashList)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            try {
                GcsUtils.downloadJsonFromGcs("usersList.json")?.let {
                    val userType = object : TypeToken<MutableList<User>>() {}.type
                    val loadUsersList: MutableList<User> = gson.fromJson(it, userType)
                    usersList.clear()
                    usersList.addAll(loadUsersList)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addUser(user: User): String {
        synchronized(lock) {
            usersList.add(user)
            saveToFile()
            return "user name:${user.name}, access: ${user.access} was added to database"
        }
    }

    fun authenticate(name: String, password: String): User? {
        return usersList.find { it.name == name && it.password == password }
    }

    fun getUser(name: String): User? {
        return usersList.find { it.name == name }
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

    fun forceClearUpdate(newTaskList: MutableMap<String, Task>): String {
        synchronized(lock) {
            replaceTaskList(newTaskList)
            removeIdFromIdListToIgnore(newTaskList.keys)
            hashTaskList()
        }
        saveToFile()
        return "filesSynchronized"
    }

    private fun replaceTaskList(newTaskList: MutableMap<String, Task>) {
        taskList.clear()
        taskList.putAll(newTaskList)
    }

    private fun removeIdFromIdListToIgnore(idList: Set<String>) {
        val iterator = idListToIgnore.iterator()
        while (iterator.hasNext()) {
            val id = iterator.next()
            if (id in idList) {
                iterator.remove()
            }
        }
    }

    fun addEditNewTask(newTaskList: MutableMap<String, Task>, receivedHash: String): String {
        if (taskList == newTaskList) {
            return "filesSynchronized"
        }
        synchronized(lock) {
            newTaskList.forEach { (key, value) ->
                if (!idListToIgnore.contains(key)) {
                    if (taskList.containsKey(key)) {
                        updateTaskIfNecessary(key, value)
                    } else {
                        addEditTask(key, value)
                    }
                }
            }
            hashTaskList()
            saveToFile()
            return if (hashTask == receivedHash) "filesSynchronized" else "downlandFile"
        }
    }

    private fun updateTaskIfNecessary(key: String, value: Task) {
        val existingTask = taskList[key]
        if (existingTask != value) {
            when {
                existingTask?.lastEdit == null -> addEditTask(key, value)
                value.lastEdit != null && isNewerEdit(existingTask.lastEdit, value.lastEdit) -> addEditTask(key, value)
            }
        }
    }

    private fun isNewerEdit(existingEdit: EditInfo?, newEdit: EditInfo?): Boolean {
        return (existingEdit?.timeStamp ?: Long.MIN_VALUE) <= (newEdit?.timeStamp ?: Long.MIN_VALUE)
    }

    private fun addEditTask(key: String, value: Task) {
        taskList[key] = value
    }

    fun removeFromList(idToRemoveList: List<String>, receivedHash: String): String {
        synchronized(lock) {
            idToRemoveList.forEach { id ->
                removeTask(id)
                idListToIgnore.add(id)
            }
            hashTaskList()
            saveToFile()
            return if (hashTask == receivedHash) "filesSynchronized" else "downlandFile"
        }
    }

    private fun removeTask(id: String) {
        taskList.remove(id)
    }

    fun returnListSynchronizedList(): MutableMap<String, Task> {
        synchronized(lock) {
            return taskList.toMutableMap()
        }
    }

    fun returnIdToIgnore(): MutableSet<String> {
        synchronized(lock) {
            return idListToIgnore.toMutableSet()
        }
    }

    fun checkReceiveHash(receivedHash: String): String {
        return when {
            receivedHash == "" -> "downlandFile"
            hashTask == null -> "sendFile"
            hashTask == receivedHash -> "filesSynchronized"
            oldHashList.contains(receivedHash) -> "downlandFile"
            else -> "sendFile"
        }
    }

    private fun hashTaskList() {
        val digest = MessageDigest.getInstance("SHA-256")
        taskList.toSortedMap().forEach { (key, task) ->
            digest.update(key.toByteArray())
            digest.update(task.title.toByteArray())
            digest.update(task.text.toByteArray())
            task.lastEdit?.let {
                digest.update(it.userId.toByteArray())
                digest.update(it.timeStamp.toString().toByteArray())
            }
        }
        updateHash(digest.digest().joinToString("") { "%02x".format(it) })
    }

    private fun updateHash(string: String) {
        hashTask?.let { oldHashList.add(it) }
        hashTask = string
    }
}





