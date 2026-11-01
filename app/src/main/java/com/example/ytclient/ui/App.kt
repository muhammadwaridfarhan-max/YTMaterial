package com.example.ytclient.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.geometry.Offset
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
import com.example.ytclient.R

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
    val contentBottom by animateDpAsState(
        targetValue = navBottom + 84.dp + if (miniVisible) 76.dp else 0.dp,
        label = "contentBottom"
    )

    BackHandler(enabled = selected != 0 && !searching) { selected = 0 }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollConnection)
            ) {
                if (selected != 1) {
                    YTTopBar(onSearch = { searching = true }, onProfile = { selected = 3 })
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    // Keeps each tab's scroll position when switching tabs.
                    tabState.SaveableStateProvider(selected) {
                        when (selected) {
                            0 -> HomeScreen(
                                vm,
                                onPlay = controller::open,
                                bottomPadding = contentBottom
                            )

                            1 -> ShortsScreen(
                                vm = vm,
                                shorts = shortsPlayer,
                                mainPlayer = controller,
                                onSearch = { searching = true },
                                bottomPadding = contentBottom
                            )

                            2 -> Placeholder(Icons.Filled.Subscriptions, R.string.nav_subs)
                            else -> YouScreen(bottomPadding = contentBottom)
                        }
                    }
                }
            }

            if (searching) {
                BackHandler { searching = false }
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

            // Floating layer: mini player card + pill nav bar.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                MiniPlayer(
                    controller = controller,
                    onExpand = { controller.expanded = true },
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 8.dp)
                )
                AnimatedVisibility(
                    visible = showBar,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    FloatingNavBar(
                        tabs = tabs,
                        selected = selected,
                        onSelect = { selected = it }
                    )
                }
            }

            if (controller.current != null && controller.expanded) {
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
            contentDescription = stringResource(label),
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.coming_soon))
    }
}
