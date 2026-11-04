@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.ytclient.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.expandIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ytclient.R
import com.example.ytclient.data.VideoItem
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import androidx.compose.runtime.collectAsState

@Composable
fun App(vm: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val controller = remember { PlayerController(context.applicationContext) }
    val shorts = remember { ShortsPlayer(context.applicationContext) }
    DisposableEffect(Unit) {
        onDispose {
            controller.release()
            shorts.release()
        }
    }

    val tabs = listOf(
        NavTab(R.string.nav_home, Icons.Filled.Home),
        NavTab(R.string.nav_shorts, Icons.Filled.PlayArrow),
        NavTab(R.string.nav_subs, Icons.Filled.List),
        NavTab(R.string.nav_you, Icons.Filled.Person)
    )
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val hazeState = remember { HazeState() }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var showLogin by rememberSaveable { mutableStateOf(false) }
    var showChannel by remember { mutableStateOf(false) }
    var currentChannel by remember { mutableStateOf<String?>(null) }

    // Collect blur settings as reactive state
    val settings by com.example.ytclient.settings.SettingsManager.settings.collectAsState(initial = com.example.ytclient.settings.AppSettings())
    val blurStrength = settings.blurStrength
    val blurEnabled = settings.blurEnabled

    BackHandler(enabled = pagerState.currentPage != 0) {
        scope.launch { pagerState.animateScrollToPage(0) }
    }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val miniVisible = controller.current != null && !controller.expanded
    val miniExtra by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (miniVisible) 76.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "miniInset"
    )
    val insets = FloatingInsets(statusTop + 72.dp, navBottom + 92.dp + miniExtra)

    // Floating top bar: shrinks to a compact pill while the page scrolls, grows back on scroll up.
    // `collapse` is only read in layout/draw lambdas, so scrolling never recomposes this screen.
    val topBarCollapse = remember { TopBarCollapse() }
    val scrollActivity = remember { ScrollActivity() }
    val hideConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y != 0f) {
                    topBarCollapse.onScroll(available.y)
                    scrollActivity.ping()
                }
                return Offset.Zero
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(pagerState.currentPage) { topBarCollapse.reset() }
    androidx.compose.runtime.LaunchedEffect(scrollActivity) {
        androidx.compose.runtime.snapshotFlow { scrollActivity.tick }.collectLatest {
            scrollActivity.active = true
            delay(120)
            scrollActivity.active = false
        }
    }
    val account by com.example.ytclient.auth.AccountManager.user.collectAsState(initial = null)
    // Warm the Shorts feed in the background; the page itself is only composed when you open it.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1200)
        vm.loadShorts()
    }

    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(
            LocalFloatingInsets provides insets,
            LocalScrollActivity provides scrollActivity
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                // Everything the floating bars blur lives inside this source box.
                Box(
                    Modifier
                        .fillMaxSize()
                        .nestedScroll(hideConnection)
                        .hazeSource(hazeState)
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 0
                    ) { page ->
                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val offset = abs(
                                        (pagerState.currentPage - page) +
                                                pagerState.currentPageOffsetFraction
                                    ).coerceIn(0f, 1f)
                                    alpha = 1f - 0.4f * offset
                                    val s = 1f - 0.06f * offset
                                    scaleX = s
                                    scaleY = s
                                }
                        ) {
                            when (page) {
                                0 -> HomeScreen(vm, onPlay = controller::open, onChannelClick = { currentChannel = it; showChannel = true })
                                1 -> ShortsScreen(
                                    vm = vm,
                                    shorts = shorts,
                                    mainPlayer = controller,
                                    onSearch = { showSearch = true },
                                    bottomPadding = 90.dp,
                                    active = pagerState.currentPage == 1,
                                    onChannelClick = { currentChannel = it; showChannel = true }
                                )
                                2 -> SubsScreen(vm, onPlay = controller::open, onOpenChannel = { currentChannel = it; showChannel = true })
                                else -> YouScreen(vm = vm, onSignIn = { showLogin = true })
                            }
                        }
                    }
                }
                FloatingTopBar(
                    visible = pagerState.currentPage != 1,
                    collapse = { topBarCollapse.progress },
                    photoUrl = account?.photoUrl,
                    onSearch = { showSearch = true },
                    onProfile = { scope.launch { pagerState.animateScrollToPage(3) } },
                    hazeState = hazeState,
                    blurStrength = blurStrength,
                    blurEnabled = blurEnabled,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
                MiniPlayer(
                    controller = controller,
                    onExpand = { controller.expanded = true },
                    hazeState = hazeState,
                    blurStrength = blurStrength,
                    blurEnabled = blurEnabled,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 84.dp)
                )
                FloatingNavBar(
                    tabs = tabs,
                    selected = pagerState.currentPage,
                    onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
                    hazeState = hazeState,
                    blurStrength = blurStrength,
                    blurEnabled = blurEnabled,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = showSearch,
            enter = fadeIn(tween(220)) + slideInHorizontally(tween(320)) { it / 6 },
            exit = fadeOut(tween(200)) + slideOutHorizontally(tween(260)) { it / 6 }
        ) {
            SearchOverlay(
                vm = vm,
                onClose = { showSearch = false },
                onPlay = controller::open
            )
        }

        if (showChannel && currentChannel != null) {
            ChannelOverlay(
                channel = currentChannel,
                vm = vm,
                onClose = {
                    showChannel = false
                    currentChannel = null
                },
                onPlay = controller::open
            )
        }

        AnimatedVisibility(
            visible = controller.current != null && controller.expanded,
            enter = slideInVertically(
                spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
            ) { it } + fadeIn(tween(160)),
            exit = slideOutVertically(
                spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)
            ) { it } + fadeOut(tween(200))
        ) {
            PlayerScreen(
                controller = controller,
                vm = vm,
                onMinimize = { controller.expanded = false },
                onLoginRequest = { showLogin = true },
                onChannelClick = {
                    controller.expanded = false
                    currentChannel = it
                    showChannel = true
                }
            )
        }

        if (showLogin) {
            LoginSheet(onDismiss = { showLogin = false })
        }
    }
}

@androidx.compose.runtime.Stable
class TopBarCollapse {
    var progress by mutableFloatStateOf(0f)
        private set

    fun onScroll(dy: Float) {
        // dy < 0: the page scrolls down -> collapse; dy > 0: scrolling back up -> expand.
        progress = (progress - dy / 260f).coerceIn(0f, 1f)
    }

    fun reset() {
        progress = 0f
    }
}

@Composable
private fun ChannelOverlay(
    channel: String?,
    vm: MainViewModel,
    onClose: () -> Unit,
    onPlay: (VideoItem) -> Unit
) {
    BackHandler { onClose() }
    Surface(
        Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        ChannelScreen(channel = channel ?: "", vm = vm, onBack = onClose, onPlay = onPlay)
    }
}

@Composable
private fun SearchOverlay(
    vm: MainViewModel,
    onClose: () -> Unit,
    onPlay: (VideoItem) -> Unit
) {
    var backProgress by remember { mutableFloatStateOf(0f) }
    val animated by animateFloatAsState(
        backProgress,
        spring(stiffness = Spring.StiffnessHigh),
        label = "searchBack"
    )
    PredictiveBackHandler { progress ->
        try {
            progress.collect { backProgress = it.progress }
            onClose()
        } catch (e: CancellationException) {
            backProgress = 0f
        }
    }
    Surface(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                transformOrigin = TransformOrigin(1f, 0.5f)
                val s = 1f - 0.1f * animated
                scaleX = s
                scaleY = s
                translationX = size.width * 0.03f * animated
                shape = RoundedCornerShape((28f * animated).dp)
                clip = animated > 0.001f
            },
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
        ) {
            SearchScreen(vm, onBack = onClose, onPlay = onPlay)
        }
    }
}

@Composable
private fun YTTopBar(onSearch: () -> Unit, onProfile: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFF0000)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSearch) {
                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search))
            }
            IconButton(onClick = onProfile) {
                Icon(Icons.Filled.Person, contentDescription = stringResource(R.string.nav_you))
            }
        }
    }
}

@Composable
private fun Placeholder(icon: ImageVector, @StringRes label: Int) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(label), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.coming_soon),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
