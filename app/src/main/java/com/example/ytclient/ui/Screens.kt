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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ytclient.R
import com.example.ytclient.data.VideoItem
import com.example.ytclient.ui.components.VideoCard

/**
 * Shared video feed. [onLoadMore] is called when the user nears the end, which makes the feed
 * endless; [header] (e.g. filter chips) scrolls with the list and is also shown while loading.
 */
@Composable
fun VideoList(
    state: UiState,
    onRetry: () -> Unit,
    onPlay: (VideoItem) -> Unit,
    onChannelClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    topPadding: Dp = 0.dp,
    bottomPadding: Dp = 0.dp,
    header: (@Composable () -> Unit)? = null,
    onLoadMore: (() -> Unit)? = null,
    loadingMore: Boolean = false
) {
    when (state) {
        is UiState.Success -> if (state.items.isNotEmpty()) {
            val listState = rememberLazyListState()
            if (onLoadMore != null) {
                val nearEnd by remember {
                    derivedStateOf {
                        val info = listState.layoutInfo
                        val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                        info.totalItemsCount > 0 && last >= info.totalItemsCount - 6
                    }
                }
                // Re-keyed on the item count so it fires again after each page arrives.
                LaunchedEffect(nearEnd, state.items.size) {
                    if (nearEnd) onLoadMore()
                }
            }
            LazyColumn(
                state = listState,
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp, end = 12.dp,
                    top = topPadding + 4.dp, bottom = 8.dp + bottomPadding
                ),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                if (header != null) {
                    item(key = "header", contentType = "header") { header() }
                }
                items(state.items, key = { it.url }, contentType = { "video" }) { video ->
                    VideoCard(video, onClick = { onPlay(video) }, onChannelClick = onChannelClick)
                }
                if (loadingMore) {
                    item(key = "loading-more", contentType = "loading") {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator(Modifier.size(40.dp)) }
                    }
                }
            }
        } else {
            Placeholder(modifier, topPadding, header) { Text(stringResource(R.string.no_results)) }
        }

        is UiState.Loading -> Placeholder(modifier, topPadding, header) { CircularProgressIndicator() }

        is UiState.Error -> Placeholder(modifier, topPadding, header) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(state.message, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
        }
    }
}

@Composable
private fun Placeholder(
    modifier: Modifier,
    topPadding: Dp,
    header: (@Composable () -> Unit)?,
    content: @Composable () -> Unit
) {
    Column(modifier.fillMaxSize().padding(PaddingValues(start = 12.dp, top = topPadding + 4.dp, end = 12.dp))) {
        if (header != null) {
            Box(Modifier.padding(vertical = 8.dp)) { header() }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { content() }
    }
}
