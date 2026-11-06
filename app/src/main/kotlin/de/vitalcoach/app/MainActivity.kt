package de.vitalcoach.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import de.vitalcoach.app.notify.DigestScheduler
import de.vitalcoach.app.notify.Notifier
import de.vitalcoach.app.ui.AppActions
import de.vitalcoach.app.ui.VitalApp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()
    private var showPrivacy by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { _ -> vm.onPermissionsResult() }

    private val notificationLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.refreshNotificationStatus()
        if (granted) askBackgroundAccessOnce()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        showPrivacy = isPrivacyIntent(intent)
        Notifier.ensureChannel(this)
        DigestScheduler.scheduleAll(this)
        val actions = AppActions(
            requestPermissions = ::askHealthPermissions,
            openHealthConnect = ::openHealthConnect,
            openSamsungHealth = ::openSamsungHealth,
            installHealthConnect = ::installHealthConnect,
            requestNotifications = ::askNotifications,
        )
        // Einmalig nach dem Update: Benachrichtigungen für die Tageszusammenfassungen erlauben
        val store = SettingsStore(this)
        if (store.onboardingDone && !store.askedNotifications && !Notifier.canNotify(this)) askNotifications()
        setContent {
            VitalApp(
                vm = vm,
                actions = actions,
                showPrivacy = showPrivacy,
                onPrivacyClosed = { if (isPrivacyIntent(intent)) finish() else showPrivacy = false },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        showPrivacy = isPrivacyIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.refreshNotificationStatus()
        vm.refreshIfStale()
    }

    private fun askNotifications() {
        val store = SettingsStore(this)
        val firstTime = !store.askedNotifications
        store.askedNotifications = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            (firstTime || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Bereits abgelehnt oder älteres Android: direkt zu den Benachrichtigungs-Einstellungen der App
            try {
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            } catch (e: ActivityNotFoundException) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
        }
    }

    /** Nach dem Erlauben der Benachrichtigungen einmalig den Hintergrund-Zugriff in Health Connect anfragen. */
    private fun askBackgroundAccessOnce() {
        val store = SettingsStore(this)
        if (store.askedBackgroundAccess) return
        lifecycleScope.launch {
            val repo = vm.repo
            if (repo.availability() != HealthConnectRepository.Availability.AVAILABLE) return@launch
            val granted = repo.grantedPermissions()
            if (granted.isEmpty() || repo.backgroundAccess(granted) != HealthConnectRepository.BackgroundAccess.NOT_GRANTED) return@launch
            store.askedBackgroundAccess = true
            askHealthPermissions()
        }
    }

    private fun isPrivacyIntent(i: Intent?): Boolean =
        i?.action == "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE" || i?.action == "android.intent.action.VIEW_PERMISSION_USAGE"

    private fun askHealthPermissions() {
        lifecycleScope.launch {
            try {
                permissionLauncher.launch(vm.permissionsToRequest())
            } catch (e: Exception) {
                openHealthConnect()
            }
        }
    }

    private fun openHealthConnect() {
        val candidates = listOf(
            Intent("android.health.connect.action.MANAGE_HEALTH_PERMISSIONS").putExtra(Intent.EXTRA_PACKAGE_NAME, packageName),
            Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS),
            Intent("android.health.connect.action.HEALTH_HOME_SETTINGS"),
        )
        for (i in candidates) {
            try {
                startActivity(i)
                return
            } catch (e: ActivityNotFoundException) {
                // nächsten Weg probieren
            } catch (e: SecurityException) {
                // nächsten Weg probieren
            }
        }
    }

    private fun openSamsungHealth() {
        val launch = packageManager.getLaunchIntentForPackage("com.sec.android.app.shealth")
        if (launch != null) {
            startActivity(launch)
        } else {
            openStore("com.sec.android.app.shealth")
        }
    }

    private fun installHealthConnect() = openStore("com.google.android.apps.healthdata")

    private fun openStore(pkg: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")))
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")))
        }
    }
}
