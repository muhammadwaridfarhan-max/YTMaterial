package com.example.ytclient.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ytclient.R
import com.example.ytclient.data.SubscriptionManager
import com.example.ytclient.data.VideoItem
import kotlinx.coroutines.launch

@Composable
fun ChannelScreen(
    channel: String,
    vm: MainViewModel,
    onBack: () -> Unit,
    onPlay: (VideoItem) -> Unit
) {
    val subs by SubscriptionManager.channels.collectAsState(initial = emptyList())
    val subscribed = channel in subs
    val scope = rememberCoroutineScope()

    // The overlay never triggered a load before, which is why it spun forever.
    LaunchedEffect(channel) { vm.loadChannel(channel) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.close)
                )
            }
            Text(
                text = channel,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (subscribed) {
                OutlinedButton(onClick = { scope.launch { SubscriptionManager.toggle(channel) } }) {
                    Text(stringResource(R.string.subscribed))
                }
            } else {
                Button(onClick = { scope.launch { SubscriptionManager.toggle(channel) } }) {
                    Text(stringResource(R.string.subscribe))
                }
            }
            Spacer(Modifier.width(8.dp))
        }
        VideoList(
            state = vm.channelState ?: UiState.Loading,
            onRetry = { vm.loadChannel(channel) },
            onPlay = onPlay,
            modifier = Modifier.fillMaxSize(),
            onLoadMore = vm::loadMoreChannel,
            loadingMore = vm.channelLoadingMore,
            bottomPadding = 24.dp
        )
    }
}
