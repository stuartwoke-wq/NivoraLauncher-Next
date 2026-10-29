package org.nivora.launcher

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import org.nivora.launcher.diagnostics.CrashReportStore
import org.nivora.launcher.diagnostics.CrashReportSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Midnight = Color(0xFF10141D)
private val Panel = Color(0xCC252D3B)
private val Cyan = Color(0xFF66D9E8)
private val Lavender = Color(0xFFC8B7FF)
private val Muted = Color(0xFF9AA6B8)

internal data class LauncherApp(
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
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        window.setBackgroundDrawableResource(android.R.color.transparent)
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
        val totalStartedAt = SystemClock.elapsedRealtime()
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        return try {
            val queryStartedAt = SystemClock.elapsedRealtime()
            @Suppress("DEPRECATION")
            val activities = pm.queryIntentActivities(intent, 0)
            val queryMs = SystemClock.elapsedRealtime() - queryStartedAt

            val mappingStartedAt = SystemClock.elapsedRealtime()
            val mappedApps = activities.mapNotNull { resolve ->
                val activityInfo = resolve.activityInfo ?: return@mapNotNull null
                if (activityInfo.packageName == packageName) return@mapNotNull null

                LauncherApp(
                    label = resolve.loadLabel(pm).toString(),
                    packageName = activityInfo.packageName,
                    icon = resolve.loadIcon(pm)
                )
            }
            val mappingMs = SystemClock.elapsedRealtime() - mappingStartedAt

            val processingStartedAt = SystemClock.elapsedRealtime()
            val result = mappedApps
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
            val processingMs = SystemClock.elapsedRealtime() - processingStartedAt
            val totalMs = SystemClock.elapsedRealtime() - totalStartedAt

            android.util.Log.d(
                "NivoraAppLoad",
                "activities=${activities.size}, apps=${result.size}, " +
                    "query=${queryMs}ms, labels_icons=${mappingMs}ms, " +
                    "dedupe_sort=${processingMs}ms, total=${totalMs}ms"
            )

            result
        } catch (exception: Exception) {
            android.util.Log.e("NivoraAppLoad", "App discovery failed", exception)
            throw exception
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
    HOME, LIBRARY, SETTINGS, DOCK, HOME_SETTINGS, PRIVACY, ABOUT,
    DEBUG_DIAGNOSTICS
}

@Composable
private fun NivoraHome(
    loadApps: () -> List<LauncherApp>,
    registerRefresh: ((() -> Unit) -> Unit),
    launchApp: (String) -> Unit
) {
    var apps by remember { mutableStateOf(emptyList<LauncherApp>()) }
    var isAppsLoading by remember { mutableStateOf(true) }
    var appLoadTimeMs by remember { mutableStateOf<Long?>(null) }
    var appLoadCount by remember { mutableStateOf(0) }
    var homeIconConversionNs by remember { mutableStateOf(0L) }
    val measuredHomeIconPackages = remember { mutableSetOf<String>() }
    var search by remember { mutableStateOf("") }
    var screen by remember { mutableStateOf(LauncherScreen.HOME) }
    var diagnosticReports by remember {
        mutableStateOf(emptyList<CrashReportSummary>())
    }
    var diagnosticError by remember { mutableStateOf<String?>(null) }
    var homePage by remember { mutableStateOf(0) }
    var menuExpanded by remember { mutableStateOf(false) }
    val libraryGridState = rememberLazyGridState()

    val context = LocalContext.current

    LaunchedEffect(screen) {
        if (screen == LauncherScreen.DEBUG_DIAGNOSTICS) {
            diagnosticError = null
            try {
                diagnosticReports = withContext(Dispatchers.IO) {
                    CrashReportStore.listReports(context)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                diagnosticError = "Crash reports could not be read."
                diagnosticReports = emptyList()
            }
        }
    }
    val preferences = remember(context) {
        LauncherPreferences(context.applicationContext)
    }
    val isSystemDark = isSystemInDarkTheme()
    val showAppLabels by preferences.showAppLabels.collectAsState(initial = true)
    val iconAppearance by preferences.iconAppearance.collectAsState(
        initial = IconAppearance.SYSTEM_DEFAULT
    )
    val homeGridColumns by preferences.homeGridColumns.collectAsState(initial = 4)
    val homeIconSizeDp by preferences.homeIconSizeDp.collectAsState(initial = 48)
    val savedDockPackages by preferences.dockAppPackages.collectAsState(initial = null)
    val coroutineScope = rememberCoroutineScope()

    var refreshJob by remember { mutableStateOf<Job?>(null) }
    var refreshGeneration by remember { mutableStateOf(0) }
    var appLoadError by remember { mutableStateOf(false) }

    val refresh: () -> Unit = {
        refreshGeneration += 1
        val generation = refreshGeneration

        refreshJob?.cancel()
        if (apps.isEmpty()) isAppsLoading = true
        appLoadError = false

        refreshJob = coroutineScope.launch {
            try {
                val (updatedApps, elapsedMs) = withContext(Dispatchers.IO) {
                    val startedAt = SystemClock.elapsedRealtime()
                    val result = loadApps()
                    result to (SystemClock.elapsedRealtime() - startedAt)
                }

                if (generation == refreshGeneration) {
                    apps = updatedApps
                    appLoadTimeMs = elapsedMs
                    appLoadCount = updatedApps.size
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                android.util.Log.e(
                    "NivoraAppLoad",
                    "App refresh failed; keeping the previous app list",
                    exception
                )
                if (generation == refreshGeneration) {
                    appLoadError = true
                }
            } finally {
                if (generation == refreshGeneration) {
                    isAppsLoading = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        registerRefresh(refresh)
        refresh()
    }

    val filteredApps = remember(apps, search) {
        apps.filter { it.label.contains(search.trim(), ignoreCase = true) }
    }

    val rowsPerPage = 4
    val pageSize = homeGridColumns * rowsPerPage
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

            val startedAtTop =
                libraryGridState.firstVisibleItemIndex == 0 &&
                libraryGridState.firstVisibleItemScrollOffset == 0

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
                    startedAtTop &&
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

    val automaticDockApps = remember(apps) {
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

    val dockApps = remember(apps, savedDockPackages, automaticDockApps) {
        val savedPackages = savedDockPackages
        if (savedPackages == null) {
            automaticDockApps
        } else {
            savedPackages.mapNotNull { packageName ->
                apps.firstOrNull { it.packageName == packageName }
            }.take(4)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = if (screen == LauncherScreen.HOME) Color.Transparent else Midnight
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
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Box {
                            Text(
                                "⋮",
                                color = Color.White,
                                fontSize = 25.sp,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { menuExpanded = true }
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
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

                    Spacer(Modifier.height(8.dp))

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
                                if (isAppsLoading) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            color = Cyan,
                                            modifier = Modifier.size(28.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Text("Loading apps…", color = Muted)
                                    }
                                } else {
                                    if (appLoadError) {
                                        Text(
                                            "Couldn't load apps. Tap to retry.",
                                            color = Muted,
                                            modifier = Modifier.clickable { refresh() }
                                        )
                                    } else {
                                        Text(
                                            if (search.isBlank()) "No launchable apps found"
                                            else "No apps match your search",
                                            color = Muted
                                        )
                                    }
                                }
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
                                    columns = GridCells.Fixed(homeGridColumns),
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(20.dp)
                                ) {
                                    items(animatedApps, key = { it.packageName }) { app ->
                                        AppTile(
                                            app = app,
                                            onClick = { launchApp(app.packageName) },
                                            showLabel = showAppLabels,
                                            iconSize = homeIconSizeDp.dp,
                                            iconAppearance = iconAppearance,
                                            onIconConversionMeasured = { packageName, elapsedNs ->
                                                if (measuredHomeIconPackages.add(packageName)) {
                                                    homeIconConversionNs += elapsedNs
                                                }
                                            }
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
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f))
                                .clickable {
                                    search = ""
                                    screen = LauncherScreen.LIBRARY
                                }
                                .padding(horizontal = 18.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⌕", color = Color.White, fontSize = 18.sp)
                            Spacer(Modifier.size(7.dp))
                            Text("Search", color = Color.White, fontSize = 13.sp)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .background(Color.White.copy(alpha = 0.13f))
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
                                            .clip(RoundedCornerShape(14.dp)),
                                        colorFilter = iconAppearance.colorFilter(isSystemDark)
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
                            if (isAppsLoading) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = Cyan,
                                        modifier = Modifier.size(28.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Text("Loading apps…", color = Muted)
                                }
                            } else {
                                if (appLoadError) {
                                    Text(
                                        "Couldn't load apps. Tap to retry.",
                                        color = Muted,
                                        modifier = Modifier.clickable { refresh() }
                                    )
                                } else {
                                    Text(
                                        if (apps.isEmpty()) "No launchable apps found"
                                        else "No apps match your search",
                                        color = Muted
                                    )
                                }
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 72.dp),
                            state = libraryGridState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            items(filteredApps, key = { it.packageName }) { app ->
                                AppTile(
                                    app = app,
                                    onClick = { launchApp(app.packageName) },
                                    showLabel = showAppLabels,
                                    iconAppearance = iconAppearance
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    }
                }

                LauncherScreen.DOCK -> {
                    var editingDockPackages by remember(savedDockPackages, apps) {
                        mutableStateOf(
                            (savedDockPackages
                                ?: automaticDockApps.map { it.packageName })
                                .filter { packageName ->
                                    apps.any { it.packageName == packageName }
                                }
                                .take(4)
                        )
                    }
                    var useAutomaticDock by remember(savedDockPackages, apps) {
                        mutableStateOf(savedDockPackages == null)
                    }

                    Spacer(Modifier.height(18.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Customize Dock",
                            color = Color.White,
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "Back",
                            color = Cyan,
                            modifier = Modifier
                                .clickable { screen = LauncherScreen.SETTINGS }
                                .padding(8.dp)
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Choose up to four apps. Selection order determines dock order.",
                        color = Muted,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${editingDockPackages.size} of 4 selected",
                        color = Cyan,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(8.dp))

                    Text(
                        "Dock order · first app appears on the left",
                        color = Muted,
                        fontSize = 12.sp
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Panel)
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        editingDockPackages.forEach { packageName ->
                            val previewApp = apps.firstOrNull {
                                it.packageName == packageName
                            }
                            if (previewApp != null) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    val previewBitmap = remember(previewApp.icon) {
                                        previewApp.icon.toImageBitmap()
                                    }
                                    if (previewBitmap != null) {
                                        androidx.compose.foundation.Image(
                                            bitmap = previewBitmap,
                                            contentDescription = previewApp.label,
                                            modifier = Modifier.size(32.dp),
                                            colorFilter = iconAppearance.colorFilter(isSystemDark)
                                        )
                                    } else {
                                        Text(
                                            previewApp.label.take(1),
                                            color = Cyan,
                                            fontSize = 20.sp
                                        )
                                    }
                                    Text(
                                        previewApp.label,
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    editingDockPackages.forEachIndexed { index, packageName ->
                        val selectedApp = apps.firstOrNull {
                            it.packageName == packageName
                        }
                        if (selectedApp != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Panel)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${index + 1}. ${selectedApp.label}",
                                    color = Color.White,
                                    modifier = Modifier.weight(1f),
                                    fontSize = 13.sp
                                )
                                Text(
                                    "←",
                                    color = if (index > 0) Cyan else Muted,
                                    modifier = Modifier
                                        .clickable {
                                            if (index > 0) {
                                                useAutomaticDock = false
                                                editingDockPackages =
                                                    editingDockPackages.toMutableList().apply {
                                                        add(index - 1, removeAt(index))
                                                    }
                                            }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                                Text(
                                    "→",
                                    color = if (index < editingDockPackages.lastIndex) Cyan else Muted,
                                    modifier = Modifier
                                        .clickable {
                                            if (index < editingDockPackages.lastIndex) {
                                                useAutomaticDock = false
                                                editingDockPackages =
                                                    editingDockPackages.toMutableList().apply {
                                                        add(index + 1, removeAt(index))
                                                    }
                                            }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                                Text(
                                    "×",
                                    color = Lavender,
                                    modifier = Modifier
                                        .clickable {
                                            useAutomaticDock = false
                                            editingDockPackages =
                                                editingDockPackages.filterNot {
                                                    it == packageName
                                                }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Text(
                        if (useAutomaticDock) {
                            "Automatic dock is enabled"
                        } else {
                            "Restore automatic selection"
                        },
                        color = Lavender,
                        modifier = Modifier
                            .clickable {
                                useAutomaticDock = true
                                editingDockPackages =
                                    automaticDockApps.map { it.packageName }.take(4)
                            }
                            .padding(vertical = 8.dp)
                    )

                    Spacer(Modifier.height(8.dp))

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(apps, key = { it.packageName }) { app ->
                            val selectedIndex =
                                editingDockPackages.indexOf(app.packageName)

                            Column(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (selectedIndex >= 0) Panel
                                        else Color.Transparent
                                    )
                                    .padding(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AppTile(
                                    app = app,
                                    showLabel = true,
                                    iconAppearance = iconAppearance,
                                    onClick = {
                                        if (selectedIndex >= 0) {
                                            useAutomaticDock = false
                                            editingDockPackages =
                                                editingDockPackages.filterNot {
                                                    it == app.packageName
                                                }
                                        } else if (editingDockPackages.size < 4) {
                                            useAutomaticDock = false
                                            editingDockPackages =
                                                editingDockPackages + app.packageName
                                        }
                                    }
                                )

                                Text(
                                    text = if (selectedIndex >= 0) {
                                        "Selected #${selectedIndex + 1}"
                                    } else if (editingDockPackages.size >= 4) {
                                        "Limit reached"
                                    } else {
                                        "Tap to add"
                                    },
                                    color = if (selectedIndex >= 0) Cyan else Muted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        "Save dock",
                        color = Cyan,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Panel)
                            .clickable {
                                val chosenPackages = editingDockPackages.toList()
                                val saveAutomatic = useAutomaticDock
                                coroutineScope.launch {
                                    if (saveAutomatic) {
                                        preferences.resetDockAppPackagesToAutomatic()
                                    } else {
                                        preferences.setDockAppPackages(chosenPackages)
                                    }
                                    screen = LauncherScreen.SETTINGS
                                }
                            }
                            .padding(16.dp)
                    )

                    Spacer(Modifier.height(12.dp))
                }

                LauncherScreen.HOME_SETTINGS -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(top = 18.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Home screen",
                                color = Color.White,
                                fontSize = 25.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "Back",
                                color = Cyan,
                                modifier = Modifier
                                    .clickable { screen = LauncherScreen.SETTINGS }
                                    .padding(8.dp)
                            )
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Panel)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Icon appearance",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Choose an icon treatment. Clear is a subtle translucency effect, not a full Liquid Glass recreation yet.",
                                color = Muted,
                                fontSize = 13.sp
                            )
                            IconAppearance.values().toList().chunked(2).forEach { rowOptions ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowOptions.forEach { appearance ->
                                        LayoutOption(
                                            label = appearance.title,
                                            selected = iconAppearance == appearance,
                                            onClick = {
                                                coroutineScope.launch {
                                                    preferences.setIconAppearance(appearance)
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (rowOptions.size == 1) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Panel)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Grid columns",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Choose how many app icons appear in each row.",
                                color = Muted,
                                fontSize = 13.sp
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(3, 4, 5, 6).forEach { columns ->
                                    LayoutOption(
                                        label = columns.toString(),
                                        selected = homeGridColumns == columns,
                                        onClick = {
                                            coroutineScope.launch {
                                                preferences.setHomeGridColumns(columns)
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Panel)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "App icon size",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Select the icon size used on your home screen.",
                                color = Muted,
                                fontSize = 13.sp
                            )

                            val iconSizes = listOf(40, 44, 48, 52, 56, 60, 64)
                            iconSizes.chunked(4).forEach { sizeRow ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    sizeRow.forEach { sizeDp ->
                                        LayoutOption(
                                            label = "${sizeDp} dp",
                                            selected = homeIconSizeDp == sizeDp,
                                            onClick = {
                                                coroutineScope.launch {
                                                    preferences.setHomeIconSizeDp(sizeDp)
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    repeat(4 - sizeRow.size) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        Text(
                            text = "Changes are saved on this device and apply to the home screen. The app library grid is unchanged.",
                            color = Muted,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }

                LauncherScreen.PRIVACY -> {
                    SettingsInfoPage(
                        title = "Privacy",
                        description = "Nivora discovers launchable apps through Android's package manager and stores launcher preferences using on-device DataStore. This page is informational; a complete privacy dashboard and detailed data controls are not implemented yet.",
                        onBack = { screen = LauncherScreen.SETTINGS }
                    )
                }

                LauncherScreen.ABOUT -> {
                    SettingsInfoPage(
                        title = "About Nivora",
                        description = "Nivora Launcher\nVersion 0.1.0\nDevelopment build\n\nThis project is under active development. Some planned features are not available yet.",
                        onBack = { screen = LauncherScreen.SETTINGS }
                    )
                }

                LauncherScreen.DEBUG_DIAGNOSTICS -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(top = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Debug & Diagnostics",
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "Back",
                                color = Cyan,
                                modifier = Modifier
                                    .clickable {
                                        screen = LauncherScreen.SETTINGS
                                    }
                                    .padding(8.dp)
                            )
                        }

                        Text(
                            "Crash reports are stored locally in Nivora's private app storage. " +
                                "This screen only lists report metadata.",
                            color = Muted,
                            fontSize = 13.sp
                        )

                        if (diagnosticError != null) {
                            Text(
                                diagnosticError.orEmpty(),
                                color = Muted,
                                fontSize = 14.sp
                            )
                        } else if (diagnosticReports.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Panel)
                                    .padding(16.dp)
                            ) {
                                Text(
                                    "No crash reports",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    "If Nivora saves a crash report in the future, it will appear here.",
                                    color = Muted,
                                    fontSize = 13.sp
                                )
                            }
                        } else {
                            Text(
                                "${diagnosticReports.size} report(s)",
                                color = Cyan,
                                fontSize = 14.sp
                            )

                            diagnosticReports.forEach { report ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Panel)
                                        .padding(14.dp)
                                ) {
                                    Text(
                                        report.fileName,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Modified: ${java.util.Date(report.lastModified)}",
                                        color = Muted,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        "Size: ${report.sizeBytes} bytes",
                                        color = Muted,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                LauncherScreen.SETTINGS -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
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

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Panel)
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Show app labels",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    "Show or hide names under app icons",
                                    color = Muted,
                                    fontSize = 13.sp
                                )
                            }
                            Switch(
                                checked = showAppLabels,
                                onCheckedChange = { enabled ->
                                    coroutineScope.launch {
                                        preferences.setShowAppLabels(enabled)
                                    }
                                }
                            )
                        }
                    }

                    listOf(
                        "Home screen" to "Icon layout and pages",
                        "Dock" to "Choose up to four apps for your dock",
                        "Privacy" to "App discovery runs on this device",
                        "Debug & Diagnostics" to "View locally stored crash reports",
                        "About Nivora" to "Version 0.1 · Development build"
                    ).forEach { (title, detail) ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Panel)
                                .clickable {
                                    screen = when (title) {
                                        "Dock" -> LauncherScreen.DOCK
                                        "Home screen" -> LauncherScreen.HOME_SETTINGS
                                        "Privacy" -> LauncherScreen.PRIVACY
                                        "Debug & Diagnostics" -> LauncherScreen.DEBUG_DIAGNOSTICS
                                        else -> LauncherScreen.ABOUT
                                    }
                                }
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

                }
                }
            }
        }
    }
}

@Composable
private fun LayoutOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Cyan.copy(alpha = 0.20f) else Midnight)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Cyan else Color.White,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun SettingsInfoPage(
    title: String,
    description: String,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 25.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "Back",
                color = Cyan,
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(8.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Panel)
                .padding(16.dp)
        ) {
            Text(
                text = description,
                color = Muted,
                fontSize = 14.sp,
                lineHeight = 21.sp
            )
        }
    }
}
