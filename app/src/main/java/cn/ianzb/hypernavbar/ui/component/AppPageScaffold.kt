package cn.ianzb.hypernavbar.ui.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.ianzb.hypernavbar.R
import cn.ianzb.hypernavbar.ui.component.search.rememberAppSearchOpen
import cn.ianzb.hypernavbar.ui.util.PageBackdrop
import cn.ianzb.hypernavbar.ui.util.PageBodyBackdrop
import cn.ianzb.hypernavbar.ui.util.pageScrollModifiers
import cn.ianzb.hypernavbar.ui.util.pageStartInset
import cn.ianzb.hypernavbar.ui.util.rememberBlurBackdrop
import cn.ianzb.hypernavbar.ui.util.rememberTopBarBackdrop
import cn.ianzb.hypernavbar.ui.util.withPageStartInset
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal val AppPageSafeWindowInsets: WindowInsets
    @Composable
    get() = WindowInsets.systemBars.union(WindowInsets.captionBar)
        .union(WindowInsets.displayCutout)

/** 正文的水平安全区，顶栏和列表分别处理顶部与底部留白。 */
val AppPageContentWindowInsets: WindowInsets
    @Composable
    get() = AppPageSafeWindowInsets.only(WindowInsetsSides.Horizontal)

@Composable
internal fun Modifier.appPageHorizontalInsets(): Modifier =
    windowInsetsPadding(AppPageContentWindowInsets).pageStartInset()

@Composable
internal fun appPageListPadding(innerPadding: PaddingValues, extraBottomPadding: Dp = 0.dp): PaddingValues {
    val systemPadding = AppPageSafeWindowInsets.asPaddingValues()
    return remember(innerPadding, extraBottomPadding, systemPadding) {
        object : PaddingValues by innerPadding {
            override fun calculateBottomPadding() =
                maxOf(innerPadding.calculateBottomPadding() + extraBottomPadding, systemPadding.calculateBottomPadding())
        }
    }.withPageStartInset()
}

/**
 * 页面骨架：玻璃顶栏 + [PageBackdrop] 采样图层。
 *
 * 顶栏采样正文，页面弹窗通过 `LocalPreferenceBackdrop` 采样整页。
 * 使用 `renderInRootScaffold` 的页面应放在外层 Scaffold 中，由外层宿主绘制弹窗。
 *
 * @param title 顶栏标题。
 * @param isBlurEnabled 总开关（用户设置中的「背景模糊」）；关闭时顶栏用纯色、弹层用不透明材质。
 * @param scrollBehavior 该页滚动行为，需与内容列表共用，顶栏折叠/浮现由它驱动。
 * @param onBack 非空时在顶栏左侧渲染返回按钮（二级页使用）。
 * @param titleAlpha 标题透明度，随滚动变化的页面（如关于页）用它做淡入。
 * @param expandable 是否允许大标题折叠成小标题；保持大标题的页面传 `false`。
 * @param useGlassTopBar 是否使用 OS4 玻璃顶栏；默认 `true`（二级页也走同一条路径）。
 *   置为 `false` 时不再单独渲染另一种顶栏，而是让玻璃顶栏不采样 backdrop（缺少运行时着色器时同样走这条路）。
 * @param color 顶栏底色覆盖项；为空时按模糊是否生效自动取透明或主题表面色。
 * @param topBarSearchAction 是否在顶栏渲染全局搜索入口（只有可搜索的「功能页」为 true；
 *   搜索状态由主页容器通过 `LocalAppSearchState` 下发，独立 Activity 中该值为 false 时也不会渲染）。
 * @param actions 顶栏右侧动作区。
 */
@Composable
fun AppPageScaffold(
    title: String,
    isBlurEnabled: Boolean,
    modifier: Modifier = Modifier,
    scrollBehavior: ScrollBehavior = MiuixScrollBehavior(),
    onBack: (() -> Unit)? = null,
    titleAlpha: (() -> Float)? = null,
    expandable: Boolean = true,
    useGlassTopBar: Boolean = true,
    color: Color? = null,
    topBarSearchAction: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    contentWindowInsets: WindowInsets = AppPageContentWindowInsets,
    content: @Composable (PaddingValues) -> Unit,
) {
    val topBarBackdrop = rememberTopBarBackdrop(isBlurEnabled)
    val popupBackdrop = if (isBlurEnabled) rememberBlurBackdrop() else null
    val blurActive = topBarBackdrop != null
    val barColor = color ?: if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface
    // 全局搜索入口：只有在主页容器（`LocalAppSearchState` 可用）内才渲染顶栏搜索按钮，
    // 二级页面 / 许可证页等独立 Activity 没有搜索状态，因此不会出现一个按了没反应的图标。
    val openSearch = rememberAppSearchOpen()
    PageBackdrop(popupBackdrop, modifier = modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                AppTopAppBar(
                    title = title,
                    scrollBehavior = scrollBehavior,
                    color = barColor,
                    backdrop = topBarBackdrop,
                    onBack = onBack,
                    titleAlpha = titleAlpha ?: { 1f },
                    expandable = expandable,
                    useGlassTopBar = useGlassTopBar,
                    actions = {
                        if (topBarSearchAction && openSearch != null) {
                            AppTopAppBarIconAction(onClick = openSearch) {
                                Icon(
                                    imageVector = MiuixIcons.Glass.Search,
                                    modifier = Modifier.size(28.dp),
                                    contentDescription = stringResource(R.string.search_hint),
                                    tint = MiuixTheme.colorScheme.onSurface,
                                )
                            }
                            // 与 miuix 示例一致：顶栏多个动作之间用 8.dp 间隔，按钮不相互贴边。
                            Spacer(Modifier.width(8.dp))
                        }
                        actions()
                    },
                )
            },
            contentWindowInsets = contentWindowInsets,
        ) { innerPadding ->
            PageBodyBackdrop(topBarBackdrop) {
                content(innerPadding)
            }
        }
    }
}

/**
 * 单列表页面的骨架：在 [AppPageScaffold] 基础上直接给出配置好的 [LazyColumn]。
 *
 * 列表已经处理了顶栏滚动联动、系统安全区与宽屏侧栏留白，
 * 页面只需要提供 `item { }` 内容。
 *
 * @param extraBottomPadding 额外底部留白，例如为悬浮底栏/悬浮按钮让出空间。
 * @param listState 需要外部控制滚动位置时传入（例如回到顶部）。
 */
@Composable
fun AppLazyPage(
    title: String,
    isBlurEnabled: Boolean,
    extraBottomPadding: Dp = 0.dp,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    scrollBehavior: ScrollBehavior = MiuixScrollBehavior(),
    onBack: (() -> Unit)? = null,
    topBarActions: @Composable RowScope.() -> Unit = {},
    topBarSearchAction: Boolean = false,
    contentWindowInsets: WindowInsets = AppPageContentWindowInsets,
    content: LazyListScope.() -> Unit,
) {
    AppPageScaffold(
        title = title,
        isBlurEnabled = isBlurEnabled,
        modifier = modifier,
        scrollBehavior = scrollBehavior,
        onBack = onBack,
        actions = topBarActions,
        topBarSearchAction = topBarSearchAction,
        contentWindowInsets = contentWindowInsets,
    ) { innerPadding ->
        val listPadding = appPageListPadding(innerPadding, extraBottomPadding)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .pageScrollModifiers(
                    showTopAppBar = true,
                    topAppBarScrollBehavior = scrollBehavior,
                    contentState = listState,
                ),
            contentPadding = listPadding,
            content = content,
        )
    }
}
