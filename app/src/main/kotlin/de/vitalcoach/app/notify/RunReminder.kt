package de.vitalcoach.app.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import de.vitalcoach.app.SettingsStore
import de.vitalcoach.app.outdoor.WeatherRepo
import de.vitalcoach.core.Motivation
import de.vitalcoach.core.Weather
import de.vitalcoach.core.WeatherVerdict
import java.time.LocalDateTime
import de.vitalcoach.core.RunPlan
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Lauf-Erinnerungen: an geplanten Tagen zur gewählten Uhrzeit, optional 2 h später ein Nachhaken,
 * falls noch kein Training aufgezeichnet wurde. Aktionen in der Benachrichtigung: „In 30 min“ und „Heute nicht“.
 */
object RunReminder {
    const val ACTION_REMIND = "de.vitalcoach.app.RUN_REMIND"
    const val ACTION_FOLLOW_UP = "de.vitalcoach.app.RUN_FOLLOW_UP"
    const val ACTION_SNOOZED = "de.vitalcoach.app.RUN_SNOOZED"
    const val ACTION_SNOOZE = "de.vitalcoach.app.RUN_SNOOZE"
    const val ACTION_SKIP = "de.vitalcoach.app.RUN_SKIP"
    const val NOTIFICATION_ID = 1201
    const val SNOOZE_MIN = 30L

    private const val RC_REMIND = 1201
    private const val RC_FOLLOW_UP = 1202
    private const val RC_SNOOZED = 1203
    const val RC_ACTION_SNOOZE = 1204
    const val RC_ACTION_SKIP = 1205

    const val KEY_MODE = "mode"
    const val MODE_REMIND = "remind"
    const val MODE_FOLLOW_UP = "follow_up"

    /** Plant die nächste Erinnerung (oder entfernt alle, wenn kein Plan existiert). */
    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(intent(ctx, ACTION_REMIND, RC_REMIND))
        val plan = SettingsStore(ctx).runPlan
        if (plan == null || plan.days.isEmpty()) {
            am.cancel(intent(ctx, ACTION_FOLLOW_UP, RC_FOLLOW_UP))
            am.cancel(intent(ctx, ACTION_SNOOZED, RC_SNOOZED))
            return
        }
        val at = nextTrigger(plan, ZonedDateTime.now()) ?: return
        setAlarm(am, at, intent(ctx, ACTION_REMIND, RC_REMIND))
    }

    fun nextTrigger(plan: RunPlan, now: ZonedDateTime): Long? {
        for (i in 0L..7L) {
            val d = now.toLocalDate().plusDays(i)
            if (d.dayOfWeek !in plan.days) continue
            val t = d.atTime(plan.reminderMinutes / 60, plan.reminderMinutes % 60).atZone(now.zone)
            if (t.isAfter(now.plusSeconds(30))) return t.toInstant().toEpochMilli()
        }
        return null
    }

    /** Wird vom Empfänger aufgerufen, wenn ein Wecker oder eine Aktion ausgelöst wurde. */
    fun onAction(ctx: Context, action: String) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val store = SettingsStore(ctx)
        when (action) {
            ACTION_REMIND -> {
                run(ctx, MODE_REMIND)
                val plan = store.runPlan
                if (plan != null && plan.followUp) {
                    setAlarm(am, System.currentTimeMillis() + Motivation.FOLLOW_UP_MIN * 60_000L, intent(ctx, ACTION_FOLLOW_UP, RC_FOLLOW_UP))
                }
                schedule(ctx)
            }
            ACTION_SNOOZED -> run(ctx, MODE_REMIND)
            ACTION_FOLLOW_UP -> run(ctx, MODE_FOLLOW_UP)
            ACTION_SNOOZE -> {
                NotificationManagerCompat.from(ctx).cancel(NOTIFICATION_ID)
                setAlarm(am, System.currentTimeMillis() + SNOOZE_MIN * 60_000L, intent(ctx, ACTION_SNOOZED, RC_SNOOZED))
            }
            ACTION_SKIP -> {
                NotificationManagerCompat.from(ctx).cancel(NOTIFICATION_ID)
                store.runSkipDay = LocalDate.now().toEpochDay()
                am.cancel(intent(ctx, ACTION_FOLLOW_UP, RC_FOLLOW_UP))
                am.cancel(intent(ctx, ACTION_SNOOZED, RC_SNOOZED))
            }
        }
    }

    /** Erinnerung sofort auslösen (zum Ausprobieren). */
    fun test(ctx: Context) = run(ctx, MODE_REMIND, test = true)

    private fun run(ctx: Context, mode: String, test: Boolean = false) {
        val b = OneTimeWorkRequestBuilder<RunReminderWorker>().setInputData(workDataOf(KEY_MODE to mode, DigestScheduler.KEY_TEST to test))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) b.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        WorkManager.getInstance(ctx).enqueueUniqueWork("run_$mode", ExistingWorkPolicy.REPLACE, b.build())
    }

    fun intent(ctx: Context, action: String, requestCode: Int): PendingIntent = PendingIntent.getBroadcast(
        ctx,
        requestCode,
        Intent(ctx, DigestAlarmReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun setAlarm(am: AlarmManager, at: Long, pi: PendingIntent) {
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
}

/** Wetter-Einschätzung für „jetzt + [minutes]“ am zuletzt bekannten Standort (null ohne Standort/Netz). */
suspend fun weatherIn(store: SettingsStore, minutes: Long): WeatherVerdict? = weatherAt(store, LocalDateTime.now().plusMinutes(minutes))

suspend fun weatherAt(store: SettingsStore, at: LocalDateTime): WeatherVerdict? {
    val loc = store.homeLocation ?: return null
    val f = WeatherRepo.forecast(loc) ?: return null
    val h = Weather.at(f, at) ?: return null
    return Weather.verdict(h, f.sunset[h.time.toLocalDate()])
}

/** Erstellt die Lauf-Erinnerung mit aktuellen Werten (Bereitschaft, Wochenziel) – oder lässt sie weg, wenn schon trainiert wurde. */
class RunReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val store = SettingsStore(ctx)
        val plan = store.runPlan ?: return Result.success()
        val mode = inputData.getString(RunReminder.KEY_MODE) ?: RunReminder.MODE_REMIND
        val test = inputData.getBoolean(DigestScheduler.KEY_TEST, false)
        val today = LocalDate.now()
        if (!test && store.runSkipDay == today.toEpochDay()) return Result.success()
        if (!Notifier.canNotify(ctx)) return Result.success()

        val content = when (val o = BackgroundAnalysis.run(ctx)) {
            is BackgroundAnalysis.Outcome.Ready -> {
                val r = o.result
                val rep = Motivation.report(plan, r)
                if (rep.todayDone && !test) return Result.success() // heute schon trainiert – keine Erinnerung nötig
                if (mode == RunReminder.MODE_FOLLOW_UP) {
                    if (rep.suggestion.restAdvised) return Result.success()
                    Motivation.followUp(plan, r)
                } else {
                    Motivation.reminder(plan, r, weatherIn(store, 30))
                }
            }
            // Ohne aktuelle Daten: nur die Erinnerung, kein Nachhaken (wir wissen nicht, ob schon trainiert wurde)
            else -> if (mode == RunReminder.MODE_FOLLOW_UP) return Result.success() else Motivation.genericReminder(plan, today)
        }
        Notifier.showRun(ctx, content, withActions = mode == RunReminder.MODE_REMIND)
        return Result.success()
    }
}
