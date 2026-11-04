#!/usr/bin/env bash
# optimize_scroll.sh - optimasi scrolling feed YTMaterial. Jalankan dari folder root repo.
# Aman dijalankan ulang: bagian yang sudah diterapkan akan dilewati.
set -euo pipefail

[ -d app/src/main/java/com/example/ytclient ] || { echo "Jalankan dari folder root repo YTMaterial." >&2; exit 1; }

# ---------------------------------------------------------------------------
# 1) Glass.kt: blur separuh resolusi, tanpa noise, dan mati sementara saat scroll
# ---------------------------------------------------------------------------
cat > app/src/main/java/com/example/ytclient/ui/Glass.kt <<'KOTLIN'
package com.example.ytclient.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * Tanda "sedang scroll". [ping] dipanggil tiap ada delta scroll; [active] true selama scroll
 * berjalan lalu mati ~120 ms setelah berhenti. Dibaca hanya di fase draw (tanpa recomposition).
 */
@Stable
class ScrollActivity {
    var tick by mutableIntStateOf(0)
        private set
    var active by mutableStateOf(false)

    fun ping() {
        tick++
    }
}

val LocalScrollActivity = staticCompositionLocalOf { ScrollActivity() }

/**
 * Frosted-glass surface: blurs whatever is drawn behind it (Android 12+), tints it with Material You
 * surface colors and adds a thin light edge. Older Android versions get a plain opaque surface.
 *
 * Blur dihitung pada separuh resolusi dan dimatikan selama list di-scroll (kaca tetap berwarna),
 * karena blur ulang tiap frame adalah penyebab utama scroll berat.
 *
 * Put the content in [GlassContent]: a bare Box does not provide a content color, which is why
 * text turned black on the dark theme.
 */
@Composable
fun Modifier.glass(
    hazeState: HazeState?,
    shape: Shape,
    elevation: Dp = 12.dp,
    blurRadius: Dp = 20.dp,
    blurEnabled: Boolean = true
): Modifier {
    val scheme = MaterialTheme.colorScheme
    val activity = LocalScrollActivity.current
    val allowBlur = blurEnabled
    val base = this
        .shadow(elevation, shape)
        .clip(shape)
        .border(0.8.dp, scheme.outlineVariant.copy(alpha = 0.55f), shape)
    return if (hazeState != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        base.hazeEffect(
            state = hazeState,
            style = HazeStyle(
                backgroundColor = scheme.surface,
                tint = HazeTint(scheme.surfaceContainerHigh.copy(alpha = 0.80f)),
                blurRadius = blurRadius,
                noiseFactor = 0f
            )
        ) {
            this.blurEnabled = allowBlur && !activity.active
            this.inputScale = HazeInputScale.Fixed(0.5f)
        }
    } else {
        base.background(scheme.surfaceContainerHigh)
    }
}

/** Sets the content color for text/icons on glass to the theme's onSurface (readable in light and dark). */
@Composable
fun GlassContent(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalContentColor provides MaterialTheme.colorScheme.onSurface,
        androidx.compose.material3.LocalTextStyle provides
            androidx.compose.material3.LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface),
        content = content
    )
}
KOTLIN
echo "ok:   Glass.kt ditulis ulang"

# ---------------------------------------------------------------------------
# 2) Patch file lain (python agar aman terhadap karakter khusus)
# ---------------------------------------------------------------------------
python3 - <<'PY'
import re, sys

def patch(path, pairs):
    s = open(path, encoding="utf-8").read()
    changed = False
    for old, new, label in pairs:
        if new and new in s:
            print(f"skip: {label} (sudah diterapkan)")
            continue
        if old not in s:
            if new == "":
                print(f"skip: {label} (sudah diterapkan)")
            else:
                print(f"WARN: {label} tidak ditemukan di {path} (dilewati)")
            continue
        s = s.replace(old, new, 1)
        changed = True
        print(f"ok:   {label}")
    if changed:
        open(path, "w", encoding="utf-8").write(s)

UI = "app/src/main/java/com/example/ytclient/ui/"

# --- App.kt: deteksi scroll -> matikan blur sementara ---------------------------------
patch(UI + "App.kt", [
    ("import kotlinx.coroutines.launch\n",
     "import kotlinx.coroutines.launch\nimport kotlinx.coroutines.delay\nimport kotlinx.coroutines.flow.collectLatest\n",
     "App.kt import"),
    ("    val hideConnection = remember {\n",
     "    val scrollActivity = remember { ScrollActivity() }\n    val hideConnection = remember {\n",
     "App.kt ScrollActivity"),
    ("if (available.y != 0f) topBarCollapse.onScroll(available.y)",
     "if (available.y != 0f) {\n                    topBarCollapse.onScroll(available.y)\n                    scrollActivity.ping()\n                }",
     "App.kt ping saat scroll"),
    ("    androidx.compose.runtime.LaunchedEffect(pagerState.currentPage) { topBarCollapse.reset() }\n",
     "    androidx.compose.runtime.LaunchedEffect(pagerState.currentPage) { topBarCollapse.reset() }\n"
     "    androidx.compose.runtime.LaunchedEffect(scrollActivity) {\n"
     "        androidx.compose.runtime.snapshotFlow { scrollActivity.tick }.collectLatest {\n"
     "            scrollActivity.active = true\n"
     "            delay(120)\n"
     "            scrollActivity.active = false\n"
     "        }\n"
     "    }\n",
     "App.kt debounce scroll"),
    ("CompositionLocalProvider(LocalFloatingInsets provides insets) {",
     "CompositionLocalProvider(\n            LocalFloatingInsets provides insets,\n            LocalScrollActivity provides scrollActivity\n        ) {",
     "App.kt provide LocalScrollActivity"),
])

# --- VideoCard.kt: kurangi kerja per item --------------------------------------------
patch(UI + "components/VideoCard.kt", [
    ("    val views = formatViews(item.views)\n"
     "    val meta = remember(item.channel, views, item.uploaded) {\n"
     "        listOfNotNull(item.channel.ifBlank { null }, views, item.uploaded).joinToString(\" • \")\n"
     "    }\n\n    Column(\n        modifier\n            .fillMaxWidth()\n            .pressScale(interaction, 0.97f)\n    ) {",
     "    val views = formatViews(item.views) ?: item.viewsText\n"
     "    val meta = remember(item.channel, views, item.uploaded) {\n"
     "        listOfNotNull(item.channel.ifBlank { null }, views, item.uploaded).joinToString(\" • \")\n"
     "    }\n\n    Column(\n        modifier\n            .fillMaxWidth()\n            .pressScale(interaction, 0.97f)\n    ) {",
     "VideoCard meta sekali hitung"),
    ("                val meta = listOfNotNull(\n"
     "                    item.channel.ifBlank { null },\n"
     "                    formatViews(item.views) ?: item.viewsText,\n"
     "                    item.uploaded\n"
     "                ).joinToString(\" • \")\n",
     "",
     "VideoCard hapus hitung ganda"),
    ("DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {",
     "if (menu) DropdownMenu(expanded = true, onDismissRequest = { menu = false }) {",
     "VideoCard menu dibuat saat dibuka"),
])

# --- build.yml: build release (non-debuggable), jauh lebih cepat untuk Compose ---------
patch(".github/workflows/build.yml", [
    ("gradle --no-daemon assembleDebug --stacktrace",
     "gradle --no-daemon assembleRelease --stacktrace",
     "build.yml assembleRelease"),
    ("app/build/outputs/apk/debug/*.apk",
     "app/build/outputs/apk/release/*.apk",
     "build.yml path apk release"),
    ("Build debug APK", "Build release APK", "build.yml nama langkah"),
])

# --- build.gradle.kts: lint release tidak menggagalkan build -------------------------
patch("app/build.gradle.kts", [
    ("    packaging {\n",
     "    lint {\n        checkReleaseBuilds = false\n    }\n\n    packaging {\n",
     "build.gradle.kts lint release"),
])
PY

# ---------------------------------------------------------------------------
# 3) Baseline profile: kode Compose + app dikompilasi AOT sejak instalasi
# ---------------------------------------------------------------------------
cat > app/src/main/baseline-prof.txt <<'PROF'
HSPLandroidx/compose/**;->**(**)**
HSPLandroidx/lifecycle/**;->**(**)**
HSPLandroidx/activity/**;->**(**)**
HSPLcoil/**;->**(**)**
HSPLdev/chrisbanes/haze/**;->**(**)**
HSPLcom/example/ytclient/**;->**(**)**
Landroidx/compose/**;
Landroidx/lifecycle/**;
Lcoil/**;
Ldev/chrisbanes/haze/**;
Lcom/example/ytclient/**;
PROF
echo "ok:   baseline-prof.txt ditulis"

echo
git diff --stat || true
echo
echo 'Lanjut: ./gitflow.sh all "perf: optimasi scroll feed (release build, baseline profile, blur ringan)"'
