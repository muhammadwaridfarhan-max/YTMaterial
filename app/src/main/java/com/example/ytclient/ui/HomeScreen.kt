@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.ytclient.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.fillMaxSize
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
            state = if (vm.useGoogleFeed) vm.googleFeedState else vm.homeState,
            onRetry = if (vm.useGoogleFeed) { /* Google feed driven by account token */ } else vm::retryFilter,
            onPlay = onPlay,
            modifier = Modifier.fillMaxSize(),
            onChannelClick = onChannelClick,
            topPadding = insets.top,
            bottomPadding = insets.bottom,
            header = {
                if (vm.useGoogleFeed) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.google_feed_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(filters.toList()) { index, label ->
                            GlassChip(
                                label = label,
                                selected = vm.selectedFilter == index,
                                onClick = { vm.selectFilter(index, label) }
                            )
                        }
                    }
                }
            },
            onLoadMore = if (vm.useGoogleFeed) vm::loadMoreGoogleFeed else vm::loadMoreHome,
            loadingMore = if (vm.useGoogleFeed) vm.googleFeedLoadingMore else vm.homeLoadingMore
        )
    }
}

/** Rounded filter chip: Material You primary when selected, a soft tonal pill otherwise. */
@Composable
private fun GlassChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = androidx.compose.material3.MaterialTheme.colorScheme
    val container by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) scheme.primary else scheme.surfaceContainerHigh,
        animationSpec = androidx.compose.animation.core.tween(180),
        label = "chipBg"
    )
    val content by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) scheme.onPrimary else scheme.onSurface,
        animationSpec = androidx.compose.animation.core.tween(180),
        label = "chipFg"
    )
    val interaction = androidx.compose.runtime.remember {
        androidx.compose.foundation.interaction.MutableInteractionSource()
    }
    Text(
        label,
        color = content,
        style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .pressScale(interaction, 0.94f)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(container)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    )
}
