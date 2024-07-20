package example.com.data

data class Task(
    var title: String = "",
    var text: String = "",
    var lastEdit: EditInfo? = null
)

data class EditInfo(
    var userId: String = "",
    var timeStamp: Long = 0
)