package com.example.movieapp.push

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

@Composable
internal fun MovieUpdateSnackbar(events: Flow<Int>, modifier: Modifier = Modifier) {
    val host = remember { SnackbarHostState() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(events, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                events.collectLatest {
                    host.showSnackbar("Movie update received", duration = SnackbarDuration.Short)
                }
            } finally {
                // Do not retain foreground feedback across backgrounding or replay it on resume.
                host.currentSnackbarData?.dismiss()
            }
        }
    }
    SnackbarHost(host, modifier)
}
