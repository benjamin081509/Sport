@file:OptIn(ExperimentalMaterial3Api::class)

package de.vitalcoach.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import de.vitalcoach.app.HealthConnectRepository
import de.vitalcoach.app.MainViewModel
import de.vitalcoach.app.Route
import de.vitalcoach.app.UiState
import de.vitalcoach.app.notify.DigestKind
import de.vitalcoach.core.Nicotine
import de.vitalcoach.core.Sex
import de.vitalcoach.core.UserProfile
import de.vitalcoach.core.ai.ClaudeModel

@Composable
fun MoreScreen(
    state: UiState,
    vm: MainViewModel,
    padding: PaddingValues,
    onRequestPermissions: () -> Unit,
    onOpenHealthConnect: () -> Unit,
    onOpenSamsungHealth: () -> Unit,
    onInstallHealthConnect: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Mehr", style = MaterialTheme.typography.headlineMedium) }
        item { DataSourceCard(state, vm, onRequestPermissions, onOpenHealthConnect, onOpenSamsungHealth, onInstallHealthConnect) }
        item { ProfileCard(state.profile) { vm.saveProfile(it) } }
        item { AiSettingsCard(state, vm) }
        item {
            SectionCard(title = "So rechnet VitalCoach", icon = Icons.Filled.Science, onClick = { vm.open(Route.Methods) }) {
                Text("Alle Formeln, Referenzwerte und wissenschaftlichen Quellen auf einen Blick.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            SectionCard(title = "Datenschutz", icon = Icons.Filled.Lock, onClick = { vm.open(Route.Privacy) }) {
                Text("Deine Daten bleiben auf deinem Handy.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            Text(
                "VitalCoach ${de.vitalcoach.app.BuildConfig.VERSION_NAME} · Kein Medizinprodukt – die Werte ersetzen keine ärztliche Beratung.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(4.dp),
            )
        }
    }
}

/** Einstellungen als eigene Seite (erreichbar über das Zahnrad und „Analysen“). */
@Composable
fun SettingsScreen(state: UiState, vm: MainViewModel, actions: AppActions) {
    DetailScaffold("Einstellungen", onBack = { vm.back() }) {
        item {
            DataSourceCard(state, vm, actions.requestPermissions, actions.openHealthConnect, actions.openSamsungHealth, actions.installHealthConnect)
        }
        item { NotificationCard(state, vm, actions) }
        item {
            SectionCard(title = "Laufplan & Lauf-Erinnerung", icon = Icons.AutoMirrored.Filled.DirectionsRun, onClick = { vm.open(Route.PlanEditor) }) {
                Text(
                    state.runPlan?.let { p ->
                        "${p.days.size}× pro Woche · Erinnerung ${de.vitalcoach.core.Motivation.clockFromMidnight(p.reminderMinutes)} Uhr"
                    } ?: "Noch kein Plan – feste Lauftage mit Erinnerung anlegen",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item { ProfileCard(state.profile) { vm.saveProfile(it) } }
        item { AiSettingsCard(state, vm) }
        item {
            SectionCard(title = "So rechnet VitalCoach", icon = Icons.Filled.Science, onClick = { vm.open(Route.Methods) }) {
                Text("Alle Formeln, Referenzwerte und wissenschaftlichen Quellen auf einen Blick.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            SectionCard(title = "Datenschutz", icon = Icons.Filled.Lock, onClick = { vm.open(Route.Privacy) }) {
                Text("Deine Daten bleiben auf deinem Handy.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            Text(
                "VitalCoach ${de.vitalcoach.app.BuildConfig.VERSION_NAME} · Kein Medizinprodukt – die Werte ersetzen keine ärztliche Beratung.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(4.dp),
            )
        }
    }
}

@Composable
fun DataSourceCard(
    state: UiState,
    vm: MainViewModel,
    onRequestPermissions: () -> Unit,
    onOpenHealthConnect: () -> Unit,
    onOpenSamsungHealth: () -> Unit,
    onInstallHealthConnect: () -> Unit,
) {
    SectionCard(title = "Datenquelle", icon = Icons.Filled.Sync) {
        when (state.availability) {
            HealthConnectRepository.Availability.AVAILABLE -> {
                StatusLine(
                    ok = state.hasPermissions,
                    text = if (state.hasPermissions) {
                        "Health Connect verbunden (${state.grantedCount} von ${state.totalPermissions} Datentypen freigegeben)"
                    } else {
                        "Noch kein Zugriff auf Health Connect"
                    },
                )
                Spacer(Modifier.height(10.dp))
                Button(onClick = onRequestPermissions, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.hasPermissions) "Berechtigungen prüfen / erweitern" else "Zugriff erlauben")
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = onOpenHealthConnect, modifier = Modifier.fillMaxWidth()) { Text("Health Connect öffnen") }
            }
            HealthConnectRepository.Availability.UPDATE_REQUIRED -> {
                StatusLine(false, "Health Connect muss aktualisiert werden.")
                Spacer(Modifier.height(8.dp))
                Button(onClick = onInstallHealthConnect) { Text("Health Connect aktualisieren") }
            }
            HealthConnectRepository.Availability.NOT_INSTALLED -> {
                StatusLine(false, "Health Connect ist nicht verfügbar.")
                Spacer(Modifier.height(8.dp))
                Button(onClick = onInstallHealthConnect) { Text("Health Connect installieren") }
            }
        }
        Spacer(Modifier.height(6.dp))
        OutlinedButton(onClick = onOpenSamsungHealth, modifier = Modifier.fillMaxWidth()) { Text("Samsung Health öffnen") }
        Spacer(Modifier.height(10.dp))
        Text(
            "Wichtig: In Samsung Health unter Einstellungen → Health Connect die Datenweitergabe erlauben (alle Kategorien aktivieren). " +
                "Neue Daten der Uhr erscheinen erst, nachdem Samsung Health synchronisiert hat.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Demo-Modus", style = MaterialTheme.typography.titleSmall)
                Text("Zeigt erfundene Beispieldaten an", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = state.demoMode, onCheckedChange = { vm.setDemo(it) })
        }
    }
}

/** Morgen- und Abend-Zusammenfassung als Benachrichtigung. */
@Composable
fun NotificationCard(state: UiState, vm: MainViewModel, actions: AppActions) {
    SectionCard(title = "Benachrichtigungen", icon = Icons.Filled.Notifications) {
        StatusLine(
            ok = state.notificationsAllowed,
            text = if (state.notificationsAllowed) "Benachrichtigungen erlaubt" else "Benachrichtigungen sind noch nicht erlaubt",
        )
        if (!state.notificationsAllowed) {
            Spacer(Modifier.height(8.dp))
            Button(onClick = actions.requestNotifications, modifier = Modifier.fillMaxWidth()) { Text("Benachrichtigungen erlauben") }
        }
        if (!state.demoMode) {
            Spacer(Modifier.height(10.dp))
            when (state.backgroundAccess) {
                HealthConnectRepository.BackgroundAccess.GRANTED ->
                    StatusLine(true, "Zugriff im Hintergrund erlaubt – Zusammenfassungen mit aktuellen Werten")
                HealthConnectRepository.BackgroundAccess.NOT_GRANTED -> {
                    StatusLine(false, "Für aktuelle Werte: in Health Connect „Zugriff im Hintergrund“ erlauben")
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = actions.requestPermissions, modifier = Modifier.fillMaxWidth()) { Text("Hintergrund-Zugriff erlauben") }
                }
                HealthConnectRepository.BackgroundAccess.UNSUPPORTED ->
                    StatusLine(false, "Health Connect erlaubt auf diesem Handy kein Lesen im Hintergrund – die Zusammenfassung nutzt den letzten Stand aus der App")
                null -> Unit
            }
        }
        Spacer(Modifier.height(12.dp))
        DigestKind.entries.forEach { kind ->
            DigestRow(
                kind = kind,
                on = state.digestOn[kind] ?: true,
                minutes = state.digestMinutes[kind] ?: kind.defaultMinutes,
                onToggle = { vm.setDigestEnabled(kind, it) },
                onTime = { vm.setDigestTime(kind, it) },
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.testDigest(DigestKind.MORNING) }, modifier = Modifier.weight(1f), enabled = state.notificationsAllowed) { Text("Morgen testen") }
            OutlinedButton(onClick = { vm.testDigest(DigestKind.EVENING) }, modifier = Modifier.weight(1f), enabled = state.notificationsAllowed) { Text("Abend testen") }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Morgens wartet die App bis zu 1 Stunde, falls Samsung Health deinen Schlaf noch nicht übertragen hat.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DigestRow(kind: DigestKind, on: Boolean, minutes: Int, onToggle: (Boolean) -> Unit, onTime: (Int) -> Unit) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(kind.label, style = MaterialTheme.typography.titleSmall)
            Text(
                if (kind == DigestKind.MORNING) "Schlaf, Erholung, Empfehlung für heute" else "Energie, Training, Schritte, Stress",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(
            onClick = {
                android.app.TimePickerDialog(context, { _, h, m -> onTime(h * 60 + m) }, minutes / 60, minutes % 60, true).show()
            },
            enabled = on,
        ) {
            Text(String.format(java.util.Locale.GERMANY, "%02d:%02d", minutes / 60, minutes % 60), style = MaterialTheme.typography.titleMedium)
        }
        Switch(checked = on, onCheckedChange = onToggle)
    }
}

@Composable
fun StatusLine(ok: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (ok) Palette.Good else Palette.Warn,
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ProfileCard(profile: UserProfile, onSave: (UserProfile) -> Unit) {
    var birthYear by rememberSaveable(profile) { mutableStateOf(profile.birthYear?.toString() ?: "") }
    var sex by rememberSaveable(profile) { mutableStateOf(profile.sex?.name) }
    var height by rememberSaveable(profile) { mutableStateOf(profile.heightCm?.let { trimNum(it) } ?: "") }
    var weight by rememberSaveable(profile) { mutableStateOf(profile.weightKg?.let { trimNum(it) } ?: "") }
    var maxHr by rememberSaveable(profile) { mutableStateOf(profile.maxHrOverride?.toString() ?: "") }
    var sleepGoal by rememberSaveable(profile) { mutableStateOf(trimNum(profile.sleepGoalHours)) }
    var stepGoal by rememberSaveable(profile) { mutableStateOf(profile.stepGoal.toString()) }
    var nicotine by rememberSaveable(profile) { mutableStateOf(profile.nicotine?.name) }
    var bpSys by rememberSaveable(profile) { mutableStateOf(profile.bpSystolic?.toString() ?: "") }
    var bpDia by rememberSaveable(profile) { mutableStateOf(profile.bpDiastolic?.toString() ?: "") }
    var saved by remember { mutableStateOf(false) }

    SectionCard(title = "Profil", icon = Icons.Filled.Person) {
        Text(
            "Alter und Geschlecht werden für Maximalpuls, Trainingsbelastung und Fitness-Normwerte benötigt.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        NumberField("Geburtsjahr", birthYear) { birthYear = it }
        Spacer(Modifier.height(8.dp))
        Text("Geschlecht", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = sex == Sex.MALE.name, onClick = { sex = Sex.MALE.name }, label = { Text("männlich") })
            FilterChip(selected = sex == Sex.FEMALE.name, onClick = { sex = Sex.FEMALE.name }, label = { Text("weiblich") })
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Größe (cm)", height, Modifier.weight(1f)) { height = it }
            NumberField("Gewicht (kg)", weight, Modifier.weight(1f)) { weight = it }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Schlafziel (h)", sleepGoal, Modifier.weight(1f)) { sleepGoal = it }
            NumberField("Schrittziel", stepGoal, Modifier.weight(1f)) { stepGoal = it }
        }
        Spacer(Modifier.height(8.dp))
        NumberField("Max. Puls (optional, aus Leistungstest)", maxHr) { maxHr = it }
        Spacer(Modifier.height(12.dp))
        Text("Für die Herzgesundheit (optional)", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text("Rauchen / Nikotin", style = MaterialTheme.typography.labelLarge)
        Nicotine.entries.forEach { n ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { nicotine = n.name },
            ) {
                RadioButton(selected = nicotine == n.name, onClick = { nicotine = n.name })
                Text(n.label, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("Letzter Blutdruck (falls die Uhr keinen liefert)", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Systolisch", bpSys, Modifier.weight(1f)) { bpSys = it }
            NumberField("Diastolisch", bpDia, Modifier.weight(1f)) { bpDia = it }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                onSave(
                    UserProfile(
                        birthYear = birthYear.toIntOrNull()?.takeIf { it in 1920..2020 },
                        sex = sex?.let { Sex.valueOf(it) },
                        heightCm = height.toDec()?.takeIf { it in 100.0..250.0 },
                        weightKg = weight.toDec()?.takeIf { it in 30.0..300.0 },
                        maxHrOverride = maxHr.toIntOrNull()?.takeIf { it in 120..230 },
                        sleepGoalHours = sleepGoal.toDec()?.takeIf { it in 5.0..11.0 } ?: 8.0,
                        stepGoal = stepGoal.toIntOrNull()?.takeIf { it in 1000..50000 } ?: 8000,
                        nicotine = nicotine?.let { Nicotine.valueOf(it) },
                        bpSystolic = bpSys.toIntOrNull()?.takeIf { it in 70..250 },
                        bpDiastolic = bpDia.toIntOrNull()?.takeIf { it in 40..150 },
                    ),
                )
                saved = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Profil speichern") }
        if (saved) {
            Spacer(Modifier.height(6.dp))
            Text("Gespeichert – alle Werte werden neu berechnet.", style = MaterialTheme.typography.bodySmall, color = Palette.Good)
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, modifier: Modifier = Modifier.fillMaxWidth(), onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || it == ',' || it == '.' }) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

private fun String.toDec(): Double? = replace(',', '.').toDoubleOrNull()

private fun trimNum(d: Double): String = if (d % 1.0 == 0.0) d.toInt().toString() else d.toString().replace('.', ',')

@Composable
private fun AiSettingsCard(state: UiState, vm: MainViewModel) {
    var key by rememberSaveable { mutableStateOf(state.apiKey) }
    var model by rememberSaveable { mutableStateOf(state.model) }
    var show by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    LaunchedEffect(state.apiKey) { if (key.isEmpty()) key = state.apiKey }
    SectionCard(title = "KI-Coach (Claude)", icon = Icons.Filled.AutoAwesome) {
        Text(
            "Optional: Mit einem eigenen API-Schlüssel von Anthropic analysiert die KI „Claude“ deine Werte ausführlich und beantwortet deine Fragen. " +
                "Die eingebaute Bewertung funktioniert auch ohne Schlüssel.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "So geht's: 1. Auf console.anthropic.com ein Konto anlegen. 2. Guthaben aufladen (eine Analyse kostet je nach Modell ca. 1–10 Cent). " +
                "3. Unter „API Keys“ einen Schlüssel erstellen und hier einfügen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { uri.openUri("https://console.anthropic.com/settings/keys") }) { Text("console.anthropic.com öffnen") }
        OutlinedTextField(
            value = key,
            onValueChange = { key = it.trim(); saved = false },
            label = { Text("API-Schlüssel (sk-ant-…)") },
            singleLine = true,
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { TextButton(onClick = { show = !show }) { Text(if (show) "verbergen" else "zeigen") } },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Text("Modell", style = MaterialTheme.typography.labelLarge)
        ClaudeModel.entries.forEach { m ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { model = m; saved = false },
            ) {
                RadioButton(selected = model == m, onClick = { model = m; saved = false })
                Column {
                    Text(m.label, style = MaterialTheme.typography.titleSmall)
                    Text(m.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Button(onClick = { vm.saveAi(key, model); saved = true }, modifier = Modifier.fillMaxWidth()) { Text("Speichern") }
        if (saved) Text("Gespeichert.", style = MaterialTheme.typography.bodySmall, color = Palette.Good)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text(
                "Datenschutz: Nur wenn du auf „KI-Analyse“ tippst, wird eine anonyme Zusammenfassung deiner Kennzahlen (keine Namen, keine Orte) " +
                    "verschlüsselt an Anthropic gesendet. Der Schlüssel wird nur auf diesem Handy gespeichert.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
