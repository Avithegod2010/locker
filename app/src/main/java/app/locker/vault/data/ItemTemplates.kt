package app.locker.vault.data

/**
 * Structured starting points for common logins and notes. Templates only pre-fill the
 * editor; every value stays editable and nothing is written until the user taps Save.
 */
data class ItemTemplate(
    val name: String,
    val title: String,
    val body: String = "",
    val username: String = "",
    val password: String = "",
    val website: String = "",
    val location: String = "",
    val fields: List<CustomField> = emptyList()
)

object ItemTemplates {
    private val login = ItemTemplate(
        name = "Login",
        title = ""
    )

    private val wifi = ItemTemplate(
        name = "Wi-Fi",
        title = "Home Wi-Fi",
        body = "Guest network and router administration details.",
        username = "home",
        website = "192.168.1.1",
        fields = listOf(
            CustomField("Network name (SSID)", ""),
            CustomField("Network password", "", sensitive = true),
            CustomField("Security", "WPA2 / WPA3"),
            CustomField("Router location", "")
        )
    )

    private val bank = ItemTemplate(
        name = "Bank",
        title = "Bank account",
        body = "Net banking, cards, and branch contact details.",
        fields = listOf(
            CustomField("Bank", ""),
            CustomField("Account number", "", sensitive = true),
            CustomField("Customer ID", ""),
            CustomField("IFSC / SWIFT", ""),
            CustomField("Branch", ""),
            CustomField("Support phone", "")
        )
    )

    private val insurance = ItemTemplate(
        name = "Insurance",
        title = "Insurance policy",
        body = "Policy, renewal, and claim details.",
        fields = listOf(
            CustomField("Provider", ""),
            CustomField("Policy number", "", sensitive = true),
            CustomField("Coverage", ""),
            CustomField("Renews", ""),
            CustomField("Agent phone", "")
        )
    )

    private val identity = ItemTemplate(
        name = "ID / passport",
        title = "Passport",
        body = "Keep the physical document in a safe place.",
        fields = listOf(
            CustomField("Document number", "", sensitive = true),
            CustomField("Issued", ""),
            CustomField("Expires", ""),
            CustomField("Issuing authority", "")
        )
    )

    private val noteTemplates = listOf(
        ItemTemplate(
            name = "Recovery codes",
            title = "Recovery codes",
            body = "One code per line. Keep this note offline."
        ),
        ItemTemplate(
            name = "Emergency contacts",
            title = "Emergency contacts",
            body = "Name — relationship — phone\n\n"
        ),
        ItemTemplate(
            name = "Travel info",
            title = "Travel information",
            body = "Bookings, addresses, and local emergency numbers.\n\n"
        ),
        ItemTemplate(
            name = "Wi-Fi guest",
            title = "Guest Wi-Fi",
            body = "Network name:\nPassword:\nValid until:"
        )
    )

    fun forType(type: VaultType): List<ItemTemplate> = when (type) {
        VaultType.SECRET -> listOf(login, wifi, bank, insurance, identity)
        VaultType.NOTE -> noteTemplates
        else -> emptyList()
    }
}
