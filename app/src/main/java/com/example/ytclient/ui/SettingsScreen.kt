@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.ytclient.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ytclient.R
import com.example.ytclient.auth.AuthManager
import com.example.ytclient.settings.AppLanguage
import com.example.ytclient.settings.AppSettings
import com.example.ytclient.settings.ColorSource
import com.example.ytclient.settings.Resolution
import com.example.ytclient.settings.SettingsManager
import com.example.ytclient.settings.ThemeMode
import com.example.ytclient.ui.theme.SeedColorSwatches
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class SettingsDialog { THEME, COLOR, LANGUAGE, RESOLUTION, BLUR, BLUR_ENABLED }

@Composable
fun YouScreen(onSignIn: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val settings by SettingsManager.settings.collectAsState(initial = AppSettings())
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    val signedIn by AuthManager.signedIn.collectAsState(initial = false)

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            top = LocalFloatingInsets.current.top,
            bottom = LocalFloatingInsets.current.bottom
        )
    ) {
        item {
            Text(
                stringResource(R.string.nav_you),
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(12.dp))
            // Google profile (photo + name) — syncs from Credential Manager
            AccountCard()
            Spacer(Modifier.height(12.dp))
            // YouTube account (device-code / TV login) for feed & subs sync
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.AccountCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(40.dp)
                    )
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                    ) {
                        Text(
                            stringResource(if (signedIn) R.string.signed_in else R.string.sign_in),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            stringResource(R.string.login_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    if (signedIn) {
                        TextButton(onClick = { scope.launch { AuthManager.signOut() } }) {
                            Text(stringResource(R.string.sign_out))
                        }
                    } else {
                        Button(onClick = onSignIn) { Text(stringResource(R.string.sign_in_short)) }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        item {
            SettingsItem(
                icon = Icons.Filled.DarkMode,
                title = stringResource(R.string.theme),
                value = themeLabel(settings.themeMode)
            ) { dialog = SettingsDialog.THEME }
        }
        item {
            SettingsItem(
                icon = Icons.Filled.Palette,
                title = stringResource(R.string.color_source),
                value = colorLabel(settings)
            ) { dialog = SettingsDialog.COLOR }
        }
        item {
            SettingsItem(
                icon = Icons.Filled.Translate,
                title = stringResource(R.string.language),
                value = settings.language.display
            ) { dialog = SettingsDialog.LANGUAGE }
        }
        item {
            SettingsItem(
                icon = Icons.Filled.Hd,
                title = stringResource(R.string.default_resolution),
                value = resolutionLabel(settings.resolution)
            ) { dialog = SettingsDialog.RESOLUTION }
        }
        item {
            SettingsItem(
                icon = Icons.Filled.DarkMode,
                title = stringResource(R.string.blur_strength),
                value = if (settings.blurStrength <= 12) stringResource(R.string.blur_light) else stringResource(R.string.blur_heavy)
            ) { dialog = SettingsDialog.BLUR }
        }
        item {
            val checkedLabel = if (settings.blurEnabled) stringResource(R.string.blur_enabled_on) else stringResource(R.string.blur_enabled_off)
            SettingsItem(
                icon = Icons.Filled.CheckBox,
                title = stringResource(R.string.blur_toggle),
                value = checkedLabel
            ) { dialog = SettingsDialog.BLUR_ENABLED }
        }
    }

    when (dialog) {
        SettingsDialog.THEME -> ThemeDialog(
            current = settings.themeMode,
            onSelect = { scope.launch { SettingsManager.setThemeMode(it) } },
            onDismiss = { dialog = null }
        )

        SettingsDialog.COLOR -> ColorDialog(
            settings = settings,
            onSource = { scope.launch { SettingsManager.setColorSource(it) } },
            onColor = { scope.launch { SettingsManager.setSeedColor(it) } },
            onDismiss = { dialog = null }
        )

        SettingsDialog.LANGUAGE -> LanguageDialog(
            current = settings.language,
            onSelect = {
                scope.launch { SettingsManager.setLanguage(it) }
                dialog = null
            },
            onDismiss = { dialog = null }
        )

        SettingsDialog.RESOLUTION -> ResolutionDialog(
            current = settings.resolution,
            onSelect = {
                scope.launch { SettingsManager.setResolution(it) }
                dialog = null
            },
            onDismiss = { dialog = null }
        )

        SettingsDialog.BLUR -> BlurDialog(
            current = settings.blurStrength,
            onSelect = {
                scope.launch { SettingsManager.setBlurStrength(it) }
                dialog = null
            },
            onDismiss = { dialog = null }
        )

        SettingsDialog.BLUR_ENABLED -> BlurEnabledDialog(
            current = settings.blurEnabled,
            onSelect = {
                scope.launch { SettingsManager.setBlurEnabled(it) }
                dialog = null
            },
            onDismiss = { dialog = null }
        )

        null -> Unit
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun themeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    }
)

@Composable
private fun colorLabel(settings: AppSettings): String = when (settings.colorSource) {
    ColorSource.DYNAMIC -> stringResource(R.string.color_dynamic)
    ColorSource.CUSTOM -> "#%06X".format(settings.seedColor and 0xFFFFFF)
}

@Composable
private fun resolutionLabel(resolution: Resolution): String =
    if (resolution == Resolution.AUTO) {
        stringResource(R.string.quality_auto)
    } else {
        "${resolution.height}p"
    }

@Composable
private fun BlurEnabledDialog(
    current: Boolean,
    onSelect: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.blur_toggle)) },
        text = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelect(true) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = true, onClick = { onSelect(true) })
                    Text(
                        stringResource(R.string.blur_enabled_on),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelect(false) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = false, onClick = { onSelect(false) })
                    Text(
                        stringResource(R.string.blur_enabled_off),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun ThemeDialog(
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    val modes = ThemeMode.entries
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.theme)) },
        text = {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                modes.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = current == mode,
                        onClick = { onSelect(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size)
                    ) {
                        Text(
                            themeLabel(mode),
                            maxLines = 1,
                            modifier = Modifier.widthIn(max = 72.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorDialog(
    settings: AppSettings,
    onSource: (ColorSource) -> Unit,
    onColor: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var hexInput by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.color_source)) },
        text = {
            Column {
                val sources = ColorSource.entries
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    sources.forEachIndexed { index, source ->
                        SegmentedButton(
                            selected = settings.colorSource == source,
                            onClick = { onSource(source) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = sources.size)
                        ) {
                            Text(
                                stringResource(
                                    if (source == ColorSource.DYNAMIC) R.string.color_dynamic
                                    else R.string.color_custom
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (settings.colorSource == ColorSource.DYNAMIC) {
                    Text(
                        stringResource(R.string.color_dynamic_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SeedColorSwatches.forEach { swatch ->
                            val selected = settings.seedColor == swatch
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(swatch))
                                    .border(
                                        width = if (selected) 3.dp else 1.dp,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant
                                        },
                                        shape = CircleShape
                                    )
                                    .clickable { onColor(swatch) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = hexInput,
                            onValueChange = { hexInput = it },
                            label = { Text(stringResource(R.string.custom_hex)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        val parsed = parseHexColor(hexInput)
                        Button(
                            onClick = { parsed?.let(onColor) },
                            enabled = parsed != null
                        ) {
                            Text(stringResource(R.string.apply))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

private fun parseHexColor(input: String): Int? {
    val hex = input.trim().removePrefix("#")
    if (hex.length != 6) return null
    val rgb = hex.toLongOrNull(16)?.toInt() ?: return null
    return (0xFF shl 24) or rgb
}

@Composable
private fun LanguageDialog(
    current: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language)) },
        text = {
            Column {
                AppLanguage.entries.forEach { lang ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelect(lang) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = current == lang, onClick = { onSelect(lang) })
                        Text(
                            if (lang == AppLanguage.SYSTEM) {
                                stringResource(R.string.language_system)
                            } else lang.display,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun ResolutionDialog(
    current: Resolution,
    onSelect: (Resolution) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.default_resolution)) },
        text = {
            Column {
                Resolution.entries.forEach { res ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelect(res) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = current == res, onClick = { onSelect(res) })
                        Text(resolutionLabel(res), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun BlurDialog(
    current: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableFloatStateOf(current.toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.blur_strength)) },
        text = {
            Column {
                Text(
                    text = "${value.roundToInt()}  ·  " +
                        if (value <= 12) stringResource(R.string.blur_light) else stringResource(R.string.blur_heavy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Slider(
                    value = value,
                    onValueChange = { value = it },
                    valueRange = 0f..40f,
                    steps = 20,
                    onValueChangeFinished = { onSelect(value.roundToInt()) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
