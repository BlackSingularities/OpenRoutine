package com.blacksingularities.openroutine.engine

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.TelephonyManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.blacksingularities.openroutine.App
import com.blacksingularities.openroutine.data.Repo
import com.blacksingularities.openroutine.model.Need
import com.blacksingularities.openroutine.update.UpdateManager

/** Po starcie telefonu i po aktualizacji: odtwarza alarmy i nasłuch, uruchamia rutyny „po uruchomieniu”. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        App.syncBackground(context)
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> Engine.dispatch("t.boot")
            Intent.ACTION_MY_PACKAGE_REPLACED -> UpdateManager.relaunchAfterUpdate(context)
        }
    }
}

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (parts.isEmpty()) return
        val from = parts.first().displayOriginatingAddress.orEmpty()
        val body = parts.joinToString("") { it.displayMessageBody.orEmpty() }
        Engine.dispatch("t.sms", mapOf("sms_from" to from, "sms_body" to body))
    }
}

/** Połączenia telefoniczne: dzwoni, rozmowa, zakończone. Numer jest dostępny tylko przy zgodzie na rejestr połączeń. */
class CallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
            TelephonyManager.EXTRA_STATE_RINGING -> "ringing"
            TelephonyManager.EXTRA_STATE_OFFHOOK -> "offhook"
            TelephonyManager.EXTRA_STATE_IDLE -> "idle"
            else -> return
        }
        @Suppress("DEPRECATION")
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        // Przy zgodzie na rejestr połączeń system wysyła zdarzenie dwa razy: z numerem i bez – bierzemy to z numerem.
        if (number == null && Perms.granted(context, Need.PHONE_STATE)) return
        val now = System.currentTimeMillis()
        if (state == last && now - lastAt < 2_000) return
        last = state
        lastAt = now
        Engine.dispatch("t.call", mapOf("state" to state, "call_number" to number.orEmpty()))
    }

    companion object {
        @Volatile private var last = ""
        @Volatile private var lastAt = 0L
    }
}

/**
 * Jawna integracja dla innych aplikacji:
 * `am broadcast -a com.blacksingularities.openroutine.EVENT --es event latarka --es token <token> --es extra <dane>`.
 * Zdarzenia bez poprawnego tokenu są odrzucane.
 */
class ExternalReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = intent.getStringExtra("event")?.trim().orEmpty()
        if (event.isEmpty()) return
        if (intent.getStringExtra("token") != Repo.token) {
            Repo.log("Integracja", "Odrzucono zdarzenie „$event” – zły token", false)
            return
        }
        val data = mutableMapOf("event" to event, "extra" to intent.getStringExtra("extra").orEmpty())
        intent.extras?.keySet()?.forEach { k ->
            if (k != "token") intent.getStringExtra(k)?.let { data["extra_$k"] = it }
        }
        Engine.dispatch("t.external", data)
    }
}

class NotificationWatcher : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (title.isEmpty() && text.isEmpty()) return
        Engine.dispatch("t.notification", mapOf("notif_app" to sbn.packageName, "notif_title" to title, "notif_text" to text))
    }
}
