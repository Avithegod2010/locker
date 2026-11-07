package app.locker.vault.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import app.locker.vault.R

/**
 * Switches the launcher between the real Locker icon and disguised icons (Calculator,
 * Calendar, Notes). Android shows exactly one launcher entry at a time: the real activity is
 * disabled whenever a disguise is active, and the chosen alias is enabled instead. Only the
 * icon and name the launcher shows change; the app itself is untouched and always opens
 * straight into Locker's own lock screen.
 *
 * Shortcuts and the home-screen widget are retargeted to the active alias so quick actions
 * keep working while disguised.
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

    private val staticShortcutIds = listOf("new_note", "new_secret", "lock_vault")

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

    /** The activity component that is currently enabled for launcher/shortcut intent use. */
    fun componentFor(context: Context, id: String): ComponentName =
        if (id == Disguise.ID_NONE || aliases[id] == null) {
            ComponentName(context.packageName, "app.locker.vault.MainActivity")
        } else {
            ComponentName(context.packageName, aliases.getValue(id))
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
        syncShortcuts(context, id)
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
        if (mainEnabled == expectedMain) {
            syncShortcuts(context, id)
            return
        }
        apply(context, id)
    }

    /**
     * Some launchers (notably Samsung One UI) keep a cached icon after the alias switches.
     * Briefly toggling the alias forces the launcher to reload the package entry.
     */
    fun refresh(context: Context, id: String) {
        if (id == Disguise.ID_NONE) return
        val aliasClass = aliases[id] ?: return
        val pm = context.packageManager
        val component = ComponentName(context.packageName, aliasClass)
        val flags = PackageManager.DONT_KILL_APP
        runCatching {
            pm.setComponentEnabledSetting(
                component,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                flags
            )
            Handler(Looper.getMainLooper()).postDelayed({
                runCatching {
                    pm.setComponentEnabledSetting(
                        component,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        flags
                    )
                    syncShortcuts(context, id)
                }
            }, 900L)
        }
    }

    /**
     * Static shortcuts point at MainActivity, which is disabled while a disguise is active.
     * They are disabled and replaced with dynamic shortcuts that target the enabled alias.
     */
    private fun syncShortcuts(context: Context, id: String) {
        val manager = runCatching {
            context.getSystemService(ShortcutManager::class.java)
        }.getOrNull() ?: return
        runCatching {
            if (id == Disguise.ID_NONE) {
                manager.enableShortcuts(staticShortcutIds)
                manager.removeAllDynamicShortcuts()
                return@runCatching
            }

            manager.disableShortcuts(staticShortcutIds)
            val component = componentFor(context, id)
            val desired = listOf(
                dynamicShortcut(
                    context, component, "alias_new_note",
                    R.string.shortcut_new_note, R.drawable.ic_shortcut_note, "new_note"
                ),
                dynamicShortcut(
                    context, component, "alias_new_secret",
                    R.string.shortcut_new_secret, R.drawable.ic_shortcut_secret, "new_secret"
                ),
                dynamicShortcut(
                    context, component, "alias_lock",
                    R.string.shortcut_lock, R.drawable.ic_tile_lock, "lock"
                )
            )
            val existing = manager.dynamicShortcuts.map { it.id }.sorted()
            if (existing != desired.map { it.id }.sorted()) {
                manager.removeAllDynamicShortcuts()
                manager.addDynamicShortcuts(desired)
            }
        }
    }

    private fun dynamicShortcut(
        context: Context,
        component: ComponentName,
        shortcutId: String,
        labelRes: Int,
        iconRes: Int,
        action: String
    ): ShortcutInfo =
        ShortcutInfo.Builder(context, shortcutId)
            .setShortLabel(context.getString(labelRes))
            .setLongLabel(context.getString(labelRes))
            .setIcon(Icon.createWithResource(context, iconRes))
            .setIntent(
                Intent(Intent.ACTION_MAIN)
                    .setComponent(component)
                    .putExtra("shortcut", action)
            )
            .build()
}
