package com.maaend.android

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.maaend.android.ui.MaaEndApp
import com.maaend.android.ui.MainViewModel
import com.maaend.android.model.RunSessionPhase
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        observeKeepScreenOn()
        setContent {
            MaaEndApp(viewModel = mainViewModel)
        }
    }

    private fun observeKeepScreenOn() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.uiState.collect { state ->
                    val keepScreenOn = state.runtimeState.phase in setOf(
                        RunSessionPhase.Preparing,
                        RunSessionPhase.Running,
                        RunSessionPhase.Stopping,
                    ) || state.runtimeState.displayPowerOffActive

                    if (keepScreenOn) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
            }
        }
    }
}
