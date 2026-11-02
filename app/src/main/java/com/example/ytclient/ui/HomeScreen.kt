package com.example.ytclient.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ytclient.R
import com.example.ytclient.data.VideoItem

@Composable
fun HomeScreen(
    vm: MainViewModel,
    onPlay: (VideoItem) -> Unit,
    topPadding: Dp = 0.dp,
    bottomPadding: Dp = 0.dp
) {
    val filters = stringArrayResource(R.array.home_filters)
    VideoList(
        state = vm.homeState,
        onRetry = vm::retryFilter,
        onPlay = onPlay,
        topPadding = topPadding,
        bottomPadding = bottomPadding,
        onLoadMore = vm::loadMoreHome,
        loadingMore = vm.homeLoadingMore,
        header = {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(filters, key = { index, _ -> index }) { index, label ->
                    FilterChip(
                        selected = vm.selectedFilter == index,
                        onClick = { vm.selectFilter(index, label) },
                        label = { Text(label) }
                    )
                }
            }
        }
    )
}
