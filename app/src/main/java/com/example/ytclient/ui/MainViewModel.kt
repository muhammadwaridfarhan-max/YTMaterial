package com.example.ytclient.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ytclient.data.FeedPager
import com.example.ytclient.data.Repository
import com.example.ytclient.data.VideoItem
import kotlinx.coroutines.Job
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

    var shortsState by mutableStateOf<UiState>(UiState.Loading)
        private set
    /** Last settled page of the Shorts pager, so the tab resumes where you left it. */
    var shortsIndex by mutableIntStateOf(0)
    private var shortsJob: Job? = null
    private var shortsLoadingMore = false

    private var lastFilterLabel: String? = null

    var homeLoadingMore by mutableStateOf(false)
        private set
    var searchLoadingMore by mutableStateOf(false)
        private set
    var homeRefreshing by mutableStateOf(false)
        private set
    var searchRefreshing by mutableStateOf(false)
        private set
    private var homePager: FeedPager? = null
    private var homeJob: Job? = null
    private var searchPager: FeedPager? = null
    private var searchJob: Job? = null
    var channelState by mutableStateOf<UiState?>(null)
        private set
    var channelLoadingMore by mutableStateOf(false)
        private set
    private var channelPager: FeedPager? = null
    private var channelJob: Job? = null

    fun loadHome() {
        selectedFilter = 0
        lastFilterLabel = null
        startHome(Repository.homePager(), "Gagal memuat video")
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
        startHome(Repository.searchPager(q), "Pencarian gagal")
    }

    private fun startHome(pager: FeedPager, fallbackError: String) {
        homeJob?.cancel()
        homeLoadingMore = false
        homePager = pager
        homeJob = viewModelScope.launch {
            homeState = UiState.Loading
            homeState = runCatching { pager.nextBatch() }.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.message ?: fallbackError) }
            )
        }
    }

    /** Called when the home list scrolls near its end: appends the next page, forever. */
    fun loadMoreHome() {
        val pager = homePager ?: return
        if (homeLoadingMore || pager.exhausted || homeState !is UiState.Success) return
        homeLoadingMore = true
        homeJob = viewModelScope.launch {
            try {
                val fresh = runCatching { pager.nextBatch() }.getOrDefault(emptyList())
                val latest = (homeState as? UiState.Success)?.items
                if (homePager === pager && latest != null && fresh.isNotEmpty()) {
                    homeState = UiState.Success(latest + fresh)
                }
            } finally {
                if (homePager === pager) homeLoadingMore = false
            }
        }
    }

    fun retryFilter() {
        val label = lastFilterLabel
        if (selectedFilter == 0 || label == null) refreshHome() else refreshSearch()
    }

    fun refreshHome() {
        homeJob?.cancel()
        homeLoadingMore = false
        homeRefreshing = true
        val pager = if (selectedFilter == 0) Repository.homePager() else {
            val q = lastFilterLabel?.trim().orEmpty()
            if (q.isEmpty()) Repository.homePager() else Repository.searchPager(q)
        }
        homePager = pager
        homeJob = viewModelScope.launch {
            homeState = runCatching { pager.nextBatch() }.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.message ?: "Gagal memuat video") }
            )
            homeRefreshing = false
        }
    }

    fun refreshSearch() {
        searchJob?.cancel()
        searchLoadingMore = false
        searchRefreshing = true
        val q = query.trim()
        if (q.isEmpty()) {
            searchRefreshing = false
            return
        }
        val pager = Repository.searchPager(q)
        searchPager = pager
        searchJob = viewModelScope.launch {
            searchState = runCatching { pager.nextBatch() }.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.message ?: "Pencarian gagal") }
            )
            searchRefreshing = false
        }
    }

    fun loadChannel(name: String) {
        channelJob?.cancel()
        channelLoadingMore = false
        channelState = UiState.Loading
        val pager = Repository.searchPager(name)
        channelPager = pager
        channelJob = viewModelScope.launch {
            channelState = runCatching { pager.nextBatch() }.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.message ?: "Gagal memuat channel") }
            )
        }
    }

    fun loadMoreChannel() {
        val pager = channelPager ?: return
        if (channelLoadingMore || pager.exhausted || channelState !is UiState.Success) return
        channelLoadingMore = true
        channelJob = viewModelScope.launch {
            try {
                val fresh = runCatching { pager.nextBatch() }.getOrDefault(emptyList())
                val latest = (channelState as? UiState.Success)?.items
                if (channelPager === pager && latest != null && fresh.isNotEmpty()) {
                    channelState = UiState.Success(latest + fresh)
                }
            } finally {
                if (channelPager === pager) channelLoadingMore = false
            }
        }
    }

    fun runSearch() {
        val q = query.trim()
        if (q.isEmpty()) return
        val pager = Repository.searchPager(q)
        searchJob?.cancel()
        searchLoadingMore = false
        searchPager = pager
        searchJob = viewModelScope.launch {
            searchState = UiState.Loading
            searchState = runCatching { pager.nextBatch() }.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.message ?: "Pencarian gagal") }
            )
        }
    }

    fun loadMoreSearch() {
        val pager = searchPager ?: return
        if (searchLoadingMore || pager.exhausted || searchState !is UiState.Success) return
        searchLoadingMore = true
        searchJob = viewModelScope.launch {
            try {
                val fresh = runCatching { pager.nextBatch() }.getOrDefault(emptyList())
                val latest = (searchState as? UiState.Success)?.items
                if (searchPager === pager && latest != null && fresh.isNotEmpty()) {
                    searchState = UiState.Success(latest + fresh)
                }
            } finally {
                if (searchPager === pager) searchLoadingMore = false
            }
        }
    }

    fun loadShorts(force: Boolean = false) {
        if (!force && (shortsState is UiState.Success || shortsJob?.isActive == true)) return
        shortsJob?.cancel()
        shortsIndex = 0
        shortsJob = viewModelScope.launch {
            shortsState = UiState.Loading
            shortsState = runCatching { Repository.shorts() }.fold(
                onSuccess = {
                    if (it.isEmpty()) UiState.Error("Shorts tidak ditemukan")
                    else UiState.Success(it)
                },
                onFailure = { UiState.Error(it.message ?: "Gagal memuat Shorts") }
            )
        }
    }

    fun loadMoreShorts() {
        if (shortsLoadingMore || shortsState !is UiState.Success) return
        shortsLoadingMore = true
        viewModelScope.launch {
            try {
                val more = runCatching { Repository.shorts() }.getOrDefault(emptyList())
                val latest = (shortsState as? UiState.Success)?.items ?: return@launch
                val seen = latest.mapTo(HashSet()) { it.url }
                val fresh = more.filter { seen.add(it.url) }
                if (fresh.isNotEmpty()) shortsState = UiState.Success(latest + fresh)
            } finally {
                shortsLoadingMore = false
            }
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

    // Must stay last: property initializers above have to run before the first load starts.
    init {
        loadHome()
    }
}
