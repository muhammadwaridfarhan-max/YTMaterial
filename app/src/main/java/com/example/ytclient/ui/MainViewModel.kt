package com.example.ytclient.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
    var relatedState by mutableStateOf<UiState?>(null)
        private set
    var query by mutableStateOf("")
    var selectedFilter by mutableIntStateOf(0)
        private set

    private var lastFilterLabel: String? = null

    init {
        loadHome()
    }

    fun loadHome() {
        selectedFilter = 0
        lastFilterLabel = null
        viewModelScope.launch {
            homeState = UiState.Loading
            homeState = runCatching { Repository.home() }.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.message ?: "Gagal memuat video") }
            )
        }
    }

    fun selectFilter(index: Int, label: String?) {
        if (index == 0) {
            loadHome()
            return
        }
        selectedFilter = index
        lastFilterLabel = label
        val q = label?.trim().orEmpty()
        if (q.isEmpty()) return
        viewModelScope.launch {
            homeState = UiState.Loading
            homeState = runCatching { Repository.search(q) }.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.message ?: "Pencarian gagal") }
            )
        }
    }

    fun retryFilter() {
        val label = lastFilterLabel
        if (selectedFilter == 0 || label == null) loadHome() else selectFilter(selectedFilter, label)
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

    fun loadRelated(item: VideoItem) {
        viewModelScope.launch {
            relatedState = UiState.Loading
            relatedState = runCatching { Repository.related(item) }.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.message ?: "Gagal memuat rekomendasi") }
            )
        }
    }
}
