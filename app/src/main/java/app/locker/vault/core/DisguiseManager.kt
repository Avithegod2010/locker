package app.locker.vault.core

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Switches the launcher between the real Locker icon and disguised icons (Calculator,
 * Calendar, Notes). Android shows exactly one launcher entry at a time: the real activity is
 * disabled whenever a disguise is active, and the chosen alias is enabled instead. Only the
 * icon and name the launcher shows change; the app itself is untouched and always opens
 * straight into Locker's own lock screen.
 */
object DisguiseManager {
    const val ID_NONE = Disguise.ID_NONE
    const val ID_CALCULATOR = Disguise.ID_CALCULATOR
    const val ID_CALENDAR = Disguise.ID_CALENDAR
    const val ID_NOTES = Disguise.ID_NOTES

    private val aliases = mapOf(
        Disguise.ID_CALCULATOR to "app.locker.vault.AliasCalculator",
        Disguise.ID_CALENDAR to "app.locker.vault.AliasCalendar",
        Disguise.ID_NOTES to "app.locker.vault.AliasNotes"
    )

    fun labelFor(id: String): String = when (id) {
        Disguise.ID_CALCULATOR -> "Calculator"
        Disguise.ID_CALENDAR -> "Calendar"
        Disguise.ID_NOTES -> "Notes"
        else -> "Locker"
    }

    fun iconFor(id: String): String = when (id) {
        Disguise.ID_CALCULATOR -> "ic_launcher_calculator"
        Disguise.ID_CALENDAR -> "ic_launcher_calendar"
        Disguise.ID_NOTES -> "ic_launcher_notes"
        else -> "ic_launcher"
    }

    /**
     * Applies the disguise. Runs synchronously with DONT_KILL_APP so the process, vault key,
     * and any open session survive the switch; launchers usually refresh the icon within a
     * second or two.
     */
    fun apply(context: Context, id: String) {
        val pm = context.packageManager
        val main = ComponentName(context, "app.locker.vault.MainActivity")
        val state = PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        val disabled = PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        val flags = PackageManager.DONT_KILL_APP
        runCatching {
            if (id == Disguise.ID_NONE) {
                pm.setComponentEnabledSetting(main, state, flags)
            } else {
                pm.setComponentEnabledSetting(main, disabled, flags)
            }
            aliases.forEach { (aliasId, aliasClass) ->
                val component = ComponentName(context.packageName, aliasClass)
                pm.setComponentEnabledSetting(
                    component,
                    if (aliasId == id) state else disabled,
                    flags
                )
            }
        }
    }

    /**
     * Ensures the stored disguise still matches the component state after an app update
     * or a restore. No-op when everything already matches, so it cannot cause launcher
     * flicker on every cold start.
     */
    fun ensureApplied(context: Context, id: String) {
        val pm = context.packageManager
        val main = ComponentName(context, "app.locker.vault.MainActivity")
        val expectedMain = id == Disguise.ID_NONE
        val mainEnabled = pm.getComponentEnabledSetting(main) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        if (mainEnabled == expectedMain) return
        apply(context, id)
    }
}
