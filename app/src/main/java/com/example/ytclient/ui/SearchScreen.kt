@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ytclient.R
import com.example.ytclient.data.SearchFilter
import com.example.ytclient.data.VideoItem

/**
 * Search screen (MeeTube style): pill field with back/clear, suggestions while typing, recent
 * searches when empty, and filter chips above the results.
 */
@Composable
fun SearchScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onPlay: (VideoItem) -> Unit,
    onChannelClick: (String) -> Unit = {}
) {
    val scheme = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    val requester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val hasResults = vm.searchState != null
    // Editing = keyboard up (or nothing searched yet): show suggestions/history instead of results.
    val editing = focused || !hasResults

    LaunchedEffect(Unit) { if (!hasResults) requester.requestFocus() }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .height(52.dp)
                    .glassOr(RoundedCornerShape(26.dp), scheme.surfaceContainerHigh),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.close),
                        tint = scheme.onSurface
                    )
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (vm.query.isEmpty()) {
                        Text(
                            stringResource(R.string.search_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurfaceVariant
                        )
                    }
                    BasicTextField(
                        value = vm.query,
                        onValueChange = vm::onQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                        cursorBrush = SolidColor(scheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            vm.runSearch()
                            focus.clearFocus()
                        }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(requester)
                            .onFocusChanged { focused = it.isFocused }
                    )
                }
                if (vm.query.isNotEmpty()) {
                    IconButton(onClick = {
                        vm.onQueryChange("")
                        requester.requestFocus()
                    }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.search_clear),
                            tint = scheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(scheme.primaryContainer)
                    .clickable {
                        vm.runSearch()
                        focus.clearFocus()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = stringResource(R.string.search),
                    tint = scheme.onPrimaryContainer
                )
            }
        }

        if (editing) {
            SuggestionList(vm, onPick = {
                vm.runSearch(it)
                focus.clearFocus()
            })
        } else {
            PullToRefreshBox(
                isRefreshing = vm.searchRefreshing,
                onRefresh = { vm.refreshSearch() },
                modifier = Modifier.fillMaxSize()
            ) {
                VideoList(
                    state = vm.searchState ?: UiState.Loading,
                    onRetry = { vm.runSearch(record = false) },
                    onPlay = onPlay,
                    onChannelClick = onChannelClick,
                    modifier = Modifier.fillMaxSize(),
                    bottomPadding = 24.dp,
                    header = {
                        val glassChips = liquidGlassOn()
                        val chipGlass = glassTint()
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(SearchFilter.entries.toList(), key = { it.name }) { f ->
                                FilterChip(
                    colors = if (glassChips) androidx.compose.material3.FilterChipDefaults.filterChipColors(containerColor = chipGlass) else androidx.compose.material3.FilterChipDefaults.filterChipColors(),
                                    selected = vm.searchFilter == f,
                                    onClick = { vm.selectSearchFilter(f) },
                                    label = {
                                        Text(
                                            stringResource(
                                                when (f) {
                                                    SearchFilter.ALL -> R.string.search_filter_all
                                                    SearchFilter.SHORTS -> R.string.search_filter_shorts
                                                    SearchFilter.LIVE -> R.string.search_filter_live
                                                    SearchFilter.RECENT -> R.string.search_filter_recent
                                                }
                                            )
                                        )
                                    }
                                )
                            }
                        }
                    },
                    onLoadMore = vm::loadMoreSearch,
                    loadingMore = vm.searchLoadingMore
                )
            }
        }
    }
}

@Composable
private fun SuggestionList(vm: MainViewModel, onPick: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val typing = vm.query.isNotBlank()
    val rows = (if (typing) vm.suggestions else vm.history).distinct()
    LazyColumn(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        if (!typing && rows.isNotEmpty()) {
            item(key = "history-title") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.search_recent),
                        style = MaterialTheme.typography.titleSmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = vm::clearHistory) {
                        Text(stringResource(R.string.search_clear_all))
                    }
                }
            }
        }
        items(rows, key = { (if (typing) "s:" else "h:") + it }) { text ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onPick(text) }
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .height(44.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (typing) Icons.Filled.Search else Icons.Filled.History,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (!typing) {
                    IconButton(onClick = { vm.removeHistory(text) }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.search_clear),
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
