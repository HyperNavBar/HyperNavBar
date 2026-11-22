package cn.ianzb.hypernavbar

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.computedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import cn.ianzb.hypernavbar.rules.RulesManager
import cn.ianzb.hypernavbar.ui.component.LocalAppTopBarCenterObstructionStart
import cn.ianzb.hypernavbar.ui.component.LocalAppTopBarCompact
import cn.ianzb.hypernavbar.ui.component.LocalAppTopBarTitleAlpha
import cn.ianzb.hypernavbar.ui.component.UpdateDialog
import cn.ianzb.hypernavbar.ui.component.liquid.IosLiquidGlassNavigationBar
import cn.ianzb.hypernavbar.ui.component.search.AppSearchOverlay
import cn.ianzb.hypernavbar.ui.component.search.AppSearchState
import cn.ianzb.hypernavbar.ui.component.search.LocalAppSearchState
import cn.ianzb.hypernavbar.ui.screen.about.AboutPageContent
import cn.ianzb.hypernavbar.ui.screen.features.FeaturesPageView
import cn.ianzb.hypernavbar.ui.screen.home.HomePageView
import cn.ianzb.hypernavbar.ui.screen.rules.RulesPageView
import cn.ianzb.hypernavbar.ui.screen.settings.SettingsPageView
import cn.ianzb.hypernavbar.ui.screen.subpage.AppPageRoute
import cn.ianzb.hypernavbar.ui.screen.subpage.AppSubPage
import cn.ianzb.hypernavbar.ui.screen.subpage.AppSubPageContent
import cn.ianzb.hypernavbar.ui.screen.subpage.LocalOpenAppSubPage
import cn.ianzb.hypernavbar.ui.screen.subpage.rememberAppPageBackStack
import cn.ianzb.hypernavbar.ui.screen.subpage.rememberOpenAppSubPage
import cn.ianzb.hypernavbar.ui.theme.AppTheme
import cn.ianzb.hypernavbar.ui.util.LocalAppContainerSize
import cn.ianzb.hypernavbar.ui.util.LocalAppPageStartInset
import cn.ianzb.hypernavbar.ui.util.LocalPreferenceBackdrop
import cn.ianzb.hypernavbar.ui.util.applyWindowBackground
import cn.ianzb.hypernavbar.ui.util.pagerSwipeOverride
import cn.ianzb.hypernavbar.ui.util.rememberBlurBackdrop
import cn.ianzb.hypernavbar.ui.util.shouldShowSplitPane
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassNavigationBar
import top.yukonga.miuix.kmp.glass.GlassNavigationBarDefaults
import top.yukonga.miuix.kmp.glass.GlassNavigationItem
import top.yukonga.miuix.kmp.glass.GlassNavigationRail
import top.yukonga.miuix.kmp.glass.GlassNavigationRailDefaults
import top.yukonga.miuix.kmp.glass.rememberGlassNavigationRailState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.HomeOutline
import top.yukonga.miuix.kmp.icon.glass.Info
import top.yukonga.miuix.kmp.icon.glass.ListView
import top.yukonga.miuix.kmp.icon.glass.Search
import top.yukonga.miuix.kmp.icon.glass.SettingsOutline
import top.yukonga.miuix.kmp.icon.glass.Tune
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.transition.NavMotion
import top.yukonga.miuix.kmp.nav.transition.NavSettleSpec
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.nav.transition.navGraphicsTransition
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PagerGestureNestedScrollConnection
import top.yukonga.miuix.kmp.utils.springAnimateToPage

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

        // 首次启动时播种默认订阅（社区规则源在上，官方规则源在下）
        RulesManager.ensureDefaultConfigs(this)

        // 同步桌面图标可见状态，避免偏好与系统组件状态不一致。
        LauncherIconController.apply(this, savedSettings.hideLauncherIcon)

        // 覆盖 XML 主题的窗口背景，兼容「系统浅色但应用内手动强制深色」的情况，避免启动白屏闪烁。
        applyWindowBackground(savedSettings.themeMode)

        setContent {
            var themeMode by remember {
                mutableStateOf(
                    try { ColorSchemeMode.valueOf(savedSettings.themeMode) }
                    catch (_: Exception) { ColorSchemeMode.System }
                )
            }
            var bottomBarStyle by remember { mutableStateOf(savedSettings.bottomBarStyle) }
            var railStyle by remember { mutableStateOf(savedSettings.railStyle) }
            var isBlurEnabled by remember { mutableStateOf(savedSettings.isBlurEnabled) }
            var applyIntervalMinutes by remember { mutableIntStateOf(savedSettings.applyIntervalMinutes) }
            var autoApplyAfterEdit by remember { mutableStateOf(savedSettings.autoApplyAfterEdit) }
            var checkUpdateOnLaunch by remember { mutableStateOf(savedSettings.checkUpdateOnLaunch) }
            var hideLauncherIcon by remember { mutableStateOf(savedSettings.hideLauncherIcon) }

            var updateInfo by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
            var isCheckingUpdate by remember { mutableStateOf(false) }
            val uriHandler = LocalUriHandler.current

            var hasRoot by remember { mutableStateOf(false) }
            var rootChecked by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    hasRoot = RootHelper.checkRoot()
                }
                rootChecked = true
            }

            LaunchedEffect(Unit) {
                if (checkUpdateOnLaunch) {
                    UpdateChecker.checkForUpdate(this@MainActivity)
                        .onSuccess { info -> if (info.hasUpdate) updateInfo = info }
                }
            }

            fun checkUpdate() {
                if (isCheckingUpdate) return
                isCheckingUpdate = true
                scope.launch {
                    val result = UpdateChecker.checkForUpdate(this@MainActivity)
                    isCheckingUpdate = false
                    result.fold(
                        onSuccess = { info ->
                            if (info.hasUpdate) {
                                updateInfo = info
                            } else {
                                Toast.makeText(
                                    this@MainActivity,
                                    getString(R.string.update_latest),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onFailure = {
                            Toast.makeText(
                                this@MainActivity,
                                getString(R.string.update_check_failed),
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                    )
                }
            }

            fun persistState() {
                AppSettings.save(
                    this@MainActivity,
                    AppSettings.load(this@MainActivity).copy(
                        themeMode = themeMode.name,
                        bottomBarStyle = bottomBarStyle,
                        railStyle = railStyle,
                        isBlurEnabled = isBlurEnabled,
                        applyIntervalMinutes = applyIntervalMinutes,
                        autoApplyAfterEdit = autoApplyAfterEdit,
                        checkUpdateOnLaunch = checkUpdateOnLaunch,
                        hideLauncherIcon = hideLauncherIcon,
                    )
                )
            }

            AppTheme(themeMode = themeMode) {
                val backdrop = if (isBlurEnabled) rememberBlurBackdrop() else null
                // MainScreen records this layer for navigation and root popup surfaces.
                CompositionLocalProvider(LocalPreferenceBackdrop provides backdrop) {
                    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0)) {
                        BoxWithConstraints(Modifier.fillMaxSize()) {
                            val size = IntSize(constraints.maxWidth, constraints.maxHeight)
                            CompositionLocalProvider(LocalAppContainerSize provides size) {
                                MainScreen(
                                    backdrop = backdrop,
                                    hasRoot = hasRoot,
                                    rootChecked = rootChecked,
                                    themeMode = themeMode,
                                    bottomBarStyle = bottomBarStyle,
                                    railStyle = railStyle,
                                    isBlurEnabled = isBlurEnabled,
                                    applyIntervalMinutes = applyIntervalMinutes,
                                    autoApplyAfterEdit = autoApplyAfterEdit,
                                    checkUpdateOnLaunch = checkUpdateOnLaunch,
                                    hideLauncherIcon = hideLauncherIcon,
                                    isCheckingUpdate = isCheckingUpdate,
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
                                    onBottomBarChange = { bottomBarStyle = it; persistState() },
                                    onRailStyleChange = { railStyle = it; persistState() },
                                    onBlurEnabledChange = { isBlurEnabled = it; persistState() },
                                    onApplyIntervalChange = { applyIntervalMinutes = it; persistState() },
                                    onAutoApplyAfterEditChange = { autoApplyAfterEdit = it; persistState() },
                                    onCheckUpdateOnLaunchChange = { checkUpdateOnLaunch = it; persistState() },
                                    onHideLauncherIconChange = {
                                        hideLauncherIcon = it
                                        LauncherIconController.apply(this@MainActivity, it)
                                        persistState()
                                    },
                                    onCheckUpdate = { checkUpdate() },
                                )

                                updateInfo?.let { info ->
                                    UpdateDialog(
                                        info = info,
                                        onDismissRequest = { updateInfo = null },
                                        onConfirm = {
                                            updateInfo = null
                                            runCatching { uriHandler.openUri(info.releaseUrl) }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MainScreen(
    backdrop: LayerBackdrop?,
    hasRoot: Boolean,
    rootChecked: Boolean,
    themeMode: ColorSchemeMode,
    bottomBarStyle: BottomBarStyle,
    railStyle: RailStyle,
    isBlurEnabled: Boolean,
    applyIntervalMinutes: Int,
    autoApplyAfterEdit: Boolean,
    checkUpdateOnLaunch: Boolean,
    hideLauncherIcon: Boolean,
    isCheckingUpdate: Boolean,
    onRetryRootCheck: () -> Unit,
    onThemeModeChange: (ColorSchemeMode) -> Unit,
    onBottomBarChange: (BottomBarStyle) -> Unit,
    onRailStyleChange: (RailStyle) -> Unit,
    onBlurEnabledChange: (Boolean) -> Unit,
    onApplyIntervalChange: (Int) -> Unit,
    onAutoApplyAfterEditChange: (Boolean) -> Unit,
    onCheckUpdateOnLaunchChange: (Boolean) -> Unit,
    onHideLauncherIconChange: (Boolean) -> Unit,
    onCheckUpdate: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { 5 })
    var selectedIndex by rememberSaveable { mutableIntStateOf(pagerState.currentPage) }
    val backStack = rememberAppPageBackStack()
    val isSubPage = backStack.size > 1
    val closeSubPage: () -> Unit = { if (backStack.size > 1) backStack.removeLastOrNull() }
    val openSubPage: (AppSubPage) -> Unit = remember(backStack) {
        { page ->
            if (backStack.size == 1) backStack.add(AppPageRoute.SubPage(page))
        }
    }
    var isNavigating by remember { mutableStateOf(false) }
    var navJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var homeRefreshKey by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val items = listOf(
        stringResource(R.string.tab_home),
        stringResource(R.string.tab_rules),
        stringResource(R.string.tab_features),
        stringResource(R.string.tab_settings),
        stringResource(R.string.tab_about)
    )
    val icons = listOf(
        MiuixIcons.Glass.HomeOutline,
        MiuixIcons.Glass.ListView,
        MiuixIcons.Glass.Tune,
        MiuixIcons.Glass.SettingsOutline,
        MiuixIcons.Glass.Info
    )

    LaunchedEffect(pagerState.currentPage) {
        if (!isNavigating && selectedIndex != pagerState.currentPage) {
            selectedIndex = pagerState.currentPage
            homeRefreshKey++
        }
    }

    val surfaceColor = MiuixTheme.colorScheme.surface
    // 全局搜索：状态挂在导航容器上，底栏搜索按钮与各主页面顶栏搜索按钮共用同一份状态。
    val searchState = remember { AppSearchState() }
    LaunchedEffect(selectedIndex) { if (selectedIndex != 2) searchState.close() }
    val blurActive = backdrop != null

    val isWideScreen = shouldShowSplitPane()
    // 宽屏且用户未关闭侧边导航栏时才走官方 GlassNavigationRail；
    // 关闭侧栏（或窄屏）时退回底部悬浮栏，保持完整的四页导航。
    val useRail = isWideScreen && railStyle != RailStyle.Off
    // 侧栏形态由设置决定：「左面」进入即展开成左侧面板，「上面」进入停在顶部折叠胶囊。
    val expandSidebar = railStyle == RailStyle.Left
    val sidebarState = rememberGlassNavigationRailState(initialExpanded = expandSidebar)
    LaunchedEffect(expandSidebar) {
        sidebarState.expanded = expandSidebar
    }
    val sidebarWidth = GlassNavigationRailDefaults.Margin + GlassNavigationRailDefaults.ExpandedWidth
    // 进度由页面的测量和绘制阶段读取，避免形变时逐帧重新组合整个导航宿主。
    val sidebarInset: () -> Dp = remember(useRail, sidebarState, sidebarWidth) {
        { if (useRail) sidebarWidth * sidebarState.progress else 0.dp }
    }
    val searchTopPadding: () -> Dp = remember(useRail, sidebarState) {
        {
            if (useRail) {
                (GlassNavigationRailDefaults.CollapsedTop + GlassNavigationRailDefaults.CollapsedHeight + 4.dp) *
                    (1f - sidebarState.progress)
            } else {
                0.dp
            }
        }
    }
    val railItems = items.mapIndexed { index, label ->
        GlassNavigationItem(icon = icons[index], label = label)
    }

    var skipRouteTransition by remember { mutableStateOf(false) }
    LaunchedEffect(skipRouteTransition) {
        if (skipRouteTransition) {
            delay(100)
            skipRouteTransition = false
        }
    }
    val onItemSelected: (Int) -> Unit = select@{ index ->
        searchState.close()
        if (useRail && isSubPage) skipRouteTransition = true
        closeSubPage()
        if (index == selectedIndex) return@select
        homeRefreshKey++
        navJob?.cancel()
        selectedIndex = index
        isNavigating = true
        navJob = scope.launch {
            val myJob = coroutineContext.job
            try {
                pagerState.springAnimateToPage(index)
            } finally {
                if (navJob == myJob) {
                    isNavigating = false
                    if (pagerState.currentPage != index) {
                        selectedIndex = pagerState.currentPage
                    }
                }
            }
        }
    }

    val pagerContent: @Composable (Dp) -> Unit = { navBarHeight ->
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier
                .fillMaxSize()
                // Cross-Axis 拦截 + 子组件（滑条）手势放行：见 PagerSwipeOverride.kt
                .pagerSwipeOverride(pagerState = pagerState),
            userScrollEnabled = false,
            pageNestedScrollConnection = PagerGestureNestedScrollConnection,
        ) { page ->
            val pageProgress: () -> Float = remember(useRail, sidebarState, pagerState, page) {
                val hidden = computedStateOf {
                    abs(page - pagerState.currentPage - pagerState.currentPageOffsetFraction) >= 1f
                }
                var lastVisibleProgress: Float? = null
                val progress: () -> Float = {
                    if (useRail) {
                        // 预组合的相邻页只在进入视口后继续跟随形变。
                        val frozen = if (hidden.value) lastVisibleProgress else null
                        frozen ?: sidebarState.progress.also { lastVisibleProgress = it }
                    } else {
                        1f
                    }
                }
                progress
            }
            val pageInset: () -> Dp = remember(useRail, sidebarWidth, pageProgress) {
                { if (useRail) sidebarWidth * pageProgress() else 0.dp }
            }
            CompositionLocalProvider(
                LocalAppPageStartInset provides pageInset,
                LocalAppTopBarTitleAlpha provides pageProgress,
            ) {
                when (page) {
                    0 -> HomePageView(
                        hasRoot = hasRoot,
                        rootChecked = rootChecked,
                        onRetryRootCheck = onRetryRootCheck,
                        isBlurEnabled = isBlurEnabled,
                        refreshKey = homeRefreshKey,
                        extraBottomPadding = navBarHeight,
                    )
                    1 -> RulesPageView(
                        applyIntervalMinutes = applyIntervalMinutes,
                        autoApplyAfterEdit = autoApplyAfterEdit,
                        isBlurEnabled = isBlurEnabled,
                        extraBottomPadding = navBarHeight,
                    )
                    2 -> FeaturesPageView(
                        isBlurEnabled = isBlurEnabled,
                        extraBottomPadding = navBarHeight,
                    )
                    3 -> SettingsPageView(
                        currentMode = themeMode,
                        onModeChange = onThemeModeChange,
                        bottomBarStyle = bottomBarStyle,
                        railStyle = railStyle,
                        onBottomBarChange = onBottomBarChange,
                        onRailStyleChange = onRailStyleChange,
                        isBlurEnabled = isBlurEnabled,
                        onBlurEnabledChange = onBlurEnabledChange,
                        applyIntervalMinutes = applyIntervalMinutes,
                        onApplyIntervalChange = onApplyIntervalChange,
                        autoApplyAfterEdit = autoApplyAfterEdit,
                        onAutoApplyAfterEditChange = onAutoApplyAfterEditChange,
                        checkUpdateOnLaunch = checkUpdateOnLaunch,
                        onCheckUpdateOnLaunchChange = onCheckUpdateOnLaunchChange,
                        hideLauncherIcon = hideLauncherIcon,
                        onHideLauncherIconChange = onHideLauncherIconChange,
                        onCheckUpdate = onCheckUpdate,
                        isCheckingUpdate = isCheckingUpdate,
                        extraBottomPadding = navBarHeight,
                    )
                    4 -> AboutPageContent(
                        openLicensePage = rememberOpenAppSubPage(AppSubPage.Licenses),
                        isBlurEnabled = isBlurEnabled,
                        extraBottomPadding = navBarHeight,
                    )
                }
            }
        }
    }

    val railTitleObstruction: () -> Dp = remember(useRail, sidebarState) {
        {
            if (useRail && !sidebarState.expanded && sidebarState.collapsedWidth > 0.dp) {
                sidebarState.collapsedStart
            } else {
                Dp.Unspecified
            }
        }
    }
    CompositionLocalProvider(
        LocalAppSearchState provides searchState,
        LocalOpenAppSubPage provides openSubPage,
        LocalAppPageStartInset provides sidebarInset,
        LocalAppTopBarCompact provides useRail,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 宽屏把搜索一起录入侧栏 backdrop，侧栏本身仍在采样层外。
            Box(
                modifier = Modifier.fillMaxSize()
                    .then(if (useRail && backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier),
            ) {
                Scaffold(
                    bottomBar = {
                        if (!useRail && !isSubPage) {
                            BottomNavigationBar(
                                style = bottomBarStyle,
                                items = items,
                                icons = icons,
                                selectedIndex = selectedIndex,
                                backdrop = backdrop,
                                blurActive = blurActive,
                                onItemSelected = onItemSelected,
                                onSearchClick = if (selectedIndex == 2) searchState::open else null,
                            )
                        }
                    },
                ) { globalPadding ->
                    val navigationBarHeight = if (useRail) {
                        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    } else {
                        globalPadding.calculateBottomPadding()
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (!useRail && backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                            .background(surfaceColor),
                    ) {
                        NavDisplay(
                            backStack = backStack,
                            onBack = closeSubPage,
                            transition = if (skipRouteTransition) InstantNavTransition else NavTransitions.MiuixDefault,
                            effects = if (useRail) NavDisplayEffects(cornerClipRadius = 0.dp) else NavDisplayEffects(),
                        ) {
                            entry<AppPageRoute.Main> {
                                pagerContent(navigationBarHeight)
                            }
                            entry<AppPageRoute.SubPage> { route ->
                                CompositionLocalProvider(
                                    LocalAppTopBarCenterObstructionStart provides railTitleObstruction,
                                ) {
                                    AppSubPageContent(route.page, isBlurEnabled, closeSubPage)
                                }
                            }
                        }
                    }
                }
                AppSearchOverlay(
                    expanded = searchState.expanded,
                    onDismissRequest = searchState::close,
                    label = stringResource(R.string.search_hint),
                    blurEnabled = blurActive,
                    searchTopPadding = searchTopPadding,
                    scrollOffset = { searchState.scrollOffset() },
                ) { query ->
                    searchState.results?.invoke(this, query, searchTopPadding)
                }
            }
            if (useRail) {
                GlassNavigationRail(
                    items = railItems,
                    selectedIndex = if (isSubPage) -1 else selectedIndex,
                    onSelect = onItemSelected,
                    backdrop = backdrop,
                    state = sidebarState,
                )
            }
        }
    }
}

private val InstantNavTransition = navGraphicsTransition(
    motion = NavMotion(programmatic = NavSettleSpec.Tween(durationMillis = 0, easing = LinearEasing)),
) {}

/** 玻璃底栏收敛后的胶囊宽度（miuix 示例 4 个 tab 的实测值，本项目同为 4 个 tab）。 */
private val NavigationBarCapsuleMaxWidth = 344.dp

/** 胶囊可用宽度超过该值就收敛，否则铺满（示例阈值）。 */
private val NavigationBarCapsuleMaxAvailable = 400.dp

/** 每个 tab 在示例里额外计入可用宽度的间距。 */
private val NavigationBarItemSpacing = 10.dp

/** 示例里从 tab 间距中扣掉的固定量。 */
private val NavigationBarSpacingPadding = 6.dp

/**
 * 底部导航栏。
 *
 * @param onSearchClick 非空时在玻璃底栏右侧渲染独立的搜索按钮（全局搜索入口之一；另一个入口是
 *   各主页面的顶栏搜索按钮），点击唤起搜索界面。
 */
@Composable
private fun BottomNavigationBar(
    style: BottomBarStyle,
    items: List<String>,
    icons: List<ImageVector>,
    selectedIndex: Int,
    backdrop: LayerBackdrop?,
    blurActive: Boolean,
    onItemSelected: (Int) -> Unit,
    onSearchClick: (() -> Unit)? = null,
) {
    when (style) {
        // HyperOS 玻璃底栏：官方 miuix-glass 组件（与 miuix example 的 AppContent 一致）。
        // 高度、图标/文字尺寸、指示器内缩、边缘高光、模糊与折射全部由 GlassNavigationBar 负责，
        // 这里只保留 demo 的左右 24.dp 边距、宽度收敛逻辑、搜索按钮与「吞掉空白点按」的手势壳。
        BottomBarStyle.HyperOS -> {
            val glassItems = items.mapIndexed { index, label ->
                GlassNavigationItem(icon = icons[index], label = label)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // 吞掉落在底栏空白处的点按（含两侧留白），避免穿透到底层 Pager。
                    .pointerInput(Unit) {
                        detectTapGestures { }
                    },
                contentAlignment = Alignment.Center,
            ) {
                GlassNavigationBar(
                    items = glassItems,
                    selectedIndex = selectedIndex,
                    onSelect = onItemSelected,
                    backdrop = backdrop,
                    // 搜索按钮是胶囊之外独立的一颗玻璃圆钮（与 demo 的 trailingAction 一致）。
                    trailingAction = onSearchClick?.let { openSearch ->
                        {
                            GlassIconButton(
                                onClick = openSearch,
                                backdrop = backdrop,
                                size = GlassNavigationBarDefaults.Height,
                                material = GlassNavigationBarDefaults.material(),
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Glass.Search,
                                    contentDescription = stringResource(R.string.search_hint),
                                    modifier = Modifier.size(28.dp),
                                    tint = MiuixTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .glassNavigationBarWidth(items.size),
                )
            }
        }

        // iOS 液态玻璃底栏：移植自 miuix example 的 component/liquid（见 ui/component/liquid/）。
        BottomBarStyle.IOS -> {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                IosLiquidGlassNavigationBar(
                    items = items,
                    icons = icons,
                    selectedIndex = selectedIndex,
                    onItemClick = onItemSelected,
                    backdrop = backdrop,
                    isBlurActive = blurActive,
                    modifier = Modifier
                        .windowInsetsPadding(
                            WindowInsets.displayCutout.union(WindowInsets.navigationBars)
                                .only(WindowInsetsSides.Horizontal),
                        )
                        .padding(horizontal = 24.dp)
                        .glassNavigationBarWidth(items.size, trailingActionWidth = 0.dp),
                )
            }
        }
    }
}

/**
 * 玻璃底栏宽度（复刻 miuix 示例 `GlassPage` 的底栏写法）。
 *
 * 手机上底栏本就该铺满（左右各 24.dp 留白）；但宽度变大后继续拉伸会让胶囊被拉得很长、
 * tab 间距变得松散。示例的做法是：按「胶囊 + 右侧搜索按钮」估算胶囊可用宽度，
 * 超过 400.dp 时把底栏收成 344.dp 胶囊加预留的动作按钮宽度，由父级 `Box` 居中，
 * 否则保持铺满。液态玻璃底栏没有独立动作按钮，因此无需预留宽度。
 */
private fun Modifier.glassNavigationBarWidth(
    itemCount: Int,
    trailingActionWidth: Dp = GlassNavigationBarDefaults.Height + GlassNavigationBarDefaults.ActionSpacing,
): Modifier = layout { measurable, constraints ->
    val actionWidth = trailingActionWidth.roundToPx()
    // 示例里 4 个 tab 时胶囊宽 344.dp、每个 tab 的额外间距 10.dp - 6.dp 归入可用宽度判断，
    // 阈值 400.dp；这里保持同一组数值，仅把 tab 数换成参数。
    val extraSpacing = (NavigationBarItemSpacing * (itemCount - 1) - NavigationBarSpacingPadding).roundToPx()
    val available = constraints.maxWidth - actionWidth + extraSpacing
    val width = if (constraints.hasBoundedWidth && available > NavigationBarCapsuleMaxAvailable.roundToPx()) {
        (NavigationBarCapsuleMaxWidth.roundToPx() + actionWidth).coerceIn(constraints.minWidth, constraints.maxWidth)
    } else {
        constraints.maxWidth
    }
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
}
