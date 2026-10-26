package org.nivora.launcher

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.ImageBitmap
import android.graphics.Bitmap
import android.graphics.Canvas

private val Midnight = Color(0xFF10141D)
private val Panel = Color(0xCC252D3B)
private val Cyan = Color(0xFF66D9E8)
private val Lavender = Color(0xFFC8B7FF)
private val Muted = Color(0xFF9AA6B8)

private data class LauncherApp(
    val label: String,
    val packageName: String,
    val icon: Drawable
)

class MainActivity : ComponentActivity() {

    private var refreshApps: (() -> Unit)? = null

    override fun onResume() {
        super.onResume()
        refreshApps?.invoke()
    }

    override fun onDestroy() {
        refreshApps = null
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Midnight.toArgb()
        window.navigationBarColor = Midnight.toArgb()

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Cyan,
                    secondary = Lavender,
                    background = Midnight,
                    surface = Panel,
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
                NivoraHome(
                    loadApps = { queryApps() },
                    registerRefresh = { refresh -> refreshApps = refresh },
                    launchApp = { packageName -> launchPackage(packageName) }
                )
            }
        }
    }

    private fun queryApps(): List<LauncherApp> {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        return try {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
                .mapNotNull { resolve ->
                    val activityInfo = resolve.activityInfo ?: return@mapNotNull null
                    if (activityInfo.packageName == packageName) return@mapNotNull null

                    LauncherApp(
                        label = resolve.loadLabel(pm).toString(),
                        packageName = activityInfo.packageName,
                        icon = resolve.loadIcon(pm)
                    )
                }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun launchPackage(packageName: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
                ?: return
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (_: Exception) {
            // An app may have been removed or disabled since the list was loaded.
        }
    }
}

private enum class LauncherScreen {
    HOME, LIBRARY, SETTINGS
}

@Composable
private fun NivoraHome(
    loadApps: () -> List<LauncherApp>,
    registerRefresh: ((() -> Unit) -> Unit),
    launchApp: (String) -> Unit
) {
    var apps by remember { mutableStateOf(emptyList<LauncherApp>()) }
    var search by remember { mutableStateOf("") }
    var screen by remember { mutableStateOf(LauncherScreen.HOME) }
    var homePage by remember { mutableStateOf(0) }
    var menuExpanded by remember { mutableStateOf(false) }

    val refresh: () -> Unit = { apps = loadApps() }

    LaunchedEffect(Unit) {
        registerRefresh(refresh)
        refresh()
    }

    val filteredApps = remember(apps, search) {
        apps.filter { it.label.contains(search.trim(), ignoreCase = true) }
    }

    val pageSize = 16
    val pageCount = maxOf(1, (apps.size + pageSize - 1) / pageSize)
    val safePage = homePage.coerceIn(0, pageCount - 1)

    val currentScreen = rememberUpdatedState(screen)
    val currentPage = rememberUpdatedState(safePage)
    val currentPageCount = rememberUpdatedState(pageCount)
    val currentSearch = rememberUpdatedState(search)

    val homeSwipeModifier = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial
            )

            var totalX = 0f
            var totalY = 0f
            var handled = false

            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull() ?: break
                val delta = change.positionChange()
                totalX += delta.x
                totalY += delta.y

                if (!handled &&
                    kotlin.math.abs(totalY) > 72f &&
                    kotlin.math.abs(totalY) > kotlin.math.abs(totalX) * 1.2f
                ) {
                    if (totalY < 0f &&
                        currentScreen.value == LauncherScreen.HOME
                    ) {
                        change.consume()
                        search = ""
                        screen = LauncherScreen.LIBRARY
                        handled = true
                    }
                } else if (!handled &&
                    kotlin.math.abs(totalX) > 72f &&
                    kotlin.math.abs(totalX) > kotlin.math.abs(totalY) * 1.2f &&
                    currentScreen.value == LauncherScreen.HOME &&
                    currentSearch.value.isBlank()
                ) {
                    change.consume()
                    if (totalX < 0f &&
                        currentPage.value < currentPageCount.value - 1
                    ) {
                        homePage = currentPage.value + 1
                    } else if (totalX > 0f && currentPage.value > 0) {
                        homePage = currentPage.value - 1
                    }
                    handled = true
                }
            } while (event.changes.any { it.pressed })
        }
    }

    val libraryVerticalSwipeModifier = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial
            )

            var totalX = 0f
            var totalY = 0f
            var handled = false

            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull() ?: break
                val delta = change.positionChange()
                totalX += delta.x
                totalY += delta.y

                if (!handled &&
                    totalY > 72f &&
                    totalY > kotlin.math.abs(totalX) * 1.2f &&
                    currentScreen.value == LauncherScreen.LIBRARY
                ) {
                    change.consume()
                    search = ""
                    screen = LauncherScreen.HOME
                    handled = true
                }
            } while (event.changes.any { it.pressed })
        }
    }

    val homeApps = apps.drop(safePage * pageSize).take(pageSize)

    val dockApps = remember(apps) {
        val preferred = listOf(
            listOf("phone", "dialer"),
            listOf("message", "sms"),
            listOf("chrome", "browser"),
            listOf("camera")
        )
        val selected = preferred.mapNotNull { keywords ->
            apps.firstOrNull { app ->
                keywords.any { word ->
                    app.label.contains(word, ignoreCase = true) ||
                        app.packageName.contains(word, ignoreCase = true)
                }
            }
        }.distinctBy { it.packageName }.toMutableList()

        apps.forEach { app ->
            if (selected.size < 4 &&
                selected.none { it.packageName == app.packageName }
            ) selected.add(app)
        }
        selected.take(4)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Midnight
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 20.dp)
                .then(homeSwipeModifier)
        ) {
            when (screen) {
                LauncherScreen.HOME -> {
                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Panel)
                            .padding(start = 14.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⌕", color = Cyan, fontSize = 23.sp)
                        Spacer(Modifier.size(10.dp))
                        BasicTextField(
                            value = search,
                            onValueChange = { search = it; homePage = 0 },
                            modifier = Modifier.weight(1f).padding(vertical = 11.dp),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Color.White,
                                fontSize = 15.sp
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            decorationBox = { innerTextField ->
                                Box {
                                    if (search.isEmpty()) {
                                        Text("Search apps", color = Muted, fontSize = 15.sp)
                                    }
                                    innerTextField()
                                }
                            }
                        )
                        Box {
                            Text(
                                "⋮",
                                color = Color.White,
                                fontSize = 25.sp,
                                modifier = Modifier
                                    .clickable { menuExpanded = true }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Settings") },
                                    onClick = {
                                        menuExpanded = false
                                        screen = LauncherScreen.SETTINGS
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    val visibleHomeApps =
                        if (search.isBlank()) homeApps else filteredApps.take(pageSize)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        if (visibleHomeApps.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (search.isBlank()) "No launchable apps found"
                                    else "No apps match your search",
                                    color = Muted
                                )
                            }
                        } else {
                            AnimatedContent(
                                targetState = if (search.isBlank()) safePage else 0,
                                modifier = Modifier.fillMaxSize(),
                                transitionSpec = {
                                    val direction = if (targetState > initialState) 1 else -1
                                    slideInHorizontally(
                                        animationSpec = tween(220),
                                        initialOffsetX = { width -> direction * width / 5 }
                                    ) + fadeIn(animationSpec = tween(180)) togetherWith
                                        slideOutHorizontally(
                                            animationSpec = tween(220),
                                            targetOffsetX = { width -> -direction * width / 5 }
                                        ) + fadeOut(animationSpec = tween(160))
                                },
                                label = "HomePageTransition"
                            ) { animatedPage ->
                                val animatedApps = if (search.isBlank()) {
                                    apps.drop(animatedPage * pageSize).take(pageSize)
                                } else {
                                    filteredApps.take(pageSize)
                                }
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(4),
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(20.dp)
                                ) {
                                    items(animatedApps, key = { it.packageName }) { app ->
                                        AppTile(
                                            app = app,
                                            onClick = { launchApp(app.packageName) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (search.isBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(pageCount) { index ->
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 4.dp)
                                        .size(if (index == safePage) 8.dp else 6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (index == safePage) Color.White
                                            else Muted.copy(alpha = 0.55f)
                                        )
                                        .clickable { homePage = index }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .background(Panel)
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        dockApps.forEach { app ->
                            val bitmap = remember(app.icon) {
                                app.icon.toImageBitmap()
                            }
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { launchApp(app.packageName) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (bitmap != null) {
                                    androidx.compose.foundation.Image(
                                        bitmap = bitmap,
                                        contentDescription = app.label,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(3.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                    )
                                } else {
                                    Text(
                                        app.label.take(1),
                                        color = Cyan,
                                        fontSize = 24.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                }

                LauncherScreen.LIBRARY -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(libraryVerticalSwipeModifier)
                    ) {
                    Spacer(Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "App Library",
                            color = Color.White,
                            fontSize = 25.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "Done",
                            color = Cyan,
                            modifier = Modifier
                                .clickable {
                                    search = ""
                                    screen = LauncherScreen.HOME
                                }
                                .padding(8.dp)
                        )
                    }

                    Spacer(Modifier.height(18.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Panel)
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⌕", color = Cyan, fontSize = 22.sp)
                        Spacer(Modifier.size(10.dp))
                        BasicTextField(
                            value = search,
                            onValueChange = { search = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Color.White,
                                fontSize = 15.sp
                            ),
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Search
                            ),
                            decorationBox = { innerTextField ->
                                Box {
                                    if (search.isEmpty()) {
                                        Text(
                                            "Search apps",
                                            color = Muted,
                                            fontSize = 15.sp
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                        if (search.isNotEmpty()) {
                            Text(
                                "×",
                                color = Muted,
                                fontSize = 22.sp,
                                modifier = Modifier.clickable { search = "" }
                            )
                        }
                    }

                    Spacer(Modifier.height(18.dp))
                    Text("${filteredApps.size} apps", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))

                    if (filteredApps.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (apps.isEmpty()) "No launchable apps found"
                                else "No apps match your search",
                                color = Muted
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 72.dp),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            items(filteredApps, key = { it.packageName }) { app ->
                                AppTile(
                                    app = app,
                                    onClick = { launchApp(app.packageName) }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    }
                }

                LauncherScreen.SETTINGS -> {
                    Spacer(Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Settings",
                            color = Color.White,
                            fontSize = 25.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "Done",
                            color = Cyan,
                            modifier = Modifier
                                .clickable { screen = LauncherScreen.HOME }
                                .padding(8.dp)
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    listOf(
                        "Appearance" to "Visual settings are not implemented yet",
                        "Home screen" to "Icon layout and pages",
                        "Dock" to "Dock customization is planned",
                        "Privacy" to "App discovery runs on this device",
                        "About Nivora" to "Version 0.1 · Development build"
                    ).forEach { (title, detail) ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Panel)
                                .padding(16.dp)
                        ) {
                            Text(
                                title,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(5.dp))
                            Text(detail, color = Muted, fontSize = 13.sp)
                        }
                    }

                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}


@Composable
private fun AppTile(
    app: LauncherApp,
    onClick: () -> Unit
) {
    val iconBitmap = remember(app.icon) { app.icon.toImageBitmap() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (iconBitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = iconBitmap,
                contentDescription = app.label,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(13.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Panel),
                contentAlignment = Alignment.Center
            ) {
                Text(app.label.take(1), color = Cyan, fontSize = 22.sp)
            }
        }

        Spacer(Modifier.height(7.dp))

        Text(
            text = app.label,
            color = Color.White,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            maxLines = 2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

private fun Drawable.toImageBitmap(): ImageBitmap? {
    return try {
        val width = intrinsicWidth.coerceIn(1, 192)
        val height = intrinsicHeight.coerceIn(1, 192)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, width, height)
        draw(canvas)
        bitmap.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}
