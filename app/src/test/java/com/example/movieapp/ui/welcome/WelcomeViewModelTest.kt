package com.example.movieapp.ui.welcome

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WelcomeViewModelTest {
    @Test
    fun aboutVisibilityTogglesAndRestoresFromSavedState() {
        val handle = SavedStateHandle()
        val viewModel = WelcomeViewModel(handle)
        assertFalse(viewModel.uiState.value.isAboutVisible)

        viewModel.toggleAbout()
        assertTrue(viewModel.uiState.value.isAboutVisible)

        val savedValues = handle.keys().associateWith { handle.get<Any?>(it) }
        val restored = WelcomeViewModel(SavedStateHandle(savedValues))
        assertTrue(restored.uiState.value.isAboutVisible)

        restored.toggleAbout()
        assertFalse(restored.uiState.value.isAboutVisible)
    }
}
