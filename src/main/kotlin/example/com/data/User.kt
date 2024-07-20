package example.com.data

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