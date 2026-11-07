package de.vitalcoach.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.vitalcoach.app.HealthConnectRepository
import de.vitalcoach.app.MainViewModel
import de.vitalcoach.app.Route
import de.vitalcoach.app.Tab
import de.vitalcoach.app.UiState

class AppActions(
    val requestPermissions: () -> Unit,
    val openHealthConnect: () -> Unit,
    val openSamsungHealth: () -> Unit,
    val installHealthConnect: () -> Unit,
    val requestNotifications: () -> Unit = {},
)

@Composable
fun VitalApp(vm: MainViewModel, actions: AppActions, showPrivacy: Boolean, onPrivacyClosed: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }

    VitalTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (showPrivacy) {
                BackHandler { onPrivacyClosed() }
                PrivacyScreen(onBack = onPrivacyClosed)
                return@Surface
            }
            if (!state.onboardingDone) {
                SetupScreen(state, vm, actions)
                return@Surface
            }
            val route = vm.backStack.lastOrNull()
            BackHandler(enabled = route != null || tab != Tab.TODAY) {
                if (!vm.back()) tab = Tab.TODAY
            }
            when (route) {
                is Route.Metric -> MetricDetailScreen(route.key, state.result, vm)
                is Route.WorkoutDetail -> WorkoutDetailScreen(route.startEpochSec, state.result, vm)
                Route.Methods -> MethodsScreen(vm)
                Route.Privacy -> PrivacyScreen(onBack = { vm.back() })
                Route.Settings -> SettingsScreen(state, vm, actions)
                Route.PlanEditor -> RunPlanScreen(state, vm, actions)
                null -> MainTabs(state, vm, tab, { tab = it }, actions)
            }
        }
    }
}

@Composable
private fun MainTabs(state: UiState, vm: MainViewModel, tab: Tab, onTab: (Tab) -> Unit, actions: AppActions) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = t == tab,
                        onClick = { onTab(t) },
                        icon = { Icon(t.icon(), contentDescription = t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        when (tab) {
            Tab.TODAY -> TodayScreen(state, vm, padding, onTab)
            Tab.RECOVERY -> RecoveryScreen(state, vm, padding)
            Tab.TRAINING -> TrainingScreen(state, vm, padding)
            Tab.SLEEP -> SleepScreen(state, padding)
            Tab.ANALYSES -> AnalysesScreen(state, vm, padding)
        }
    }
}

private fun Tab.icon(): ImageVector = when (this) {
    Tab.TODAY -> Icons.Filled.Today
    Tab.RECOVERY -> Icons.Filled.BatteryChargingFull
    Tab.TRAINING -> Icons.Filled.FitnessCenter
    Tab.SLEEP -> Icons.Filled.Bedtime
    Tab.ANALYSES -> Icons.Filled.Insights
}

/** Ersteinrichtung in drei einfachen Schritten. */
@Composable
private fun SetupScreen(state: UiState, vm: MainViewModel, actions: AppActions) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 24.dp, bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text("Willkommen bei VitalCoach", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Deine Galaxy Watch misst jeden Tag Puls, Schlaf und Training. VitalCoach macht daraus verständliche Werte zu Erholung, " +
                        "Trainingsbereitschaft und Fitness – mit konkreten Tipps. In drei Schritten bist du startklar:",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            item {
                StepCard(1, "Samsung Health mit Health Connect verbinden") {
                    Text(
                        "Öffne Samsung Health → Menü (☰ oder ⋮) → Einstellungen → Health Connect. Erlaube dort Samsung Health, alle Daten " +
                            "(Aktivität, Schlaf, Vitalwerte, Körpermaße) an Health Connect zu senden.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = actions.openSamsungHealth) { Text("Samsung Health öffnen") }
                }
            }
            item {
                StepCard(2, "VitalCoach den Lesezugriff erlauben") {
                    when (state.availability) {
                        HealthConnectRepository.Availability.AVAILABLE -> {
                            StatusLine(
                                state.hasPermissions,
                                if (state.hasPermissions) "Zugriff erteilt (${state.grantedCount}/${state.totalPermissions})" else "Noch kein Zugriff",
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("Tipp: Wähle im nächsten Fenster „Alle zulassen“.", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = actions.requestPermissions) { Text("Zugriff erlauben") }
                        }
                        else -> {
                            StatusLine(false, "Health Connect fehlt oder muss aktualisiert werden.")
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = actions.installHealthConnect) { Text("Health Connect installieren") }
                        }
                    }
                }
            }
            item {
                StepCard(3, "Dein Profil") {
                    Text(
                        "Geburtsjahr und Geschlecht sind für korrekte Berechnungen (Maximalpuls, Belastung, Fitness-Normwerte) wichtig.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            item { ProfileCard(state.profile) { vm.saveProfile(it) } }
            item {
                Button(onClick = { vm.finishOnboarding(demo = false) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text("Los geht's")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { vm.finishOnboarding(demo = true) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Erst mal mit Demo-Daten ausprobieren")
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Hinweis: Die App lernt in den ersten 7 Tagen deine persönlichen Normalwerte. Trage die Uhr möglichst auch nachts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StepCard(n: Int, title: String, content: @Composable () -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("$n", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(10.dp))
        Column(Modifier.padding(start = 2.dp)) { content() }
    }
}
