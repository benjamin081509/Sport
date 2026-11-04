package de.vitalcoach.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import de.vitalcoach.app.ui.AppActions
import de.vitalcoach.app.ui.VitalApp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()
    private var showPrivacy by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { _ -> vm.onPermissionsResult() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        showPrivacy = isPrivacyIntent(intent)
        val actions = AppActions(
            requestPermissions = ::askHealthPermissions,
            openHealthConnect = ::openHealthConnect,
            openSamsungHealth = ::openSamsungHealth,
            installHealthConnect = ::installHealthConnect,
        )
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
        vm.refreshIfStale()
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
