package com.dilipkumarkv.localgguf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dilipkumarkv.localgguf.ui.navigation.AppNavigation
import com.dilipkumarkv.localgguf.ui.theme.LocalGgufTheme
import com.dilipkumarkv.localgguf.ui.viewmodel.AppViewModelFactory
import com.dilipkumarkv.localgguf.ui.viewmodel.ChatViewModel
import com.dilipkumarkv.localgguf.ui.viewmodel.ModelViewModel
import com.dilipkumarkv.localgguf.ui.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {

    private val factory by lazy { AppViewModelFactory(applicationContext) }

    private val modelViewModel: ModelViewModel by viewModels { factory }
    private val chatViewModel: ChatViewModel by viewModels { factory }
    private val settingsViewModel: SettingsViewModel by viewModels { factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()

            LocalGgufTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(
                        modelViewModel = modelViewModel,
                        chatViewModel = chatViewModel,
                        settingsViewModel = settingsViewModel
                    )
                }
            }
        }
    }
}
