@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.example.ytclient.ui

import android.content.Intent
import android.view.LayoutInflater
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.ytclient.R
import com.example.ytclient.data.VideoItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** YouTube-Shorts style feed: one full-screen clip per page, autoplay on the settled page. */
@Composable
fun ShortsScreen(
    vm: MainViewModel,
    shorts: ShortsPlayer,
    mainPlayer: PlayerController,
    onSearch: () -> Unit,
    bottomPadding: Dp,
    onChannelClick: (String) -> Unit = {}
) {
    val pullToRefreshState = rememberPullToRefreshState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Pull-to-refresh for shorts feed
    PullToRefreshBox(
        isRefreshing = vm.shortsRefreshing,
        onRefresh = { scope.launch { vm.refreshShorts() } },
        state = pullToRefreshState,
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            when (val state = vm.shortsState) {
                is UiState.Loading -> LoadingIndicator(Modifier.align(Alignment.Center))

                is UiState.Error -> Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(state.message, color = Color.White, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { vm.loadShorts(force = true) }) {
                        Text(stringResource(R.string.retry))
                    }
                }

                is UiState.Success -> ShortsPager(
                    state.items,
                    vm,
                    shorts,
                    bottomPadding,
                    onChannelClick = onChannelClick
                )
            }

            // Top overlay: title + search
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent))
                    )
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.nav_shorts),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onSearch) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = stringResource(R.string.search),
                        tint = Color.White
                    )
                }
            }
        }
    }

    // LaunchedEffect for shorts loading
    LaunchedEffect(Unit) {
        mainPlayer.player.pause()
        vm.loadShorts()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> shorts.pause()
                Lifecycle.Event.ON_RESUME -> shorts.resume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            shorts.pause()
        }
    }
}

@Composable
private fun ShortsPager(
    items: List<VideoItem>,
    vm: MainViewModel,
    shorts: ShortsPlayer,
    bottomPadding: Dp,
    onChannelClick: (String) -> Unit
) {
    val latest by rememberUpdatedState(items)
    val pagerState = rememberPagerState(
        initialPage = vm.shortsIndex.coerceIn(0, items.lastIndex)
    ) { latest.size }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val list = latest
            if (page !in list.indices) return@collect
            vm.shortsIndex = page
            shorts.play(list[page])
            shorts.prefetch(list.drop(page + 1).take(2))
            if (page >= list.size - 3) vm.loadMoreShorts()
        }
    }

    VerticalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = 1,
        key = { latest[it].url }
    ) { page ->
        ShortPage(
            item = latest[page],
            isActive = pagerState.settledPage == page,
            shorts = shorts,
            bottomPadding = bottomPadding,
            onChannelClick = onChannelClick
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun ShortPage(
    item: VideoItem,
    isActive: Boolean,
    shorts: ShortsPlayer,
    bottomPadding: Dp,
    onChannelClick: (String) -> Unit
) {
    val context = LocalContext.current
    var liked by rememberSaveable(item.url) { mutableStateOf(false) }
    var heartBurst by remember { mutableStateOf(false) }
    LaunchedEffect(heartBurst) {
        if (heartBurst) {
            delay(650)
            heartBurst = false
        }
    }

    val videoVisible = isActive && shorts.currentUrl == item.url && shorts.hasFirstFrame
    val thumbAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (videoVisible) 0f else 1f,
        animationSpec = androidx.compose.animation.core.tween(180),
        label = "thumb"
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(isActive) {
                androidx.compose.foundation.gestures.detectTapGestures(
                    onTap = { if (isActive) shorts.togglePlay() },
                    onDoubleTap = {
                        if (isActive) {
                            liked = true
                            heartBurst = true
                        }
                    }
                )
            }
    ) {
        if (isActive) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    (LayoutInflater.from(ctx).inflate(R.layout.view_short_player, null, false)
                        as PlayerView).apply { player = shorts.player }
                },
                update = { view ->
                    view.resizeMode = if (shorts.isPortrait) {
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    } else {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                onRelease = { it.player = null }
            )
        }

        // Thumbnail covers the surface until the first frame is rendered.
        AsyncImage(
            model = item.thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = thumbAlpha }
        )

        if (isActive) {
            val err = shorts.error
            if (err != null) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(err, color = Color.White, textAlign = TextAlign.Center)
                    TextButton(onClick = { shorts.retry(item) }) {
                        Text(stringResource(R.string.retry))
                    }
                }
            } else if (shorts.isBuffering || !shorts.hasFirstFrame) {
                LoadingIndicator(Modifier.align(Alignment.Center))
            }
        }

        AnimatedVisibility(
            visible = isActive && shorts.userPaused,
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.45f)) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.play),
                    tint = Color.White,
                    modifier = Modifier
                        .padding(16.dp)
                        .size(48.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = heartBurst,
            modifier = Modifier.align(Alignment.Center),
            enter = scaleIn(PopSpring, initialScale = 0.3f) + fadeIn(),
            exit = scaleOut(targetScale = 1.4f) + fadeOut()
        ) {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(104.dp)
            )
        }

        // Bottom gradient + info + actions
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))
                )
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, bottom = bottomPadding + 12.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ShortAvatar(item.channelAvatar, item.channel)
                    Spacer(Modifier.width(10.dp))
                    // Channel name clickable to open channel feed
                    Text(
                        item.channel,
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable {
                            onChannelClick(item.channel)
                        }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    item.title,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(start = 12.dp)
            ) {
                ShortAction(
                    icon = Icons.Filled.ThumbUp,
                    label = stringResource(R.string.like),
                    tint = if (liked) MaterialTheme.colorScheme.primary else Color.White,
                    popKey = liked,
                    onClick = { liked = !liked }
                )
                ShortAction(
                    icon = Icons.Filled.Share,
                    label = stringResource(R.string.share),
                    tint = Color.White,
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "${item.title}\\n${item.url}")
                        }
                        context.startActivity(Intent.createChooser(send, null))
                    }
                )
            }
        }

        if (isActive) {
            LinearProgressIndicator(
                progress = { shorts.progress },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = bottomPadding - 4.dp)
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.White.copy(alpha = 0.2f)
            )
        }
    }
}

@Composable
private fun ShortAvatar(url: String?, name: String) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.2f))
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                name.trim().firstOrNull()?.uppercase() ?: "?",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun ShortAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    popKey: Boolean = false,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pop = remember { Animatable(1f) }
    LaunchedEffect(popKey) {
        if (popKey) {
            pop.snapTo(0.5f)
            pop.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium)
            )
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.35f),
            interactionSource = interaction,
            modifier = Modifier
                .size(48.dp)
                .pressScale(interaction, 0.85f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = tint,
                    modifier = Modifier.graphicsLayer {
                        scaleX = pop.value
                        scaleY = pop.value
                    }
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}
