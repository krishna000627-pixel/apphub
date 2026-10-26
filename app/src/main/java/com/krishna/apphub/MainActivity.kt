package com.krishna.apphub

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.os.Build
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    private val hub: HubVM by viewModels()

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        hub.handleCallback(intent.data)
        intent.getStringExtra("route")?.let { hub.openRoute = it }   // v27: tap on a build / download notification
    }

    private val askNotif = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onPause() { hub.inForeground = false; super.onPause() }

    override fun onResume() {
        super.onResume()
        hub.inForeground = true
        hub.resumeLogin()
        hub.scanZips(this)
        hub.checkInstalls()
        hub.autoSync()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.install(this)   // v27: the last crash is saved to a file
        enableEdgeToEdge()
        if (savedInstanceState == null) { hub.handleCallback(intent?.data); intent?.getStringExtra("route")?.let { hub.openRoute = it } }
        Sync.schedule(applicationContext, hub.prefs)
        ServerWatch.schedule(applicationContext, hub.prefs)   // v42: server down / up alerts
        // one-time: lets the login notification ("tap to return") show after approving on GitHub
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
            && !getSharedPreferences("hub_ui", MODE_PRIVATE).getBoolean("asked_notif", false)) {
            getSharedPreferences("hub_ui", MODE_PRIVATE).edit().putBoolean("asked_notif", true).apply()
            askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            val vm: HubVM = viewModel()
            HubTheme(vm.themeMode, vm.accent, vm.textSize, isSystemInDarkTheme()) {   // v74: 0 dark, 1 white, 2 follow the phone, 3.. more themes (Theme.kt)
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { App(vm) }
            }
        }
    }
}
