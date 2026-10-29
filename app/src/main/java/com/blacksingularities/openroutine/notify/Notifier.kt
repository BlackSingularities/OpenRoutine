package com.blacksingularities.openroutine.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.blacksingularities.openroutine.MainActivity
import com.blacksingularities.openroutine.R
import java.util.concurrent.atomic.AtomicInteger

object Notifier {
    const val CHANNEL_ROUTINES = "routines"
    const val CHANNEL_ROUTINES_HIGH = "routines_high"
    const val CHANNEL_MONITOR = "monitor"
    const val CHANNEL_LAUNCH = "launch"
    const val CHANNEL_UPDATES = "updates"
    const val MONITOR_ID = 1

    private val nextId = AtomicInteger(1000)

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_ROUTINES, "Powiadomienia z rutyn", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CHANNEL_ROUTINES_HIGH, "Ważne powiadomienia z rutyn", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(CHANNEL_LAUNCH, "Otwieranie aplikacji i stron", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Gdy Android nie pozwala otworzyć ekranu z tła, rutyna prosi o dotknięcie."
                },
                NotificationChannel(CHANNEL_MONITOR, "Praca w tle", NotificationManager.IMPORTANCE_MIN).apply {
                    description = "Stałe powiadomienie, dzięki któremu rutyny reagują na zdarzenia."
                    setShowBadge(false)
                },
                NotificationChannel(CHANNEL_UPDATES, "Aktualizacje", NotificationManager.IMPORTANCE_LOW),
            ),
        )
    }

    fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun show(context: Context, title: String, text: String, high: Boolean = false, tap: PendingIntent? = null, channel: String? = null) {
        val n = NotificationCompat.Builder(context, channel ?: if (high) CHANNEL_ROUTINES_HIGH else CHANNEL_ROUTINES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(if (high) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(tap ?: openApp(context))
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(nextId.incrementAndGet(), n)
        } catch (_: SecurityException) {
            // Brak zgody na powiadomienia – akcja zostanie odnotowana w dzienniku jako błąd przez wywołującego.
            throw IllegalStateException("Brak zgody na powiadomienia")
        }
    }

    /** Usuwa powiadomienia pokazane przez rutyny (bez stałego powiadomienia pracy w tle). */
    fun clearRoutines(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.activeNotifications.filter { it.id != MONITOR_ID }.forEach { nm.cancel(it.tag, it.id) }
    }
}
