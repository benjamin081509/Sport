package de.vitalcoach.app.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import de.vitalcoach.app.SettingsStore
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** Die zwei täglichen Zusammenfassungen. */
enum class DigestKind(val notificationId: Int, val defaultMinutes: Int, val label: String) {
    MORNING(1101, 7 * 60 + 30, "Morgen-Zusammenfassung"),
    EVENING(1102, 21 * 60 + 30, "Abend-Zusammenfassung"),
    ;

    companion object {
        fun from(name: String?): DigestKind? = entries.firstOrNull { it.name == name }
    }
}

/**
 * Plant die Zusammenfassungen mit dem AlarmManager (pünktlich, auch im Ruhemodus des Handys) und
 * startet zur Uhrzeit einen Hintergrundjob (WorkManager), der die Daten liest und die Benachrichtigung zeigt.
 */
object DigestScheduler {
    const val ACTION_ALARM = "de.vitalcoach.app.DIGEST_ALARM"
    const val EXTRA_KIND = "kind"
    const val KEY_KIND = "kind"
    const val KEY_ATTEMPT = "attempt"
    const val KEY_TEST = "test"

    fun enabled(ctx: Context, kind: DigestKind): Boolean = SettingsStore(ctx).digestEnabled(kind.name)
    fun minutes(ctx: Context, kind: DigestKind): Int = SettingsStore(ctx).digestMinutes(kind.name, kind.defaultMinutes)

    fun scheduleAll(ctx: Context) = DigestKind.entries.forEach { schedule(ctx, it) }

    /** Plant den nächsten Termin (oder entfernt ihn, wenn ausgeschaltet). */
    fun schedule(ctx: Context, kind: DigestKind) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val pi = alarmIntent(ctx, kind)
        am.cancel(pi)
        if (!enabled(ctx, kind)) return
        val at = nextTrigger(minutes(ctx, kind), ZonedDateTime.now())
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    /** Nächster Zeitpunkt [minutes] nach Mitternacht (heute, falls noch nicht vorbei, sonst morgen). */
    fun nextTrigger(minutes: Int, now: ZonedDateTime): Long {
        var t = now.toLocalDate().atTime(minutes / 60, minutes % 60).atZone(now.zone)
        if (!t.isAfter(now.plusSeconds(30))) t = now.toLocalDate().plusDays(1).atTime(minutes / 60, minutes % 60).atZone(now.zone)
        return t.toInstant().toEpochMilli()
    }

    /** Startet die Zusammenfassung jetzt bzw. nach [delayMin] Minuten im Hintergrund. */
    fun runNow(ctx: Context, kind: DigestKind, attempt: Int = 0, delayMin: Long = 0, test: Boolean = false) {
        val builder = OneTimeWorkRequestBuilder<DigestWorker>()
            .setInputData(workDataOf(KEY_KIND to kind.name, KEY_ATTEMPT to attempt, KEY_TEST to test))
        if (delayMin > 0) {
            builder.setInitialDelay(delayMin, TimeUnit.MINUTES)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Sofort ausführen – auch wenn das Handy im Ruhemodus ist
            builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        }
        val req = builder.build()
        WorkManager.getInstance(ctx).enqueueUniqueWork("digest_${kind.name}", ExistingWorkPolicy.REPLACE, req)
    }

    private fun alarmIntent(ctx: Context, kind: DigestKind): PendingIntent = PendingIntent.getBroadcast(
        ctx,
        kind.notificationId,
        Intent(ctx, DigestAlarmReceiver::class.java).setAction(ACTION_ALARM).putExtra(EXTRA_KIND, kind.name),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

/** Empfängt den Wecker (→ Zusammenfassung erstellen, nächsten Termin planen) sowie Neustart/Update/Zeitänderung. */
class DigestAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action.orEmpty()
        when (action) {
            DigestScheduler.ACTION_ALARM -> {
                val kind = DigestKind.from(intent.getStringExtra(DigestScheduler.EXTRA_KIND)) ?: return
                DigestScheduler.runNow(context, kind)
                DigestScheduler.schedule(context, kind)
            }
            RunReminder.ACTION_REMIND, RunReminder.ACTION_FOLLOW_UP, RunReminder.ACTION_SNOOZED,
            RunReminder.ACTION_SNOOZE, RunReminder.ACTION_SKIP -> RunReminder.onAction(context, action)
            else -> {
                // Neustart, App-Update, Zeit-/Zeitzonenwechsel: alles neu planen
                DigestScheduler.scheduleAll(context)
                RunReminder.schedule(context)
            }
        }
    }
}
