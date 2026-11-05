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
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
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
    val glassOn = liquidGlassOn()

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
                    .then(
                        if (glassOn) Modifier.glassOr(CircleShape, scheme.primaryContainer, 1.2f)
                        else Modifier.background(scheme.primaryContainer)
                    )
                    .clickable {
                        vm.runSearch()
                        focus.clearFocus()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = stringResource(R.string.search),
                    tint = if (glassOn) scheme.primary else scheme.onPrimaryContainer
                )
            }
        }

        if (editing) {
            SuggestionList(vm, onPick = {
                vm.runSearch(it)
                focus.clearFocus()
            }, onFill = {
                vm.onQueryChange("$it ")
                requester.requestFocus()
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
private fun SuggestionList(vm: MainViewModel, onPick: (String) -> Unit, onFill: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val typing = vm.query.isNotBlank()
    val rows = (if (typing) vm.suggestions else vm.history).distinct()
    val typed = vm.query.trim()
    val boldStyle = SpanStyle(fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
    val dimColor = scheme.onSurfaceVariant
    if (!typing && rows.isEmpty()) {
        SearchEmptyState(
            Icons.Filled.Search,
            stringResource(R.string.search_empty_title),
            stringResource(R.string.search_empty)
        )
        return
    }
    LazyColumn(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp)
    ) {
        if (typing && vm.suggestionsLoading && rows.isEmpty()) {
            item(key = "suggest-loading") {
                LinearProgressIndicator(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                        .clip(CircleShape)
                )
            }
        }
        if (!typing) {
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
                        Text(stringResource(R.string.search_clear_history))
                    }
                }
            }
        }
        items(rows, key = { (if (typing) "s:" else "h:") + it }) { text ->
            val label = if (typing && text.startsWith(typed, ignoreCase = true)) {
                buildAnnotatedString {
                    withStyle(SpanStyle(color = dimColor)) { append(text.substring(0, typed.length)) }
                    withStyle(boldStyle) { append(text.substring(typed.length)) }
                }
            } else AnnotatedString(text)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onPick(text) }
                    .padding(horizontal = 12.dp, vertical = 4.dp)
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
                    label,
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
                IconButton(onClick = { onFill(text) }) {
                    Icon(
                        Icons.AutoMirrored.Filled.CallMade,
                        contentDescription = stringResource(R.string.search_fill),
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer { rotationZ = -90f }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchEmptyState(icon: ImageVector, title: String, body: String) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(88.dp)
                .glassOr(CircleShape, scheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
    }
}
