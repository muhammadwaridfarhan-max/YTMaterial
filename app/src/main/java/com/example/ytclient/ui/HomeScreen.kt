package com.example.ytclient.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ytclient.R
import com.example.ytclient.data.VideoItem

@Composable
fun HomeScreen(vm: MainViewModel, onPlay: (VideoItem) -> Unit, onChannelClick: (String) -> Unit) {
    val filters = stringArrayResource(R.array.home_filters)
    val pullToRefreshState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = vm.homeRefreshing,
        onRefresh = { vm.refreshHome() },
        state = pullToRefreshState,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(Modifier.fillMaxSize()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(filters.toList()) { index, label ->
                    FilterChip(
                        selected = vm.selectedFilter == index,
                        onClick = { vm.selectFilter(index, label) },
                        label = { Text(label) }
                    )
                }
            }
            VideoList(
                state = vm.homeState,
                onRetry = vm::retryFilter,
                onPlay = onPlay,
                modifier = Modifier.fillMaxSize(),
                onChannelClick = onChannelClick,
                onLoadMore = vm::loadMoreHome,
                loadingMore = vm.homeLoadingMore
            )
        }
    }
}
