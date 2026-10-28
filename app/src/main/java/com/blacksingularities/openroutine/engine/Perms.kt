package com.blacksingularities.openroutine.engine

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.blacksingularities.openroutine.model.Need

/** Stan uprawnień i ekrany systemu, w których użytkownik je nadaje. */
object Perms {
    fun granted(ctx: Context, need: Need): Boolean = when (need) {
        Need.NOTIFICATIONS -> Build.VERSION.SDK_INT < 33 || has(ctx, Manifest.permission.POST_NOTIFICATIONS)
        Need.EXACT_ALARM -> Scheduler.canExact(ctx)
        Need.MONITOR -> ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
        Need.NOTIFICATION_ACCESS -> NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
        Need.DND_ACCESS -> ctx.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted
        Need.WRITE_SETTINGS -> Settings.System.canWrite(ctx)
        Need.OVERLAY -> Settings.canDrawOverlays(ctx)
        Need.SMS_RECEIVE -> has(ctx, Manifest.permission.RECEIVE_SMS)
        Need.SMS_SEND -> has(ctx, Manifest.permission.SEND_SMS)
    }

    private fun has(ctx: Context, p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    /** Uprawnienie nadawane w oknie systemowym w aplikacji (runtime) – lub null, gdy w ustawieniach. */
    fun runtime(need: Need): String? = when (need) {
        Need.NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else null
        Need.SMS_RECEIVE -> Manifest.permission.RECEIVE_SMS
        Need.SMS_SEND -> Manifest.permission.SEND_SMS
        else -> null
    }

    fun settingsIntent(ctx: Context, need: Need): Intent {
        val pkg = Uri.parse("package:" + ctx.packageName)
        return when (need) {
            Need.EXACT_ALARM -> if (Build.VERSION.SDK_INT >= 31) Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg) else appDetails(pkg)
            Need.MONITOR -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)
            Need.NOTIFICATION_ACCESS -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            Need.DND_ACCESS -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            Need.WRITE_SETTINGS -> Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, pkg)
            Need.OVERLAY -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg)
            Need.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
            else -> appDetails(pkg)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun appDetails(pkg: Uri) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
}
