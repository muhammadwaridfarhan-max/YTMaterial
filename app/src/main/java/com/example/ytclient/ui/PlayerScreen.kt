package com.example.ytclient.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import android.widget.Toast
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.ytclient.R
import com.example.ytclient.data.Repository
import com.example.ytclient.data.SubscriptionManager
import com.example.ytclient.ui.components.VideoRow
import androidx.compose.runtime.collectAsState
import com.example.ytclient.data.StreamTrack
import com.example.ytclient.data.VideoDownloader
import com.example.ytclient.data.VideoStreams
import com.example.ytclient.ui.components.VideoCard
import com.example.ytclient.ui.components.WavySeekBar
import com.example.ytclient.ui.components.formatViews
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

private val YouRed = Color(0xFFFF2D3C)
private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
private val SLEEP_MINUTES = listOf(15, 30, 45, 60, 90)
private val TRANSLATE_LANGS = listOf("en", "id", "es", "pt", "fr", "de", "ja", "ko", "ar", "hi", "zh-Hans", "ru")

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    controller: PlayerController,
    vm: MainViewModel,
    onMinimize: () -> Unit,
    onLoginRequest: () -> Unit = {},
    onChannelClick: (String) -> Unit = {}
) {
    val item = controller.current ?: return
    val context = LocalContext.current
    val subs by SubscriptionManager.channels.collectAsState(initial = emptyList())
    val subscribedNow = item.channel in subs
    val subScope = rememberCoroutineScope()
    var showQualitySheet by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    /** Player settings sheet page: null = closed, else "main" / "audio" / "subs" / "translate" / "sleep". */
    var morePage by remember { mutableStateOf<String?>(null) }
    var showDownloadSheet by remember { mutableStateOf(false) }
    var downloadMessage by remember { mutableStateOf<String?>(null) }
    val fullscreen = controller.isFullscreen

    var backProgress by remember { mutableFloatStateOf(0f) }
    val animatedBack by animateFloatAsState(
        backProgress,
        spring(stiffness = Spring.StiffnessHigh),
        label = "predictiveBack"
    )
    PredictiveBackHandler(enabled = !fullscreen) { progress ->
        try {
            progress.collect { backProgress = it.progress }
            onMinimize()
        } catch (e: CancellationException) {
            backProgress = 0f
        }
    }
    BackHandler(enabled = fullscreen) { controller.isFullscreen = false }

    LaunchedEffect(item.url) { vm.loadRelated(item) }

    DisposableEffect(fullscreen) {
        val act = context.findActivity()
        val window = act?.window
        if (act != null && window != null) {
            val ctrl = WindowCompat.getInsetsController(window, window.decorView)
            if (fullscreen) {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                ctrl.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                ctrl.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                ctrl.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose { }
    }
    DisposableEffect(Unit) {
        onDispose {
            val act = context.findActivity()
            val window = act?.window
            if (act != null && window != null) {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                WindowCompat.getInsetsController(window, window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
            controller.isFullscreen = false
        }
    }

    Surface(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = animatedBack
                transformOrigin = TransformOrigin(0.5f, 1f)
                scaleX = 1f - 0.14f * p
                scaleY = 1f - 0.14f * p
                translationY = size.height * 0.04f * p
                alpha = 1f - 0.25f * p
                shape = RoundedCornerShape((32f * p).dp)
                clip = p > 0.001f
            },
        color = MaterialTheme.colorScheme.surface
    ) {
      Box(Modifier.fillMaxSize()) {
        if (!fullscreen) {
            // Ambient light: blurred, palette-tinted glow of the thumbnail behind the details.
            AmbientGlow(
                imageUrl = item.thumbnail,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(520.dp)
            )
        }
        Column(Modifier.fillMaxSize()) {
            val areaModifier = if (fullscreen) {
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            } else {
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .statusBarsPadding()
                    // Slightly taller than 16:9 in portrait: bigger stage, room for the overlay controls.
                    .aspectRatio(16f / 10f)
            }
            Box(areaModifier) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            this.player = controller.player
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                        }
                    }
                )
                PlayerControls(
                    controller = controller,
                    title = item.title,
                    onMinimize = onMinimize,
                    onQuality = { showQualitySheet = true },
                    onSpeed = { showSpeedSheet = true },
                    onMore = { morePage = "main" }
                )
            }

            if (!fullscreen) {
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                item.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                listOfNotNull(formatViews(item.views), item.uploaded).joinToString(" • "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(14.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onChannelClick(item.channel) }
                                ) {
                                    Box(
                                        Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!item.channelAvatar.isNullOrBlank()) {
                                            AsyncImage(
                                                model = item.channelAvatar,
                                                contentDescription = item.channel,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            item.channel,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Button(
                                    onClick = {
                                        subScope.launch { SubscriptionManager.toggle(item.channel) }
                                    },
                                    shape = CircleShape,
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.onSurface,
                                        contentColor = MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Text(stringResource(if (subscribedNow) R.string.subscribed else R.string.subscribe))
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AssistChip(
                                    shape = CircleShape,
                                    onClick = { },
                                    label = { Text(stringResource(R.string.like)) },
                                    leadingIcon = { Icon(Icons.Filled.ThumbUp, contentDescription = null) }
                                )
                                AssistChip(
                                    shape = CircleShape,
                                    onClick = { },
                                    label = { Text(stringResource(R.string.share)) },
                                    leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) }
                                )
                                AssistChip(
                                    shape = CircleShape,
                                    onClick = { showDownloadSheet = true },
                                    label = { Text(stringResource(R.string.download)) },
                                    leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) }
                                )
                            }
                        }
                        HorizontalDivider()
                        Text(
                            stringResource(R.string.up_next),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }

                    when (val rs = vm.relatedState) {
                        null, UiState.Loading -> item {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) { CircularProgressIndicator() }
                        }

                        is UiState.Error -> item {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    rs.message,
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                TextButton(onClick = { vm.loadRelated(item) }) {
                                    Text(stringResource(R.string.retry))
                                }
                            }
                        }

                        is UiState.Success -> items(rs.items, key = { it.url }) { video ->
                            VideoRow(
                                video,
                                onClick = { controller.open(video) },
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .animateItem()
                            )
                        }
                    }
                }
            }
        }
      }
    }

    if (showQualitySheet) {
        val streams = controller.streams
        ModalBottomSheet(onDismissRequest = { showQualitySheet = false }, containerColor = glassSheetColor()) {
            Column(Modifier.padding(bottom = 32.dp)) {
                SheetTitle(stringResource(R.string.quality))
                if (streams == null) {
                    Text(
                        stringResource(R.string.err_no_stream),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                } else {
                    OptionRow(
                        label = stringResource(R.string.quality_auto),
                        selected = controller.qualityHeight == -1
                    ) {
                        controller.setQuality(-1)
                        showQualitySheet = false
                    }
                    streams.heights.forEach { h ->
                        OptionRow(label = "${h}p", selected = controller.qualityHeight == h) {
                            controller.setQuality(h)
                            showQualitySheet = false
                        }
                    }
                }
            }
        }
    }

    if (showSpeedSheet) {
        ModalBottomSheet(onDismissRequest = { showSpeedSheet = false }, containerColor = glassSheetColor()) {
            Column(Modifier.padding(bottom = 32.dp)) {
                SheetTitle(stringResource(R.string.playback_speed))
                SPEEDS.forEach { sp ->
                    OptionRow(label = speedLabel(sp), selected = controller.speed == sp) {
                        controller.setSpeed(sp)
                        showSpeedSheet = false
                    }
                }
            }
        }
    }

    val page = morePage
    if (page != null) {
        val streams = controller.streams
        val offLabel = stringResource(R.string.off)
        val originalLabel = stringResource(R.string.audio_original)
        val autoGenLabel = stringResource(R.string.subtitle_auto_generated)
        val endOfVideoLabel = stringResource(R.string.sleep_end_of_video)
        val minutesFmt = stringResource(R.string.sleep_minutes)
        val currentAudio = streams?.audioTracks?.firstOrNull { it.id == controller.audioTrackId }
        val currentSub = streams?.subtitles?.firstOrNull {
            it.languageTag == controller.subtitleLang && it.autoGenerated == controller.subtitleAuto
        } ?: streams?.subtitles?.firstOrNull { it.languageTag == controller.subtitleLang }
        ModalBottomSheet(onDismissRequest = { morePage = null }, containerColor = glassSheetColor()) {
            Column(
                Modifier
                    .padding(bottom = 32.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                when (page) {
                    "audio" -> {
                        SheetTitle(stringResource(R.string.audio_language))
                        val tracks = streams?.audioTracks.orEmpty()
                        if (tracks.isEmpty()) {
                            Text(
                                stringResource(R.string.audio_single_track),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                        tracks.forEach { t ->
                            OptionRow(
                                label = if (t.isOriginal && !t.name.contains("original", true)) "${t.name} ($originalLabel)" else t.name,
                                selected = controller.audioTrackId == t.id
                            ) {
                                controller.setAudioTrack(t)
                                morePage = null
                            }
                        }
                    }
                    "subs" -> {
                        SheetTitle(stringResource(R.string.subtitles))
                        OptionRow(label = offLabel, selected = controller.subtitleLang.isEmpty()) {
                            controller.setSubtitle(null)
                            morePage = null
                        }
                        streams?.subtitles.orEmpty().forEach { sub ->
                            OptionRow(
                                label = if (sub.autoGenerated) "${sub.name} ($autoGenLabel)" else sub.name,
                                selected = currentSub == sub
                            ) {
                                controller.setSubtitle(sub, controller.subtitleTranslate)
                                morePage = null
                            }
                        }
                    }
                    "translate" -> {
                        SheetTitle(stringResource(R.string.subtitle_translate))
                        OptionRow(label = offLabel, selected = controller.subtitleTranslate.isEmpty()) {
                            controller.setSubtitle(currentSub, "")
                            morePage = null
                        }
                        TRANSLATE_LANGS.forEach { tag ->
                            OptionRow(
                                label = java.util.Locale.forLanguageTag(tag).let { it.getDisplayName(it) }
                                    .replaceFirstChar { it.uppercase() },
                                selected = controller.subtitleTranslate == tag
                            ) {
                                controller.setSubtitle(currentSub, tag)
                                morePage = null
                            }
                        }
                    }
                    "sleep" -> {
                        SheetTitle(stringResource(R.string.sleep_timer))
                        OptionRow(
                            label = offLabel,
                            selected = controller.sleepAtMs == 0L && !controller.sleepEndOfVideo
                        ) {
                            controller.setSleepTimer(0)
                            morePage = null
                        }
                        SLEEP_MINUTES.forEach { m ->
                            OptionRow(label = String.format(minutesFmt, m), selected = false) {
                                controller.setSleepTimer(m)
                                morePage = null
                            }
                        }
                        OptionRow(label = endOfVideoLabel, selected = controller.sleepEndOfVideo) {
                            controller.setSleepTimer(-1)
                            morePage = null
                        }
                    }
                    else -> {
                        SheetTitle(stringResource(R.string.player_settings))
                        SettingRow(
                            stringResource(R.string.audio_language),
                            when {
                                streams?.audioTracks.isNullOrEmpty() -> originalLabel
                                else -> currentAudio?.name ?: originalLabel
                            }
                        ) { morePage = "audio" }
                        SettingRow(
                            stringResource(R.string.subtitles),
                            currentSub?.name ?: offLabel
                        ) { morePage = "subs" }
                        if (currentSub != null && controller.canTranslate(currentSub)) {
                            SettingRow(
                                stringResource(R.string.subtitle_translate),
                                controller.subtitleTranslate.ifEmpty { offLabel }
                            ) { morePage = "translate" }
                        }
                        SettingRow(
                            stringResource(R.string.sleep_timer),
                            when {
                                controller.sleepEndOfVideo -> endOfVideoLabel
                                controller.sleepAtMs > 0L -> String.format(
                                    minutesFmt,
                                    ((controller.sleepAtMs - System.currentTimeMillis()) / 60_000L + 1).toInt()
                                )
                                else -> offLabel
                            }
                        ) { morePage = "sleep" }
                    }
                }
            }
        }
    }

    if (showDownloadSheet) {
        val streams = controller.streams
        ModalBottomSheet(onDismissRequest = { showDownloadSheet = false }, containerColor = glassSheetColor()) {
            Column(Modifier.padding(bottom = 32.dp)) {
                SheetTitle(stringResource(R.string.download))
                val progress = VideoDownloader.progress
                if (streams == null || item.url.isBlank()) {
                    Text(
                        stringResource(R.string.err_no_stream),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                } else if (progress != null) {
                    Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
                        Text(
                            stringResource(R.string.download_starting) + " " + (progress * 100).toInt() + "%",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    val audioLabel = stringResource(R.string.download_audio_only)
                    downloadOptions(streams, audioLabel).forEach { opt ->
                        OptionRow(label = opt.label, selected = false) {
                            VideoDownloader.start(
                                context, item.title, opt.track, opt.mux, streams.audioUrl, streams.audioExt
                            )
                        }
                    }
                }
            }
        }
    }

    downloadMessage?.let { msg ->
        LaunchedEffect(msg) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            downloadMessage = null
        }
    }
}

private data class DlOption(val label: String, val track: StreamTrack?, val mux: Boolean)

/** One entry per resolution: progressive (has audio) first, otherwise H.264/H.265 video-only + audio merged. */
private fun downloadOptions(s: VideoStreams, audioLabel: String): List<DlOption> {
    val candidates = mutableListOf<Pair<StreamTrack, Boolean>>()
    s.progressive.forEach { candidates.add(it to false) }
    if (s.audioUrl != null && s.audioExt == "m4a") {
        s.videoOnly
            .filter { it.ext == "mp4" && (it.codec.startsWith("avc") || it.codec.startsWith("hev") || it.codec.startsWith("hvc")) }
            .forEach { candidates.add(it to true) }
    }
    val seen = mutableSetOf<Int>()
    val out = mutableListOf<DlOption>()
    candidates
        .sortedWith(
            compareByDescending<Pair<StreamTrack, Boolean>> { it.first.height }
                .thenBy { it.second }
                .thenByDescending { it.first.fps }
        )
        .forEach { (track, mux) ->
            if (seen.add(track.height)) {
                val fps = if (track.fps > 30) track.fps.toString() else ""
                out.add(DlOption("${track.height}p$fps", track, mux))
            }
        }
    if (s.audioUrl != null) out.add(DlOption(audioLabel, null, false))
    return out
}

private fun speedLabel(s: Float) = if (s == 1f) "Normal" else "${s.toString().removeSuffix(".0")}x"

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
private fun PlayerControls(
    controller: PlayerController,
    title: String,
    onMinimize: () -> Unit,
    onQuality: () -> Unit,
    onSpeed: () -> Unit,
    onMore: () -> Unit
) {
    val player = controller.player
    val fullscreen = controller.isFullscreen
    val isLive = controller.streams?.isLive == true

    var visible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var scrubbing by remember { mutableStateOf(false) }

    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var buffered by remember { mutableLongStateOf(0L) }

    var flashSide by remember { mutableIntStateOf(0) }
    var flashTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition
            duration = player.duration.coerceAtLeast(0L)
            buffered = player.bufferedPosition
            delay(200)
        }
    }
    LaunchedEffect(visible, controller.isPlayingState, scrubbing, interaction) {
        if (visible && controller.isPlayingState && !scrubbing) {
            delay(3500)
            visible = false
        }
    }
    LaunchedEffect(flashTick) {
        if (flashSide != 0) {
            delay(550)
            flashSide = 0
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { visible = !visible; interaction++ },
                    onDoubleTap = { offset ->
                        val forward = offset.x > size.width / 2f
                        controller.seekBy(if (forward) 10_000L else -10_000L)
                        flashSide = if (forward) 1 else -1
                        flashTick++
                    }
                )
            }
    ) {
        AnimatedVisibility(
            visible = flashSide != 0,
            enter = fadeIn() + scaleIn(initialScale = 0.7f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
            modifier = Modifier.align(
                if (flashSide >= 0) Alignment.CenterEnd else Alignment.CenterStart
            )
        ) {
            Box(
                Modifier
                    .padding(horizontal = 48.dp)
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (flashSide >= 0) Icons.Filled.Forward10 else Icons.Filled.Replay10,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        if (controller.isLoading || controller.isBuffering) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }

        controller.error?.let { err ->
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(err, color = Color(0xFFFF8A80), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = controller::retry) {
                    Text(stringResource(R.string.retry), color = Color.White)
                }
            }
        }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.65f),
                            0.35f to Color.Black.copy(alpha = 0.15f),
                            0.65f to Color.Black.copy(alpha = 0.15f),
                            1f to Color.Black.copy(alpha = 0.75f)
                        )
                    )
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        if (fullscreen) controller.isFullscreen = false else onMinimize()
                    }) {
                        Icon(
                            if (fullscreen) Icons.AutoMirrored.Filled.ArrowBack
                            else Icons.Filled.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.close),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    if (fullscreen) {
                        Text(
                            title,
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    ChipButton(speedLabel(controller.speed), onSpeed)
                    Spacer(Modifier.width(6.dp))
                    ChipButton(
                        if (controller.qualityHeight <= 0) stringResource(R.string.quality_auto)
                        else "${controller.qualityHeight}p",
                        onQuality
                    )
                    IconButton(onClick = onMore) {
                        Icon(
                            Icons.Filled.Tune,
                            contentDescription = stringResource(R.string.player_settings),
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                if (!(controller.isLoading || controller.isBuffering)) {
                    val controlsGlass = if (liquidGlassOn()) {
                        Modifier
                            .glassSurface(CircleShape, Color.Black.copy(alpha = 0.28f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    } else Modifier
                    Row(
                        Modifier
                            .align(Alignment.Center)
                            .then(controlsGlass),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        IconButton(onClick = { controller.seekBy(-10_000L); interaction++ }) {
                            Icon(
                                Icons.Filled.Replay10, contentDescription = null,
                                tint = Color.White, modifier = Modifier.size(26.dp)
                            )
                        }
                        Box(
                            Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(
                                onClick = { controller.togglePlay(); interaction++ },
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    if (controller.isPlayingState) Icons.Filled.Pause
                                    else Icons.Filled.PlayArrow,
                                    contentDescription = stringResource(
                                        if (controller.isPlayingState) R.string.pause else R.string.play
                                    ),
                                    tint = Color.White,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                        IconButton(onClick = { controller.seekBy(10_000L); interaction++ }) {
                            Icon(
                                Icons.Filled.Forward10, contentDescription = null,
                                tint = Color.White, modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }

                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                ) {
                    if (!isLive) {
                        WavySeekBar(
                            position = position,
                            duration = duration,
                            buffered = buffered,
                            isPlaying = controller.isPlayingState,
                            onSeek = { controller.seekTo(it); position = it; interaction++ },
                            onScrubbing = { scrubbing = it },
                            activeColor = YouRed
                        )
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (isLive) stringResource(R.string.live)
                            else "${formatTime(position)} / ${formatTime(duration)}",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = {
                            controller.isFullscreen = !fullscreen
                            interaction++
                        }) {
                            Icon(
                                if (fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                contentDescription = stringResource(R.string.fullscreen),
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChipButton(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = Color.White.copy(alpha = 0.18f)
    ) {
        Text(
            label,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun SheetTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
    )
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (selected) {
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun SettingRow(label: String, value: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
