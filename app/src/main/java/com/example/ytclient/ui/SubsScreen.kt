package com.example.ytclient.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ytclient.R
import com.example.ytclient.data.SubscriptionManager
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SubsScreen(
    vm: MainViewModel,
    onPlay: (com.example.ytclient.data.VideoItem) -> Unit,
    onOpenChannel: (String) -> Unit
) {
    if (vm.signedIn) {
        // Signed in: the real subscriptions feed of the YouTube account.
        androidx.compose.runtime.LaunchedEffect(Unit) { vm.loadSubsFeed() }
        val insets = LocalFloatingInsets.current
        val pullState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = vm.subsRefreshing,
            onRefresh = { vm.refreshSubs() },
            state = pullState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator(
                    state = pullState,
                    isRefreshing = vm.subsRefreshing,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = insets.top)
                )
            }
        ) {
            VideoList(
                state = vm.subsState,
                onRetry = { vm.loadSubsFeed(force = true) },
                onPlay = onPlay,
                modifier = Modifier.fillMaxSize(),
                onChannelClick = onOpenChannel,
                topPadding = insets.top,
                bottomPadding = insets.bottom,
                onLoadMore = vm::loadMoreSubs,
                loadingMore = vm.subsLoadingMore
            )
        }
    } else {
        LocalSubsList(onOpenChannel)
    }
}

@Composable
private fun LocalSubsList(onOpenChannel: (String) -> Unit) {
    val channels by SubscriptionManager.channels.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    if (channels.isEmpty()) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Filled.List,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.subs_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 8.dp,
                top = LocalFloatingInsets.current.top + 8.dp,
                bottom = LocalFloatingInsets.current.bottom
            )
        ) {
            items(channels, key = { it }) { name ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenChannel(name) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            name.take(1).uppercase(),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(
                        name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { scope.launch { SubscriptionManager.toggle(name) } }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.unsubscribe))
                    }
                }
            }
        }
    }
}
