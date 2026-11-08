package de.vitalcoach.app

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.vitalcoach.core.AnalysisResult
import de.vitalcoach.core.Analyzer
import de.vitalcoach.core.DemoData
import de.vitalcoach.core.Digest
import de.vitalcoach.app.notify.DigestKind
import de.vitalcoach.app.notify.DigestScheduler
import de.vitalcoach.app.notify.Notifier
import de.vitalcoach.app.notify.RunReminder
import de.vitalcoach.core.RunPlan
import de.vitalcoach.core.MetricKey
import de.vitalcoach.core.SnapshotCodec
import de.vitalcoach.core.UserProfile
import de.vitalcoach.core.ai.AiAnswer
import de.vitalcoach.core.ai.AiReport
import de.vitalcoach.core.ai.ClaudeCoach
import de.vitalcoach.core.ai.ClaudeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

enum class Tab(val label: String) { TODAY("Heute"), RECOVERY("Erholung"), TRAINING("Training"), SLEEP("Schlaf"), ANALYSES("Analysen") }

sealed interface Route {
    data class Metric(val key: MetricKey) : Route
    data class WorkoutDetail(val startEpochSec: Long) : Route
    data object Methods : Route
    data object Privacy : Route
    data object Settings : Route
    data object PlanEditor : Route
    data object RunRoutes : Route
}

sealed interface AiState {
    data object Idle : AiState
    data object Loading : AiState
    data class Done(val text: String, val at: Instant, val question: String?, val truncated: Boolean) : AiState
    data class Error(val message: String) : AiState
}

data class UiState(
    val availability: HealthConnectRepository.Availability = HealthConnectRepository.Availability.AVAILABLE,
    val hasPermissions: Boolean = false,
    val grantedCount: Int = 0,
    val totalPermissions: Int = 0,
    val loading: Boolean = false,
    val progress: String? = null,
    val error: String? = null,
    val result: AnalysisResult? = null,
    val profile: UserProfile = UserProfile(),
    val demoMode: Boolean = false,
    val onboardingDone: Boolean = false,
    val apiKey: String = "",
    val model: ClaudeModel = ClaudeModel.OPUS,
    val ai: AiState = AiState.Idle,
    val lastRefresh: Instant? = null,
    // Benachrichtigungen
    val notificationsAllowed: Boolean = false,
    val backgroundAccess: HealthConnectRepository.BackgroundAccess? = null,
    val digestOn: Map<DigestKind, Boolean> = emptyMap(),
    val digestMinutes: Map<DigestKind, Int> = emptyMap(),
    /** Persönlicher Laufplan (null = noch keiner). */
    val runPlan: RunPlan? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val repo = HealthConnectRepository(app)
    private val store = SettingsStore(app)
    private val history = HistoryStore(app)

    private val _state = MutableStateFlow(
        UiState(
            profile = store.profile,
            demoMode = store.demoMode,
            onboardingDone = store.onboardingDone,
            apiKey = store.apiKey,
            model = store.model,
            ai = store.lastAiText?.let { AiState.Done(it, Instant.ofEpochMilli(store.lastAiTime), store.lastAiQuestion, false) } ?: AiState.Idle,
            notificationsAllowed = Notifier.canNotify(app),
            digestOn = DigestKind.entries.associateWith { store.digestEnabled(it.name) },
            digestMinutes = DigestKind.entries.associateWith { store.digestMinutes(it.name, it.defaultMinutes) },
            runPlan = store.runPlan,
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Navigationsstapel für Detailseiten. */
    val backStack = mutableStateListOf<Route>()

    init {
        refresh()
    }

    fun open(route: Route) {
        backStack.add(route)
    }

    fun back(): Boolean = if (backStack.isNotEmpty()) {
        backStack.removeAt(backStack.lastIndex)
        true
    } else {
        false
    }

    suspend fun permissionsToRequest(): Set<String> = repo.permissionsToRequest()

    fun onPermissionsResult() = refresh()

    /** Beim Zurückkehren in die App nur neu laden, wenn die Daten älter als 10 Minuten sind. */
    fun refreshIfStale() {
        val last = _state.value.lastRefresh ?: return
        if (!_state.value.loading && Instant.now().isAfter(last.plusSeconds(600))) refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, progress = null) }
            try {
                val availability = repo.availability()
                val granted = if (availability == HealthConnectRepository.Availability.AVAILABLE) repo.grantedPermissions() else emptySet()
                val hasCore = granted.containsAll(repo.corePermissions)
                val bg = if (availability == HealthConnectRepository.Availability.AVAILABLE) repo.backgroundAccess(granted) else null
                val s = _state.value
                val result = withContext(Dispatchers.Default) {
                    when {
                        s.demoMode -> {
                            val profile = if (s.profile.birthYear == null) DemoData.profile else s.profile
                            Analyzer.analyze(DemoData.generate(ZoneId.systemDefault(), Instant.now()), profile)
                        }
                        availability == HealthConnectRepository.Availability.AVAILABLE && granted.isNotEmpty() -> {
                            val saved = withContext(Dispatchers.IO) { history.load() }
                            val data = withContext(Dispatchers.IO) {
                                repo.readAll(onProgress = { p -> _state.update { st -> st.copy(progress = p) } })
                            }.copy(history = saved)
                            _state.update { st -> st.copy(progress = "Berechne Auswertungen") }
                            val r = Analyzer.analyze(data, s.profile)
                            withContext(Dispatchers.IO) { history.save(SnapshotCodec.merge(saved, SnapshotCodec.fromResult(r))) }
                            r
                        }
                        else -> null
                    }
                }
                if (result != null) cacheDigests(result)
                _state.update {
                    it.copy(
                        availability = availability,
                        hasPermissions = hasCore,
                        backgroundAccess = bg,
                        notificationsAllowed = Notifier.canNotify(getApplication<Application>()),
                        grantedCount = granted.count { p -> p in repo.readPermissions },
                        totalPermissions = repo.readPermissions.size,
                        result = result,
                        loading = false,
                        progress = null,
                        lastRefresh = Instant.now(),
                    )
                }
            } catch (t: Throwable) {
                _state.update { it.copy(loading = false, progress = null, error = "Daten konnten nicht geladen werden: ${t.message ?: t.javaClass.simpleName}") }
            }
        }
    }

    /** Zusammenfassungen für den Fall merken, dass Health Connect im Hintergrund nicht gelesen werden darf. */
    private fun cacheDigests(r: AnalysisResult) {
        val now = System.currentTimeMillis()
        val plan = store.runPlan
        store.saveDigest(DigestKind.MORNING.name, Digest.morning(r, plan), now, Digest.hasLastNight(r))
        store.saveDigest(DigestKind.EVENING.name, Digest.evening(r, plan), now, true)
    }

    fun refreshNotificationStatus() {
        _state.update { it.copy(notificationsAllowed = Notifier.canNotify(getApplication<Application>())) }
    }

    fun setDigestEnabled(kind: DigestKind, on: Boolean) {
        store.setDigestEnabled(kind.name, on)
        DigestScheduler.schedule(getApplication<Application>(), kind)
        _state.update { it.copy(digestOn = it.digestOn + (kind to on)) }
    }

    fun setDigestTime(kind: DigestKind, minutes: Int) {
        store.setDigestMinutes(kind.name, minutes)
        DigestScheduler.schedule(getApplication<Application>(), kind)
        _state.update { it.copy(digestMinutes = it.digestMinutes + (kind to minutes)) }
    }

    /** Laufplan speichern (null = löschen) und Erinnerungen neu planen. */
    fun saveRunPlan(plan: RunPlan?) {
        store.runPlan = plan
        RunReminder.schedule(getApplication<Application>())
        _state.update { it.copy(runPlan = plan) }
    }

    /** Lauf-Erinnerung sofort auslösen (zum Ausprobieren). */
    fun testRunReminder() = RunReminder.test(getApplication<Application>())

    /** Sendet die Zusammenfassung sofort (zum Ausprobieren). */
    fun testDigest(kind: DigestKind) = DigestScheduler.runNow(getApplication<Application>(), kind, test = true)

    fun saveProfile(p: UserProfile) {
        store.profile = p
        _state.update { it.copy(profile = p) }
        refresh()
    }

    fun setDemo(on: Boolean) {
        store.demoMode = on
        _state.update { it.copy(demoMode = on) }
        refresh()
    }

    fun finishOnboarding(demo: Boolean) {
        store.onboardingDone = true
        if (demo) store.demoMode = true
        _state.update { it.copy(onboardingDone = true, demoMode = demo || it.demoMode) }
        refresh()
    }

    fun saveAi(apiKey: String, model: ClaudeModel) {
        store.apiKey = apiKey
        store.model = model
        _state.update { it.copy(apiKey = apiKey.trim(), model = model) }
    }

    fun runAi(question: String?) {
        val s = _state.value
        val result = s.result ?: return
        if (s.ai is AiState.Loading) return
        _state.update { it.copy(ai = AiState.Loading) }
        viewModelScope.launch {
            val answer = withContext(Dispatchers.IO) {
                ClaudeCoach(s.apiKey).analyze(AiReport.build(result), question, s.model)
            }
            val newState = when (answer) {
                is AiAnswer.Success -> {
                    val now = Instant.now()
                    store.lastAiText = answer.text
                    store.lastAiTime = now.toEpochMilli()
                    store.lastAiQuestion = question
                    AiState.Done(answer.text, now, question, answer.truncated)
                }
                is AiAnswer.Failure -> AiState.Error(answer.message)
            }
            _state.update { it.copy(ai = newState) }
        }
    }
}
