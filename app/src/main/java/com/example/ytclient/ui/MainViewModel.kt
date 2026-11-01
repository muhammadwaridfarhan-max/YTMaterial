package com.example.ytclient.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ytclient.data.Repository
import com.example.ytclient.data.VideoItem
import kotlinx.coroutines.launch

sealed interface UiState {
    data object Loading : UiState
    data class Success(val items: List<VideoItem>) : UiState
    data class Error(val message: String) : UiState
}

class MainViewModel : ViewModel() {
    var homeState by mutableStateOf<UiState>(UiState.Loading)
        private set
    var searchState by mutableStateOf<UiState?>(null)
        private set
    var query by mutableStateOf("")

    init {
        loadHome()
    }

    fun loadHome() {
        viewModelScope.launch {
            homeState = UiState.Loading
            homeState = runCatching { Repository.home() }.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.message ?: "Gagal memuat video") }
            )
        }
    }

    fun runSearch() {
        val q = query.trim()
        if (q.isEmpty()) return
        viewModelScope.launch {
            searchState = UiState.Loading
            searchState = runCatching { Repository.search(q) }.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.message ?: "Pencarian gagal") }
            )
        }
    }
}
