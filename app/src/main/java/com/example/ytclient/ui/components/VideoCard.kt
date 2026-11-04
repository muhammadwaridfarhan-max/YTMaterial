package com.example.ytclient.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ytclient.R
import com.example.ytclient.data.VideoItem
import com.example.ytclient.ui.pressScale

/**
 * MeeTube-style feed card: a big rounded thumbnail with no card frame, then avatar, title, meta
 * line and a three-dot menu. Colors come from the Material You theme.
 */
@Composable
fun VideoCard(
    item: VideoItem,
    onClick: () -> Unit,
    onChannelClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    var menu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // Decode at the size it is shown (not the full-size source): much smoother scrolling.
    val thumbRequest = remember(item.thumbnail) {
        ImageRequest.Builder(context).data(item.thumbnail).size(640, 360).build()
    }
    val views = formatViews(item.views)
    val meta = remember(item.channel, views, item.uploaded) {
        listOfNotNull(item.channel.ifBlank { null }, views, item.uploaded).joinToString(" • ")
    }

    Column(
        modifier
            .fillMaxWidth()
            .pressScale(interaction, 0.97f)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
        ) {
            AsyncImage(
                model = thumbRequest,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            DurationBadge(item, Modifier.align(Alignment.BottomEnd))
        }
        Row(
            Modifier.padding(top = 12.dp, start = 2.dp),
            verticalAlignment = Alignment.Top
        ) {
            ChannelAvatar(
                item.channelAvatar,
                item.channel,
                Modifier.clickable { onChannelClick(item.channel) }
            )
            Spacer(Modifier.width(12.dp))
            Column(
                Modifier
                    .weight(1f)
                    .clickable(onClick = onClick)
            ) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.open_channel)) },
                        onClick = {
                            menu = false
                            onChannelClick(item.channel)
                        }
                    )
                }
            }
        }
    }
}

/** Compact row for "Up next": thumbnail on the left, title and meta on the right. */
@Composable
fun VideoRow(item: VideoItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val context = LocalContext.current
    val thumbRequest = remember(item.thumbnail) {
        ImageRequest.Builder(context).data(item.thumbnail).size(400, 225).build()
    }
    val views = formatViews(item.views)
    val meta = remember(item.channel, views, item.uploaded) {
        listOfNotNull(item.channel.ifBlank { null }, views, item.uploaded).joinToString(" • ")
    }
    Row(
        modifier
            .fillMaxWidth()
            .pressScale(interaction, 0.97f)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        Box(
            Modifier
                .width(168.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            AsyncImage(
                model = thumbRequest,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            DurationBadge(item, Modifier.align(Alignment.BottomEnd))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DurationBadge(item: VideoItem, modifier: Modifier) {
    if (item.isLive) {
        Box(
            modifier
                .padding(8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFFF0000))
        ) {
            Text(
                stringResource(R.string.live),
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall
            )
        }
    } else if (item.durationSec > 0) {
        Box(
            modifier
                .padding(8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.7f))
        ) {
            Text(
                formatDuration(item.durationSec),
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun ChannelAvatar(url: String?, name: String, modifier: Modifier = Modifier) {
    val initial = name.trim().firstOrNull()?.uppercase() ?: "?"
    Box(
        modifier = modifier
            .size(40.dp)
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
