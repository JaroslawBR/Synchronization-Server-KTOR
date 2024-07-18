package example.com.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.security.MessageDigest

data class Task(
    var title: String = "",
    var text: String = "",
    var lastEdit: EditInfo? = null
)

data class EditInfo(
    var userId: String = "",
    var timeStamp: Long = 0
)

object TaskStorage {
    private val taskList: MutableMap<String, Task> = mutableMapOf() // id - string
    private var hashTask: String? = null
    private val oldHashList: MutableSet<String> = mutableSetOf()

    private val idListToIgnore: MutableSet<String> = mutableSetOf()
    internal val lock = Any()
    val gson = Gson()

    const val folderPath = "file"
    private const val taskListFile = "$folderPath/taskList.json"
    private const val hashTaskFile = "$folderPath/hashTask.json"
    private const val oldHashListFile = "$folderPath/oldHashList.json"

    fun start(){
        val folder = File(folderPath)
        if (!folder.exists()) {
            folder.mkdirs()
        }
        loadFromFile()
    }

    private fun saveToFile() {
        synchronized(lock) {
            File(taskListFile).writeText(gson.toJson(taskList))
            File(hashTaskFile).writeText(gson.toJson(hashTask))
            File(oldHashListFile).writeText(gson.toJson(oldHashList))
        }
    }

    private fun loadFromFile() {
        synchronized(lock) {
            try {
                if (File(taskListFile).exists()) {
                    val taskType = object : TypeToken<MutableMap<String, Task>>() {}.type
                    val loadedTaskList: MutableMap<String, Task> =
                        gson.fromJson(File(taskListFile).readText(), taskType)
                    taskList.clear()
                    taskList.putAll(loadedTaskList)
                }
            } catch (e: Exception) {
                saveToFile()
            }

            try {
                if (File(hashTaskFile).exists()) {
                    hashTask = gson.fromJson(File(hashTaskFile).readText(), String::class.java)
                }
            } catch (e: Exception) {
                saveToFile()
            }

            try {
                if (File(oldHashListFile).exists()) {
                    val oldHashType = object : TypeToken<MutableSet<String>>() {}.type
                    val loadedOldHashList: MutableSet<String> = gson.fromJson(File(oldHashListFile).readText(), oldHashType)
                    oldHashList.clear()
                    oldHashList.addAll(loadedOldHashList)
                }
            } catch (e: Exception) {
                saveToFile()
            }
        }
    }


    fun forceClearUpdate(newTaskList: MutableMap<String, Task>): String {
        synchronized(lock) {
            replaceTaskList(newTaskList)
            removeIdFromIdListToIgnore(newTaskList.keys)
            hashTaskList(taskList)
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
            hashTaskList(taskList)
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
            hashTaskList(taskList)
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

    private fun hashTaskList(taskList: Map<String, Task>) {
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




