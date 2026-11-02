package com.example.ytclient.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.ytclient.R
import com.example.ytclient.auth.AccountManager
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

@Composable
fun App(vm: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val controller = remember { PlayerController(context.applicationContext) }
    val shortsPlayer = remember { ShortsPlayer(context.applicationContext) }
    DisposableEffect(Unit) {
        onDispose {
            controller.release()
            shortsPlayer.release()
        }
    }

    val tabs = remember {
        listOf(
            NavTab(R.string.nav_home, Icons.Filled.Home),
            NavTab(R.string.nav_shorts, Icons.Filled.PlayCircle),
            NavTab(R.string.nav_subs, Icons.Filled.Subscriptions),
            NavTab(R.string.nav_you, Icons.Filled.Person)
        )
    }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var barVisible by remember { mutableStateOf(true) }
    val tabState = rememberSaveableStateHolder()
    val hazeState = remember { HazeState() }
    val user by AccountManager.user.collectAsState(initial = null)

    // Show the bar again whenever the tab changes; keep the two players from overlapping.
    LaunchedEffect(selected) { barVisible = true }
    LaunchedEffect(controller.isPlayingState) {
        if (controller.isPlayingState) shortsPlayer.pause()
    }

    // Hide the floating bar while scrolling down, bring it back on scroll up.
    val scrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -8f) barVisible = false
                else if (available.y > 8f) barVisible = true
                return Offset.Zero
            }
        }
    }
    val showBar = (barVisible || selected == 1) && !searching

    // Space that screens must leave at the bottom so nothing hides behind the floating UI.
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val miniVisible = controller.current != null && !controller.expanded
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // Room the floating top bar occupies, so list content starts just below it.
    val topInset = statusTop + 72.dp
    val showTopBar = barVisible && selected != 1 && !searching
    val contentBottom by animateDpAsState(
        targetValue = navBottom + 84.dp + if (miniVisible) 76.dp else 0.dp,
        label = "contentBottom"
    )

    BackHandler(enabled = selected != 0 && !searching) { selected = 0 }
    BackHandler(enabled = searching) { searching = false }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState)
                    .nestedScroll(scrollConnection)
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    AnimatedContent(
                        targetState = selected,
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = {
                            val dir = if (targetState > initialState) 1 else -1
                            (slideInHorizontally(OffsetSpring) { dir * it / 5 } +
                                fadeIn(SoftSpring) +
                                scaleIn(SoftSpring, initialScale = 0.95f)) togetherWith
                                (slideOutHorizontally(OffsetSpring) { -dir * it / 5 } +
                                    fadeOut(tween(120)))
                        },
                        label = "tabs"
                    ) { tab ->
                        // Keeps each tab's scroll position when switching tabs.
                        tabState.SaveableStateProvider(tab) {
                        when (tab) {
                            0 -> HomeScreen(
                                vm,
                                onPlay = controller::open,
                                topPadding = topInset,
                                bottomPadding = contentBottom
                            )

                            1 -> ShortsScreen(
                                vm = vm,
                                shorts = shortsPlayer,
                                mainPlayer = controller,
                                onSearch = { searching = true },
                                bottomPadding = contentBottom
                            )

                            2 -> Placeholder(
                                Icons.Filled.Subscriptions,
                                R.string.nav_subs,
                                Modifier.padding(top = topInset)
                            )
                            else -> YouScreen(topPadding = topInset, bottomPadding = contentBottom)
                        }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = searching,
                enter = slideInVertically(OffsetSpring) { it / 6 } +
                    fadeIn(SoftSpring) +
                    scaleIn(SoftSpring, initialScale = 0.94f),
                exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.96f)
            ) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                    ) {
                        SearchScreen(
                            vm,
                            onBack = { searching = false },
                            onPlay = controller::open,
                            bottomPadding = contentBottom
                        )
                    }
                }
            }

            // Soft scrim so list content fades out under the status bar icons.
            if (selected != 1 && !searching) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(statusTop + 12.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }

            // Floating glass top bar; slides away on scroll down, returns on scroll up.
            AnimatedVisibility(
                visible = showTopBar,
                modifier = Modifier.align(Alignment.TopCenter),
                enter = slideInVertically(OffsetSpring) { -it } + fadeIn(SoftSpring),
                exit = slideOutVertically(OffsetSpring) { -it } + fadeOut(tween(150))
            ) {
                FloatingTopBar(
                    avatarUrl = user?.photoUrl,
                    onSearch = { searching = true },
                    onProfile = { selected = 3 },
                    hazeState = hazeState,
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            // Floating layer: mini player card + pill nav bar.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedVisibility(
                    visible = miniVisible,
                    enter = slideInVertically(OffsetSpring) { it } +
                        scaleIn(SoftSpring, initialScale = 0.85f) +
                        fadeIn(SoftSpring),
                    exit = slideOutVertically(OffsetSpring) { it } + fadeOut(tween(150))
                ) {
                    MiniPlayer(
                        controller = controller,
                        onExpand = { controller.expanded = true },
                        hazeState = hazeState,
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 8.dp)
                    )
                }
                AnimatedVisibility(
                    visible = showBar,
                    enter = slideInVertically(OffsetSpring) { it } + fadeIn(SoftSpring),
                    exit = slideOutVertically(OffsetSpring) { it } + fadeOut(tween(150))
                ) {
                    FloatingNavBar(
                        tabs = tabs,
                        selected = selected,
                        onSelect = { selected = it },
                        hazeState = hazeState
                    )
                }
            }

            AnimatedVisibility(
                visible = controller.current != null && controller.expanded,
                enter = slideInVertically(OffsetSpring) { it } + fadeIn(SoftSpring),
                exit = slideOutVertically(OffsetSpring) { it } + fadeOut(tween(150))
            ) {
                PlayerScreen(
                    controller = controller,
                    vm = vm,
                    onMinimize = { controller.expanded = false }
                )
            }
        }
    }
}

@Composable
private fun FloatingTopBar(
    avatarUrl: String?,
    onSearch: () -> Unit,
    onProfile: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .glass(hazeState, CircleShape, elevation = 10.dp)
            .padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0xFFFF0000)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.weight(1f))
        FilledTonalIconButton(onClick = onSearch, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search))
        }
        Spacer(Modifier.width(6.dp))
        FilledTonalIconButton(onClick = onProfile, modifier = Modifier.size(40.dp)) {
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = stringResource(R.string.nav_you),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                )
            } else {
                Icon(Icons.Filled.Person, contentDescription = stringResource(R.string.nav_you))
            }
        }
    }
}

@Composable
private fun Placeholder(icon: ImageVector, @StringRes label: Int, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = stringResource(label),
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.coming_soon))
    }
}
