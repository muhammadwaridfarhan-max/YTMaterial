package com.example.ytclient.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ytclient.auth.AccountManager
import com.example.ytclient.auth.AuthManager
import com.example.ytclient.data.AccountPager
import com.example.ytclient.data.ChannelDetails
import com.example.ytclient.data.ChannelTabPager
import com.example.ytclient.data.Pager
import com.example.ytclient.data.Repository
import com.example.ytclient.data.SearchFilter
import com.example.ytclient.data.SearchHistory
import com.example.ytclient.data.VideoItem
import com.example.ytclient.data.YtAccountApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler

sealed interface UiState {
    data object Loading : UiState

    @Immutable
    data class Success(val items: List<VideoItem>) : UiState
    data class Error(val message: String) : UiState
}

enum class ChannelTab { VIDEOS, SHORTS, LIVE, ABOUT }

class MainViewModel : ViewModel() {
    var homeState by mutableStateOf<UiState>(UiState.Loading)
        private set
    var searchState by mutableStateOf<UiState?>(null)
        private set
    var relatedState by mutableStateOf<UiState?>(null)
        private set

    /** Text in the search box. */
    var query by mutableStateOf("")
        private set
    var searchFilter by mutableStateOf(SearchFilter.ALL)
        private set
    var suggestions by mutableStateOf<List<String>>(emptyList())
        private set
    var history by mutableStateOf<List<String>>(emptyList())
        private set
    private var suggestJob: Job? = null

    var selectedFilter by mutableIntStateOf(0)
        private set

    /** True while a Google/YouTube account is signed in: home + subscriptions come from it. */
    var signedIn by mutableStateOf(false)
        private set

    var shortsState by mutableStateOf<UiState>(UiState.Loading)
        private set
    /** Last settled page of the Shorts pager, so the tab resumes where you left it. */
    var shortsIndex by mutableIntStateOf(0)
    var shortsRefreshing by mutableStateOf(false)
        private set
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
    private var homePager: Pager? = null
    private var homeJob: Job? = null
    private var searchPager: Pager? = null
    private var searchJob: Job? = null

    // Subscriptions feed of the signed-in account.
    var subsState by mutableStateOf<UiState>(UiState.Loading)
        private set
    var subsLoadingMore by mutableStateOf(false)
        private set
    var subsRefreshing by mutableStateOf(false)
        private set
    private var subsPager: Pager? = null
    private var subsJob: Job? = null

    // Channel page.
    var channelState by mutableStateOf<UiState?>(null)
        private set
    var channelLoadingMore by mutableStateOf(false)
        private set
    var channelDetails by mutableStateOf<ChannelDetails?>(null)
        private set
    var channelTab by mutableStateOf(ChannelTab.VIDEOS)
        private set
    var channelTabs by mutableStateOf(listOf(ChannelTab.VIDEOS, ChannelTab.ABOUT))
        private set
    private var channelHandlers: Map<String, ListLinkHandler> = emptyMap()
    private var channelName = ""
    private var channelPager: Pager? = null
    private var channelJob: Job? = null

    // ---- home ---------------------------------------------------------------------------

    private fun newHomePager(): Pager =
        if (signedIn) AccountPager(AccountPager.HOME) { Repository.homePager() }
        else Repository.homePager()

    fun loadHome() {
        selectedFilter = 0
        lastFilterLabel = null
        startHome(newHomePager(), "Gagal memuat video")
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

    private fun startHome(pager: Pager, fallbackError: String) {
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
        if (selectedFilter == 0 || label == null) refreshHome() else refreshSearchHome()
    }

    private fun refreshSearchHome() = refreshHome()

    fun refreshHome() {
        homeJob?.cancel()
        homeLoadingMore = false
        homeRefreshing = true
        val pager = if (selectedFilter == 0) newHomePager() else {
            val q = lastFilterLabel?.trim().orEmpty()
            if (q.isEmpty()) newHomePager() else Repository.searchPager(q)
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

    // ---- account ------------------------------------------------------------------------

    /** Re-reads name + photo of the signed-in account so the avatar always matches Google. */
    fun refreshProfile() {
        viewModelScope.launch {
            runCatching { YtAccountApi.profile() }.getOrNull()?.let { AccountManager.saveProfile(it) }
        }
    }

    fun loadSubsFeed(force: Boolean = false) {
        if (!signedIn) return
        if (!force && (subsState is UiState.Success || subsJob?.isActive == true)) return
        subsJob?.cancel()
        subsLoadingMore = false
        val pager = AccountPager(AccountPager.SUBSCRIPTIONS)
        subsPager = pager
        subsJob = viewModelScope.launch {
            subsState = UiState.Loading
            subsState = runCatching { pager.nextBatch() }.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.message ?: "Gagal memuat langganan") }
            )
        }
    }

    fun refreshSubs() {
        subsRefreshing = true
        viewModelScope.launch {
            try {
                loadSubsFeed(force = true)
                subsJob?.join()
            } finally {
                subsRefreshing = false
            }
        }
    }

    fun loadMoreSubs() {
        val pager = subsPager ?: return
        if (subsLoadingMore || pager.exhausted || subsState !is UiState.Success) return
        subsLoadingMore = true
        subsJob = viewModelScope.launch {
            try {
                val fresh = runCatching { pager.nextBatch() }.getOrDefault(emptyList())
                val latest = (subsState as? UiState.Success)?.items
                if (subsPager === pager && latest != null && fresh.isNotEmpty()) {
                    subsState = UiState.Success(latest + fresh)
                }
            } finally {
                if (subsPager === pager) subsLoadingMore = false
            }
        }
    }

    // ---- search -------------------------------------------------------------------------

    fun onQueryChange(text: String) {
        query = text
        suggestJob?.cancel()
        if (text.isBlank()) {
            suggestions = emptyList()
            return
        }
        suggestJob = viewModelScope.launch {
            delay(220)
            suggestions = runCatching { Repository.suggestions(text.trim()) }
                .getOrDefault(emptyList())
        }
    }

    fun selectSearchFilter(filter: SearchFilter) {
        if (searchFilter == filter) return
        searchFilter = filter
        if (query.isNotBlank()) runSearch(record = false)
    }

    fun runSearch(text: String? = null, record: Boolean = true) {
        if (text != null) query = text
        val q = query.trim()
        if (q.isEmpty()) return
        suggestJob?.cancel()
        suggestions = emptyList()
        if (record) viewModelScope.launch { SearchHistory.add(q) }
        val pager = Repository.searchPager(q, searchFilter)
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

    fun refreshSearch() {
        searchJob?.cancel()
        searchLoadingMore = false
        val q = query.trim()
        if (q.isEmpty()) return
        searchRefreshing = true
        val pager = Repository.searchPager(q, searchFilter)
        searchPager = pager
        searchJob = viewModelScope.launch {
            searchState = runCatching { pager.nextBatch() }.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.message ?: "Pencarian gagal") }
            )
            searchRefreshing = false
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

    fun removeHistory(item: String) {
        viewModelScope.launch { SearchHistory.remove(item) }
    }

    fun clearHistory() {
        viewModelScope.launch { SearchHistory.clear() }
    }

    // ---- channel ------------------------------------------------------------------------

    fun loadChannel(name: String) {
        channelJob?.cancel()
        channelLoadingMore = false
        channelState = UiState.Loading
        channelDetails = null
        channelTab = ChannelTab.VIDEOS
        channelTabs = listOf(ChannelTab.VIDEOS, ChannelTab.ABOUT)
        channelHandlers = emptyMap()
        channelName = name
        channelJob = viewModelScope.launch {
            val bundle = runCatching { Repository.channelBundle(name) }.getOrNull()
            channelDetails = bundle?.details
            channelHandlers = bundle?.tabs.orEmpty()
            channelTabs = buildList {
                add(ChannelTab.VIDEOS)
                if (channelHandlers.containsKey(ChannelTabs.SHORTS)) add(ChannelTab.SHORTS)
                if (channelHandlers.containsKey(ChannelTabs.LIVESTREAMS)) add(ChannelTab.LIVE)
                add(ChannelTab.ABOUT)
            }
            startChannelTab(ChannelTab.VIDEOS)
        }
    }

    fun selectChannelTab(tab: ChannelTab) {
        if (tab == channelTab) return
        channelTab = tab
        if (tab != ChannelTab.ABOUT) {
            channelJob?.cancel()
            channelLoadingMore = false
            channelState = UiState.Loading
            channelJob = viewModelScope.launch { startChannelTab(tab) }
        }
    }

    private suspend fun startChannelTab(tab: ChannelTab) {
        val key = when (tab) {
            ChannelTab.SHORTS -> ChannelTabs.SHORTS
            ChannelTab.LIVE -> ChannelTabs.LIVESTREAMS
            else -> ChannelTabs.VIDEOS
        }
        val details = channelDetails
        val handler = channelHandlers[key]
        val pager: Pager = if (handler != null) {
            ChannelTabPager(handler, details?.name ?: channelName, details?.avatar)
        } else {
            Repository.channelPager(channelName)
        }
        channelPager = pager
        channelState = runCatching { pager.nextBatch() }.fold(
            onSuccess = { UiState.Success(it) },
            onFailure = { UiState.Error(it.message ?: "Gagal memuat channel") }
        )
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

    // ---- shorts / related ---------------------------------------------------------------

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

    fun refreshShorts() {
        shortsRefreshing = true
        viewModelScope.launch {
            try {
                loadShorts(force = true)
            } finally {
                shortsRefreshing = false
            }
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
        viewModelScope.launch { SearchHistory.items.collect { history = it } }
        viewModelScope.launch {
            // The feed follows the login state: sign in -> account feed, sign out -> anonymous feed.
            AuthManager.signedIn.distinctUntilChanged().collect { now ->
                signedIn = now
                if (now) {
                    refreshProfile()
                    loadSubsFeed(force = true)
                } else {
                    subsState = UiState.Loading
                    AccountManager.clearProfile()
                }
                loadHome()
            }
        }
    }
}
