package com.example.movieapp.ui.welcome

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WelcomeUiState(val isAboutVisible: Boolean = false)

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(
        WelcomeUiState(isAboutVisible = savedStateHandle[ABOUT_VISIBLE] ?: false),
    )
    val uiState: StateFlow<WelcomeUiState> = mutableUiState.asStateFlow()

    fun toggleAbout() {
        val isVisible = !mutableUiState.value.isAboutVisible
        savedStateHandle[ABOUT_VISIBLE] = isVisible
        mutableUiState.value = WelcomeUiState(isAboutVisible = isVisible)
    }

    private companion object {
        const val ABOUT_VISIBLE = "about_visible"
    }
}
