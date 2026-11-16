package app.locker.vault.data

/**
 * One plainly marked example item shown in a trial vault so that the very first screen a
 * new user sees is not an empty list. Samples are ordinary items carrying [TrialContent.TAG],
 * so they can be edited, tagged, or deleted exactly like real ones.
 */
data class TrialSample(
    val type: VaultType,
    val title: String,
    val body: String = "",
    val username: String = "",
    val password: String = "",
    val location: String = "",
    val website: String = "",
    val fields: List<CustomField> = emptyList(),
    val daysAgo: Int = 0
)

/**
 * Starter content for the public-password trial vault. Everything here is invented: no real
 * credentials, no personal data, and nothing that would leak if the vault were opened by a
 * stranger. Each sample keeps a backdated timestamp so the trial looks like a lived-in vault
 * rather than a freshly generated demo.
 */
object TrialContent {
    /** Tag applied to every seeded sample, so they are obvious and easy to remove. */
    const val TAG = "Sample"

    val samples: List<TrialSample> = listOf(
        TrialSample(
            type = VaultType.NOTE,
            title = "Welcome to Locker",
            body = "This is a temporary vault so you can look around.\n\n" +
                "Everything you see is encrypted on this phone before it is written to disk. " +
                "Nothing is uploaded, and the app has no internet permission at all.\n\n" +
                "Set your own password from Security to keep this vault and remove the trial reminders.",
            daysAgo = 0
        ),
        TrialSample(
            type = VaultType.SECRET,
            title = "Example login",
            username = "demo@example.com",
            password = "Sunflower-Trial-42",
            website = "https://example.com",
            body = "A sample secret item. Use the copy button, then press Edit to see how a " +
                "password item is laid out.",
            daysAgo = 1
        ),
        TrialSample(
            type = VaultType.SECRET,
            title = "Home Wi-Fi (sample)",
            username = "home",
            password = "not-a-real-password",
            website = "192.168.1.1",
            body = "Wi-Fi entries store the network name, password, and router address.",
            daysAgo = 5
        ),
        TrialSample(
            type = VaultType.THING,
            title = "Passport (sample)",
            location = "Bedroom drawer",
            body = "Things are for real-world objects: where it is, when it expires, and any " +
                "reference numbers you would otherwise forget.",
            fields = listOf(
                CustomField("Expires", "2031"),
                CustomField("Number", "P-000000")
            ),
            daysAgo = 12
        ),
        TrialSample(
            type = VaultType.THING,
            title = "Bike lock code (sample)",
            location = "Garage shelf",
            body = "A short note stored with the thing it belongs to.",
            fields = listOf(CustomField("Code", "0000")),
            daysAgo = 20
        ),
        TrialSample(
            type = VaultType.NOTE,
            title = "How collections work",
            body = "Collections group related items, and they can contain other collections.\n\n" +
                "Open the drawer to create one, then move items into it from the item menu " +
                "or by long-pressing to select several at once.\n\n" +
                "Tags cut across collections, so the same item can appear in two different views.",
            daysAgo = 30
        )
    )
}
