package com.ianzb.hypernavbar

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.ianzb.hypernavbar.ui.util.isInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Rule
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ianzb.hypernavbar.ui.screen.about.AboutPageContent
import com.ianzb.hypernavbar.ui.screen.home.HomePageView
import com.ianzb.hypernavbar.ui.screen.rules.RulesPageView
import com.ianzb.hypernavbar.ui.screen.settings.SettingsPageView
import com.ianzb.hypernavbar.ui.theme.AppTheme
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import com.ianzb.hypernavbar.ui.component.liquid.IosLiquidGlassNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.FloatingToolbarDefaults
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleHelper.getSavedLanguage(newBase)
        super.attachBaseContext(LocaleHelper.wrapContext(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        val savedSettings = AppSettings.load(this)

        setContent {
            var themeMode by remember {
                mutableStateOf(
                    try { ColorSchemeMode.valueOf(savedSettings.themeMode) }
                    catch (_: Exception) { ColorSchemeMode.System }
                )
            }
            var isFloatingNavbar by remember { mutableStateOf(savedSettings.isFloatingNavbar) }
            var isLiquidGlass by remember { mutableStateOf(savedSettings.isLiquidGlass) }
            var applyIntervalMinutes by remember { mutableIntStateOf(savedSettings.applyIntervalMinutes) }

            var hasRoot by remember { mutableStateOf(false) }
            var rootChecked by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    hasRoot = RootHelper.checkRoot()
                }
                rootChecked = true
            }

            fun persistState() {
                AppSettings.save(
                    this@MainActivity,
                    AppSettings(
                        themeMode = themeMode.name,
                        isFloatingNavbar = isFloatingNavbar,
                        isLiquidGlass = isLiquidGlass,
                        applyIntervalMinutes = applyIntervalMinutes,
                    )
                )
            }

            AppTheme(themeMode = themeMode) {
                MainScreen(
                    hasRoot = hasRoot,
                    rootChecked = rootChecked,
                    themeMode = themeMode,
                    isFloatingNavbar = isFloatingNavbar,
                    isLiquidGlass = isLiquidGlass,
                    applyIntervalMinutes = applyIntervalMinutes,
                    onRetryRootCheck = {
                        scope.launch {
                            rootChecked = false
                            withContext(Dispatchers.IO) {
                                hasRoot = RootHelper.checkRoot()
                            }
                            rootChecked = true
                        }
                    },
                    onThemeModeChange = { themeMode = it; persistState() },
                    onFloatingNavbarChange = { isFloatingNavbar = it; persistState() },
                    onLiquidGlassChange = { isLiquidGlass = it; persistState() },
                    onApplyIntervalChange = { applyIntervalMinutes = it; persistState() },
                )
            }
        }
    }
}

@Composable
private fun MainScreen(
    hasRoot: Boolean,
    rootChecked: Boolean,
    themeMode: ColorSchemeMode,
    isFloatingNavbar: Boolean,
    isLiquidGlass: Boolean,
    applyIntervalMinutes: Int,
    onRetryRootCheck: () -> Unit,
    onThemeModeChange: (ColorSchemeMode) -> Unit,
    onFloatingNavbarChange: (Boolean) -> Unit,
    onLiquidGlassChange: (Boolean) -> Unit,
    onApplyIntervalChange: (Int) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val pagerState = rememberPagerState(pageCount = { 4 })
    var selectedIndex by remember { mutableIntStateOf(0) }
    var isNavigating by remember { mutableStateOf(false) }
    var navJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var homeRefreshKey by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val items = listOf(
        stringResource(R.string.tab_home),
        stringResource(R.string.tab_rules),
        stringResource(R.string.tab_settings),
        stringResource(R.string.tab_about)
    )
    val icons = listOf(
        Icons.Rounded.Home,
        Icons.AutoMirrored.Rounded.Rule,
        Icons.Rounded.Settings,
        Icons.Rounded.Info
    )

    LaunchedEffect(pagerState.currentPage) {
        if (!isNavigating && selectedIndex != pagerState.currentPage) {
            selectedIndex = pagerState.currentPage
            homeRefreshKey++
        }
    }

    val surfaceColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
    val blurActive = true

    val navBarMode = if (!isFloatingNavbar) 0 else if (!isLiquidGlass) 1 else 2

    Scaffold(
        popupHost = { },
        bottomBar = {
            BottomNavigationBar(
                mode = navBarMode,
                items = items,
                icons = icons,
                selectedIndex = selectedIndex,
                backdrop = backdrop,
                blurActive = blurActive,
                onItemSelected = { index ->
                    if (index == selectedIndex) return@BottomNavigationBar
                    homeRefreshKey++
                    navJob?.cancel()
                    selectedIndex = index
                    isNavigating = true
                    navJob = scope.launch {
                        val myJob = coroutineContext.job
                        try {
                            pagerState.scroll(MutatePriority.UserInput) {
                                val distance = abs(index - pagerState.currentPage).coerceAtLeast(2)
                                val duration = 100 * distance + 100
                                val layoutInfo = pagerState.layoutInfo
                                val pageSize = layoutInfo.pageSize + layoutInfo.pageSpacing
                                val currentDistanceInPages = index - pagerState.currentPage - pagerState.currentPageOffsetFraction
                                val scrollPixels = currentDistanceInPages * pageSize
                                var previousValue = 0f
                                animate(
                                    initialValue = 0f,
                                    targetValue = scrollPixels,
                                    animationSpec = tween(easing = EaseInOut, durationMillis = duration),
                                ) { currentValue, _ ->
                                    previousValue += scrollBy(currentValue - previousValue)
                                }
                            }
                            if (pagerState.currentPage != index) {
                                pagerState.scrollToPage(index)
                            }
                        } finally {
                            if (navJob == myJob) {
                                isNavigating = false
                                if (pagerState.currentPage != index) {
                                    selectedIndex = pagerState.currentPage
                                }
                            }
                        }
                    }
                },
            )
        }
    ) { globalPadding ->
        val navBarHeight = globalPadding.calculateBottomPadding()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(Modifier.layerBackdrop(backdrop))
                .background(surfaceColor)
        ) {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (page) {
                     0 -> HomePageView(
                        hasRoot = hasRoot,
                        rootChecked = rootChecked,
                        onRetryRootCheck = onRetryRootCheck,
                        refreshKey = homeRefreshKey,
                        extraBottomPadding = navBarHeight,
                    )
                     1 -> RulesPageView(
                        applyIntervalMinutes = applyIntervalMinutes,
                        extraBottomPadding = navBarHeight,
                    )
                     2 -> SettingsPageView(
                        currentMode = themeMode,
                        onModeChange = onThemeModeChange,
                        isFloatingNavbar = isFloatingNavbar,
                        onFloatingNavbarChange = onFloatingNavbarChange,
                        isLiquidGlass = isLiquidGlass,
                        onLiquidGlassChange = onLiquidGlassChange,
                        applyIntervalMinutes = applyIntervalMinutes,
                        onApplyIntervalChange = onApplyIntervalChange,
                        extraBottomPadding = navBarHeight,
                    )
                    3 -> AboutPageContent(
                        openLicensePage = {
                            context.startActivity(Intent(context, LicenseActivity::class.java))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomNavigationBar(
    mode: Int,
    items: List<String>,
    icons: List<androidx.compose.ui.graphics.vector.ImageVector>,
    selectedIndex: Int,
    backdrop: LayerBackdrop?,
    blurActive: Boolean,
    onItemSelected: (Int) -> Unit,
) {
    when (mode) {
        2 -> {
            val navigationItems = remember(items, icons) {
                List(items.size) { i -> NavigationItem(items[i], icons[i]) }
            }
            IosLiquidGlassNavigationBar(
                modifier = Modifier.padding(horizontal = 12.dp),
                items = navigationItems,
                selectedIndex = selectedIndex,
                onItemClick = { index ->
                    onItemSelected(index)
                },
                backdrop = backdrop,
                isBlurActive = blurActive,
            )
        }
        1 -> {
            val floatingBarColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer
            val floatingBarShape = RoundedCornerShape(FloatingToolbarDefaults.CornerRadius)
            val isDark = isInDarkTheme()
            val floatingHighlight = remember(isDark) {
                if (isDark) Highlight.GlassStrokeMiddleDark else Highlight.GlassStrokeMiddleLight
            }
            FloatingNavigationBar(
                modifier = if (blurActive) {
                    Modifier.textureBlur(
                        backdrop = backdrop!!,
                        shape = floatingBarShape,
                        blurRadius = 25f,
                        colors = BlurDefaults.blurColors(
                            blendColors = listOf(
                                BlendColorEntry(color = MiuixTheme.colorScheme.surfaceContainer.copy(0.4f)),
                            ),
                        ),
                        highlight = floatingHighlight,
                    )
                } else {
                    Modifier
                },
                color = floatingBarColor,
            ) {
                items.forEachIndexed { index, label ->
                    FloatingNavigationBarItem(
                        selected = selectedIndex == index,
                        onClick = { onItemSelected(index) },
                        icon = icons[index],
                        label = label,
                        enabled = true
                    )
                }
            }
        }
        else -> {
            val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface
            Box(
                modifier = Modifier
                    .then(
                        if (blurActive) {
                            Modifier.textureBlur(
                                backdrop = backdrop!!,
                                shape = RectangleShape,
                                blurRadius = 25f,
                                colors = BlurDefaults.blurColors(
                                    blendColors = listOf(
                                        BlendColorEntry(color = MiuixTheme.colorScheme.surface.copy(0.5f)),
                                    ),
                                ),
                            )
                        } else {
                            Modifier
                        }
                    )
                    .background(barColor)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
            ) {
                NavigationBar(
                    color = barColor,
                ) {
                    items.forEachIndexed { index, label ->
                        NavigationBarItem(
                            selected = selectedIndex == index,
                            onClick = { onItemSelected(index) },
                            icon = icons[index],
                            label = label,
                            enabled = true
                        )
                    }
                }
            }
        }
    }
}
