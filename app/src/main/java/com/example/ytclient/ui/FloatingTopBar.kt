package com.example.ytclient.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ytclient.R
import dev.chrisbanes.haze.HazeState
import kotlin.math.roundToInt

/**
 * MeeTube-style floating bar: compact brand pill + round glass buttons. [collapse] (0..1) is read
 * in layout/draw phases only: the brand text folds away and the buttons shrink slightly as the
 * page scrolls, with no recomposition per frame.
 */
@Composable
fun FloatingTopBar(
    visible: Boolean,
    collapse: () -> Float,
    photoUrl: String?,
    onSearch: () -> Unit,
    onProfile: () -> Unit,
    hazeState: HazeState?,
    blurStrength: Int,
    blurEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(180)) + slideInVertically(tween(260)) { -it },
        exit = fadeOut(tween(140)) + slideOutVertically(tween(200)) { -it }
    ) {
        val scheme = MaterialTheme.colorScheme
        Row(
            Modifier
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .graphicsLayer {
                        val s = 1f - 0.08f * collapse()
                        scaleX = s
                        scaleY = s
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                    }
                    .glass(
                        hazeState, RoundedCornerShape(22.dp), elevation = 6.dp,
                        blurRadius = blurStrength.dp, blurEnabled = blurEnabled
                    )
            ) {
                GlassContent {
                    Row(
                        Modifier.height(44.dp).padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(scheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.PlayArrow, contentDescription = null,
                                tint = scheme.onPrimary, modifier = Modifier.size(18.dp)
                            )
                        }
                        // Folds to zero width as the page scrolls; the pill ends up a 44dp circle.
                        Row(
                            Modifier
                                .layout { measurable, constraints ->
                                    val p = measurable.measure(constraints.copy(minWidth = 0))
                                    val w = (p.width * (1f - collapse())).roundToInt()
                                    layout(w, p.height) { p.placeRelative(0, 0) }
                                }
                                .graphicsLayer {
                                    alpha = 1f - collapse()
                                    clip = true
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = scheme.onSurface,
                                maxLines = 1,
                                softWrap = false
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            GlassCircleButton(
                onClick = onSearch, hazeState = hazeState, blurStrength = blurStrength,
                blurEnabled = blurEnabled, collapse = collapse,
                description = stringResource(R.string.search)
            ) {
                Icon(Icons.Filled.Search, null, tint = scheme.onSurface)
            }
            Spacer(Modifier.width(8.dp))
            GlassCircleButton(
                onClick = onProfile, hazeState = hazeState, blurStrength = blurStrength,
                blurEnabled = blurEnabled, collapse = collapse,
                description = stringResource(R.string.nav_you)
            ) {
                if (photoUrl != null) {
                    // Same photo as the Google/YouTube account that is signed in.
                    AsyncImage(
                        model = photoUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(32.dp).clip(CircleShape)
                    )
                } else {
                    Icon(Icons.Filled.Person, null, tint = scheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun GlassCircleButton(
    onClick: () -> Unit,
    hazeState: HazeState?,
    blurStrength: Int,
    blurEnabled: Boolean,
    collapse: () -> Float,
    description: String,
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .pressScale(interaction, 0.88f)
            .graphicsLayer {
                val s = 1f - 0.12f * collapse()
                scaleX = s
                scaleY = s
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0.5f)
            }
            .glass(
                hazeState, CircleShape, elevation = 6.dp,
                blurRadius = blurStrength.dp, blurEnabled = blurEnabled
            )
    ) {
        GlassContent {
            IconButton(onClick = onClick, interactionSource = interaction, modifier = Modifier.size(44.dp)) { content() }
        }
    }
}
