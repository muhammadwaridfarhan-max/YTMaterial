package com.example.ytclient.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.example.ytclient.R
import com.example.ytclient.data.VideoItem
import com.example.ytclient.ui.pressScale

@Composable
fun VideoCard(item: VideoItem, onClick: () -> Unit, onChannelClick: (String) -> Unit = {}, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interaction, 0.96f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        interactionSource = interaction
    ) {
        Box {
            val context = LocalContext.current
            // Decode at the size it is shown (not the full-size source): much smoother scrolling.
            val thumbRequest = remember(item.thumbnail) {
                ImageRequest.Builder(context).data(item.thumbnail).size(640, 360).build()
            }
            AsyncImage(
                model = thumbRequest,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            )
            if (item.isLive) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFFF0000)
                ) {
                    Text(
                        stringResource(R.string.live),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            } else if (item.durationSec > 0) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.75f)
                ) {
                    Text(
                        formatDuration(item.durationSec),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        Row(Modifier.padding(12.dp)) {
            ChannelAvatar(item.channelAvatar, item.channel)
            Spacer(Modifier.width(12.dp))
            Column(
                Modifier
                    .weight(1f)
                    .clickable { onChannelClick(item.channel) }
            ) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                val meta = listOfNotNull(
                    item.channel.ifBlank { null },
                    formatViews(item.views),
                    item.uploaded
                ).joinToString(" • ")
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ChannelAvatar(url: String?, name: String) {
    val initial = name.trim().firstOrNull()?.uppercase() ?: "?"
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        if (!url.isNullOrBlank()) {
            val context = LocalContext.current
            val request = remember(url) {
                ImageRequest.Builder(context).data(url).size(96, 96).build()
            }
            AsyncImage(
                model = request,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                initial,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
internal fun formatViews(views: Long): String? {
    if (views < 0) return null
    return when {
        views >= 1_000_000_000 ->
            stringResource(R.string.views_billion, views / 1_000_000_000.0)

        views >= 1_000_000 ->
            stringResource(R.string.views_million, views / 1_000_000.0)

        views >= 1_000 ->
            stringResource(R.string.views_thousand, views / 1_000)

        else -> stringResource(R.string.views_exact, views)
    }
}

internal fun formatDuration(sec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
