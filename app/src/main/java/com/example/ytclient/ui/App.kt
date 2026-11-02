package com.example.ytclient.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs

@Composable
fun App(vm: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val controller = remember { PlayerController(context.applicationContext) }
    DisposableEffect(Unit) {
        onDispose { controller.release() }
    }

    val tabs = listOf(
        NavTab(R.string.nav_home, Icons.Filled.Home),
        NavTab(R.string.nav_shorts, Icons.Filled.PlayArrow),
        NavTab(R.string.nav_subs, Icons.Filled.List),
        NavTab(R.string.nav_you, Icons.Filled.Person)
    )
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var showLogin by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = pagerState.currentPage != 0) {
        scope.launch { pagerState.animateScrollToPage(0) }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                YTTopBar(
                    onSearch = { showSearch = true },
                    onProfile = { scope.launch { pagerState.animateScrollToPage(3) } }
                )
            },
            bottomBar = {
                NavigationBar {
                    tabs.forEachIndexed { index, tab ->
                        val selected = pagerState.targetPage == index
                        val iconScale by animateFloatAsState(
                            if (selected) 1.18f else 1f,
                            spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "navIcon"
                        )
                        NavigationBarItem(
                            selected = selected,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            icon = {
                                Icon(
                                    tab.icon,
                                    contentDescription = stringResource(tab.label),
                                    modifier = Modifier.graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    }
                                )
                            },
                            label = { Text(stringResource(tab.label)) }
                        )
                    }
                }
            }
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1
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
                            0 -> HomeScreen(vm, onPlay = controller::open)
                            1 -> Placeholder(Icons.Filled.PlayArrow, R.string.nav_shorts)
                            2 -> Placeholder(Icons.Filled.List, R.string.nav_subs)
                            else -> YouScreen(onSignIn = { showLogin = true })
                        }
                    }
                }
                MiniPlayer(
                    controller = controller,
                    onExpand = { controller.expanded = true },
                    modifier = Modifier.align(Alignment.BottomCenter)
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

        AnimatedVisibility(
            visible = controller.current != null && controller.expanded,
            enter = slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { it } +
                    fadeIn(tween(200)),
            exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(220))
        ) {
            PlayerScreen(
                controller = controller,
                vm = vm,
                onMinimize = { controller.expanded = false },
                onLoginRequest = { showLogin = true }
            )
        }

        if (showLogin) {
            LoginSheet(onDismiss = { showLogin = false })
        }
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
