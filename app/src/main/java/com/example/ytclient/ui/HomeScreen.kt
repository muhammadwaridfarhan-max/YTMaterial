@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.ytclient.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.unit.dp
import com.example.ytclient.R
import com.example.ytclient.data.VideoItem

@Composable
fun HomeScreen(vm: MainViewModel, onPlay: (VideoItem) -> Unit, onChannelClick: (String) -> Unit) {
    val filters = stringArrayResource(R.array.home_filters)
    val insets = LocalFloatingInsets.current
    val pullToRefreshState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = vm.homeRefreshing,
        onRefresh = { vm.refreshHome() },
        state = pullToRefreshState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullToRefreshState,
                isRefreshing = vm.homeRefreshing,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = insets.top)
            )
        }
    ) {
        // Chips live in the list header, so the feed scrolls edge to edge under the floating bars.
        VideoList(
            state = vm.homeState,
            onRetry = vm::retryFilter,
            onPlay = onPlay,
            modifier = Modifier.fillMaxSize(),
            onChannelClick = onChannelClick,
            topPadding = insets.top,
            bottomPadding = insets.bottom,
            header = {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(filters.toList()) { index, label ->
                        FilterChip(
                            selected = vm.selectedFilter == index,
                            onClick = { vm.selectFilter(index, label) },
                            label = { Text(label) }
                        )
                    }
                }
            },
            onLoadMore = vm::loadMoreHome,
            loadingMore = vm.homeLoadingMore
        )
    }
}
