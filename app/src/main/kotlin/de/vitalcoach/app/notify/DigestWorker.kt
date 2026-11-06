package de.vitalcoach.app.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.vitalcoach.app.HealthConnectRepository
import de.vitalcoach.app.HistoryStore
import de.vitalcoach.app.SettingsStore
import de.vitalcoach.core.AnalysisResult
import de.vitalcoach.core.Analyzer
import de.vitalcoach.core.DemoData
import de.vitalcoach.core.Digest
import de.vitalcoach.core.SnapshotCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Erstellt die Morgen- bzw. Abend-Zusammenfassung im Hintergrund: Daten aus Health Connect lesen,
 * auswerten und als Benachrichtigung zeigen. Morgens wird bis zu 1 Stunde gewartet, falls Samsung Health
 * den Schlaf der letzten Nacht noch nicht übertragen hat.
 */
class DigestWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val kind = DigestKind.from(inputData.getString(DigestScheduler.KEY_KIND)) ?: return Result.success()
        val attempt = inputData.getInt(DigestScheduler.KEY_ATTEMPT, 0)
        val test = inputData.getBoolean(DigestScheduler.KEY_TEST, false)
        if (!Notifier.canNotify(ctx)) return Result.success()

        when (val o = BackgroundAnalysis.run(ctx)) {
            is BackgroundAnalysis.Outcome.Ready -> {
                val r = o.result
                if (kind == DigestKind.MORNING && !test && !Digest.hasLastNight(r) && attempt < MORNING_RETRIES) {
                    DigestScheduler.runNow(ctx, kind, attempt + 1, delayMin = RETRY_MIN)
                    return Result.success()
                }
                Notifier.show(ctx, kind, if (kind == DigestKind.MORNING) Digest.morning(r) else Digest.evening(r))
            }
            BackgroundAnalysis.Outcome.NoAccess -> Notifier.showHint(
                ctx, kind, "VitalCoach braucht Zugriff",
                "Öffne die App und erlaube den Zugriff auf Health Connect, damit deine Zusammenfassung erstellt werden kann.",
            )
            BackgroundAnalysis.Outcome.NoBackgroundAccess -> {
                // Rückfall: zuletzt in der App berechnete Zusammenfassung von heute
                val cached = SettingsStore(ctx).loadDigest(kind.name)
                val zone = ZoneId.systemDefault()
                val at = cached?.second?.let { Instant.ofEpochMilli(it).atZone(zone) }
                val usable = cached != null && at != null && at.toLocalDate() == LocalDate.now(zone) && (kind == DigestKind.EVENING || cached.third)
                if (usable) {
                    Notifier.show(ctx, kind, cached!!.first, "ℹ️ Stand ${at!!.format(HM)} Uhr – für aktuelle Werte „Zugriff im Hintergrund“ erlauben")
                } else {
                    Notifier.showHint(
                        ctx, kind, if (kind == DigestKind.MORNING) "☀️ Deine Morgen-Zusammenfassung" else "🌙 Dein Tagesrückblick",
                        "Tippe, um sie zu laden. Für automatische Zusammenfassungen in VitalCoach unter Einstellungen → Benachrichtigungen den Hintergrund-Zugriff erlauben.",
                    )
                }
            }
            is BackgroundAnalysis.Outcome.Failed -> {
                if (attempt < 1) {
                    DigestScheduler.runNow(ctx, kind, attempt + 1, delayMin = 10)
                } else {
                    Notifier.showHint(ctx, kind, "Zusammenfassung nicht möglich", "Die Daten konnten gerade nicht gelesen werden. Tippe, um VitalCoach zu öffnen.")
                }
            }
        }
        return Result.success()
    }

    private companion object {
        const val MORNING_RETRIES = 3
        const val RETRY_MIN = 20L
        val HM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}

/** Dieselbe Auswertung wie in der App – nur ohne Oberfläche. */
object BackgroundAnalysis {
    sealed interface Outcome {
        data class Ready(val result: AnalysisResult) : Outcome
        data object NoAccess : Outcome
        data object NoBackgroundAccess : Outcome
        data class Failed(val message: String?) : Outcome
    }

    suspend fun run(ctx: Context): Outcome = withContext(Dispatchers.Default) {
        try {
            val store = SettingsStore(ctx)
            if (store.demoMode) {
                val profile = if (store.profile.birthYear == null) DemoData.profile else store.profile
                return@withContext Outcome.Ready(Analyzer.analyze(DemoData.generate(ZoneId.systemDefault(), Instant.now()), profile))
            }
            val repo = HealthConnectRepository(ctx)
            if (repo.availability() != HealthConnectRepository.Availability.AVAILABLE) return@withContext Outcome.NoAccess
            val granted = repo.grantedPermissions()
            if (!granted.containsAll(repo.corePermissions)) return@withContext Outcome.NoAccess
            if (repo.backgroundAccess(granted) != HealthConnectRepository.BackgroundAccess.GRANTED) return@withContext Outcome.NoBackgroundAccess
            val history = HistoryStore(ctx)
            val saved = withContext(Dispatchers.IO) { history.load() }
            val data = withContext(Dispatchers.IO) { repo.readAll() }.copy(history = saved)
            // Liefert Health Connect gar nichts, wurde das Lesen im Hintergrund blockiert.
            if (data.heartRate.isEmpty() && data.sleep.isEmpty() && data.daily.all { (it.steps ?: 0L) == 0L }) {
                return@withContext Outcome.NoBackgroundAccess
            }
            val r = Analyzer.analyze(data, store.profile)
            withContext(Dispatchers.IO) { history.save(SnapshotCodec.merge(saved, SnapshotCodec.fromResult(r))) }
            Outcome.Ready(r)
        } catch (t: Throwable) {
            Outcome.Failed(t.message)
        }
    }
}
