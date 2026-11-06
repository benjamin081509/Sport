package de.vitalcoach.app.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.vitalcoach.app.MainActivity
import de.vitalcoach.app.R
import de.vitalcoach.core.DigestContent
import de.vitalcoach.core.Tone

/** Zeigt die täglichen Zusammenfassungen als übersichtliche, ausklappbare Benachrichtigung. */
object Notifier {
    private const val CHANNEL = "daily_digest"

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Tageszusammenfassung", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Morgens und abends eine kurze Zusammenfassung deiner wichtigsten Werte"
            },
        )
    }

    fun canNotify(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    /** Zusammenfassung: Titel + Kurztext (eingeklappt), ausgeklappt eine Zeile pro Punkt. */
    fun show(ctx: Context, kind: DigestKind, c: DigestContent, note: String? = null) {
        val body = (c.lines + listOfNotNull(note)).joinToString("\n")
        post(ctx, kind, c.title, c.text, body, toneColor(c.tone))
    }

    /** Hinweis, wenn keine Zusammenfassung erstellt werden konnte. */
    fun showHint(ctx: Context, kind: DigestKind, title: String, text: String) {
        post(ctx, kind, title, text, text, 0xFF2F6FED.toInt())
    }

    @SuppressLint("MissingPermission")
    private fun post(ctx: Context, kind: DigestKind, title: String, text: String, body: String, color: Int) {
        if (!canNotify(ctx)) return
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx,
            kind.notificationId,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_vital)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().setBigContentTitle(title).bigText(body))
            .setColor(color)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(kind.notificationId, n)
        } catch (e: SecurityException) {
            // Benachrichtigungen wurden in der Zwischenzeit verboten
        }
    }

    private fun toneColor(t: Tone): Int = when (t) {
        Tone.GOOD -> 0xFF1FA463.toInt()
        Tone.OK -> 0xFF7CB342.toInt()
        Tone.WARN -> 0xFFF59E0B.toInt()
        Tone.BAD -> 0xFFE5484D.toInt()
        Tone.NEUTRAL -> 0xFF2F6FED.toInt()
    }
}
