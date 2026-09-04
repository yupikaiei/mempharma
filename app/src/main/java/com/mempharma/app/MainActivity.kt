package com.mempharma.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.mempharma.app.data.scheduler.AlarmScheduler
import com.mempharma.app.data.settings.SettingsRepository
import com.mempharma.app.ui.navigation.MemPharmaApp
import com.mempharma.app.ui.theme.MemPharmaTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var scheduler: AlarmScheduler

    @Inject
    lateinit var settings: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Re-arm every reminder when the app opens (safety net after force-stop
        // or any alarm loss — scheduling is idempotent).
        lifecycleScope.launch { scheduler.rescheduleAll() }

        setContent {
            val fontScale by settings.fontScale.collectAsState(initial = SettingsRepository.FONT_STANDARD)
            MemPharmaTheme {
                val density = LocalDensity.current
                // Global accessibility font scaling multiplier.
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalDensity provides Density(density.density, density.fontScale * fontScale)
                ) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        RequestNotificationPermission()
                        MemPharmaApp()
                    }
                }
            }
        }
    }
}

/** Handles requesting notification permission (Android 13+) on first launch. */
@Composable
fun RequestNotificationPermission() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result handled implicitly by the OS; we never re-prompt aggressively */ }

    var askedOnce by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!askedOnce && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                askedOnce = true
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
