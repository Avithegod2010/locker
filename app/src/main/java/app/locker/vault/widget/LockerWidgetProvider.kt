package app.locker.vault.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import app.locker.vault.LockerApp
import app.locker.vault.MainActivity
import app.locker.vault.R
import app.locker.vault.core.DisguiseManager
import app.locker.vault.core.SessionState

class LockerWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_locker)
            views.setTextViewText(
                R.id.widget_status,
                if ((context.applicationContext as LockerApp).container.session.state.value == SessionState.UNLOCKED) {
                    "Vault unlocked"
                } else {
                    "Vault locked"
                }
            )
            views.setOnClickPendingIntent(R.id.widget_open, openIntent(context))
            views.setOnClickPendingIntent(R.id.widget_lock, lockIntent(context))
            views.setOnClickPendingIntent(
                R.id.widget_note,
                shortcutIntent(context, "new_note", 12)
            )
            views.setOnClickPendingIntent(
                R.id.widget_camera,
                shortcutIntent(context, "new_photo", 13)
            )
            appWidgetManager.updateAppWidget(id, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_LOCK) {
            (context.applicationContext as LockerApp).container.lockNow()
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                android.content.ComponentName(context, LockerWidgetProvider::class.java)
            )
            onUpdate(context, manager, ids)
        }
    }

    private fun openIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            10,
            Intent(context, MainActivity::class.java).setComponent(launcherComponent(context)),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun lockIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            11,
            Intent(context, LockerWidgetProvider::class.java).setAction(ACTION_LOCK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun shortcutIntent(
        context: Context,
        shortcut: String,
        requestCode: Int
    ): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java)
                .setComponent(launcherComponent(context))
                .putExtra("shortcut", shortcut),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun launcherComponent(context: Context): android.content.ComponentName {
        val lockerApp = context.applicationContext as LockerApp
        val disguise = lockerApp.container.preferences.settings.value.disguiseMode
        return DisguiseManager.componentFor(context, disguise)
    }

    companion object {
        private const val ACTION_LOCK = "app.locker.vault.widget.LOCK"
    }
}
