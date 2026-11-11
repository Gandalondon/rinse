package com.gandalondon.rinse

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gandalondon.rinse.system.Notify
import com.gandalondon.rinse.ui.RinseApp

class MainActivity : ComponentActivity() {
    private val vm: RinseViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notify.channels(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)

        setContent {
            val ui by vm.ui.collectAsStateWithLifecycle()
            val light = !ui.settings && ui.snap.phase == Phase.Done
            LaunchedEffect(light) {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = light
                    isAppearanceLightNavigationBars = light
                }
            }
            RinseApp(vm, ui, openUrl = { url ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            })
        }
    }

    override fun onResume() {
        super.onResume()
        vm.resume()
    }

    override fun onPause() {
        super.onPause()
        vm.pause()
    }
}
