package de.vitalcoach.app

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.vitalcoach.core.AnalysisResult
import de.vitalcoach.core.Analyzer
import de.vitalcoach.core.DemoData
import de.vitalcoach.core.MetricKey
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

enum class Tab(val label: String) { TODAY("Heute"), TRAINING("Training"), SLEEP("Schlaf"), BODY("Körper"), MORE("Mehr") }

sealed interface Route {
    data class Metric(val key: MetricKey) : Route
    data class WorkoutDetail(val startEpochSec: Long) : Route
    data object Methods : Route
    data object Privacy : Route
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
    val error: String? = null,
    val result: AnalysisResult? = null,
    val profile: UserProfile = UserProfile(),
    val demoMode: Boolean = false,
    val onboardingDone: Boolean = false,
    val apiKey: String = "",
    val model: ClaudeModel = ClaudeModel.OPUS,
    val ai: AiState = AiState.Idle,
    val lastRefresh: Instant? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val repo = HealthConnectRepository(app)
    private val store = SettingsStore(app)

    private val _state = MutableStateFlow(
        UiState(
            profile = store.profile,
            demoMode = store.demoMode,
            onboardingDone = store.onboardingDone,
            apiKey = store.apiKey,
            model = store.model,
            ai = store.lastAiText?.let { AiState.Done(it, Instant.ofEpochMilli(store.lastAiTime), store.lastAiQuestion, false) } ?: AiState.Idle,
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
            _state.update { it.copy(loading = true, error = null) }
            try {
                val availability = repo.availability()
                val granted = if (availability == HealthConnectRepository.Availability.AVAILABLE) repo.grantedPermissions() else emptySet()
                val hasCore = granted.containsAll(repo.corePermissions)
                val s = _state.value
                val result = withContext(Dispatchers.Default) {
                    when {
                        s.demoMode -> {
                            val profile = if (s.profile.birthYear == null) DemoData.profile else s.profile
                            Analyzer.analyze(DemoData.generate(ZoneId.systemDefault(), Instant.now()), profile)
                        }
                        availability == HealthConnectRepository.Availability.AVAILABLE && granted.isNotEmpty() -> {
                            val data = withContext(Dispatchers.IO) { repo.readAll() }
                            Analyzer.analyze(data, s.profile)
                        }
                        else -> null
                    }
                }
                _state.update {
                    it.copy(
                        availability = availability,
                        hasPermissions = hasCore,
                        grantedCount = granted.count { p -> p in repo.readPermissions },
                        totalPermissions = repo.readPermissions.size,
                        result = result,
                        loading = false,
                        lastRefresh = Instant.now(),
                    )
                }
            } catch (t: Throwable) {
                _state.update { it.copy(loading = false, error = "Daten konnten nicht geladen werden: ${t.message ?: t.javaClass.simpleName}") }
            }
        }
    }

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
