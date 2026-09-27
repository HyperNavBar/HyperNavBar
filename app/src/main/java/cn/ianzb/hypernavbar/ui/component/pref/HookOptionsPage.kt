package cn.ianzb.hypernavbar.ui.component.pref

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.ianzb.hypernavbar.R
import cn.ianzb.hypernavbar.prefs.ConfigState
import cn.ianzb.hypernavbar.prefs.OptionRegistry
import cn.ianzb.hypernavbar.prefs.OptionSpec
import cn.ianzb.hypernavbar.prefs.OptionType
import cn.ianzb.hypernavbar.ui.util.BlurredBar
import cn.ianzb.hypernavbar.ui.util.blurSource
import cn.ianzb.hypernavbar.ui.util.pageScrollModifiers
import cn.ianzb.hypernavbar.ui.util.rememberBlurState
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Text as MiuixText

/**
 * 一个组件分区。
 *
 * @param titleRes 分区标题（中文描述）字符串资源。
 * @param specs 分区内包含的配置项，按顺序渲染。
 * @param titleEn 英文组件名，非空时以「中文（English）」拼接。**仅用于模板示例展示 API 英文名**；
 *   实际功能页应省略该参数，保持单语言小标题。
 */
data class HookSection(
    val titleRes: Int,
    val specs: List<OptionSpec>,
    val titleEn: String = "",
)

/**
 * 子页面搜索入口：把子页面内的配置项并入父页面搜索（支持多级嵌套）。
 *
 * 子页面自身不必再放搜索栏，其功能统一由最上层页面的搜索栏检索；命中后调用对应层级的
 * [onOpen] 直接打开目标页面。嵌套时搜索结果摘要以「父 / 子」路径展示。
 *
 * @param titleRes 子页面标题，作为搜索结果摘要（多级路径）的一部分。
 * @param specs 该层子页面直接包含的配置项（通常已在 App 启动时注册到 [OptionRegistry]，此处用于建立搜索映射）。
 * @param onOpen 点击搜索结果时打开该子页面。
 * @param subPages 更深一层的子页面，递归并入搜索。
 */
data class HookSubPage(
    val titleRes: Int,
    val specs: List<OptionSpec>,
    val onOpen: () -> Unit,
    val subPages: List<HookSubPage> = emptyList(),
)

private sealed interface SearchTarget {
    data class Section(val section: HookSection) : SearchTarget
    data class SubPage(val subPage: HookSubPage, val path: List<Int>) : SearchTarget
}

/** 递归展开子页面，并记录每层的标题路径（用于搜索结果摘要）。 */
private fun flattenSubPages(
    subPages: List<HookSubPage>,
    prefix: List<Int> = emptyList(),
): List<Pair<HookSubPage, List<Int>>> = subPages.flatMap { subPage ->
    val path = prefix + subPage.titleRes
    listOf(subPage to path) + flattenSubPages(subPage.subPages, path)
}

/** 分区标题：默认仅渲染 `titleRes`；`titleEn` 非空时拼接为 `中文（English）`（仅示例 / API 展示用）。 */
@Composable
fun hookSectionTitle(section: HookSection): String = buildString {
    append(stringResource(section.titleRes))
    if (section.titleEn.isNotBlank()) {
        append("（").append(section.titleEn).append("）")
    }
}

/**
 * 通用组件页面：顶栏 + 可折叠搜索栏 + 分区列表。
 *
 * - 自动注册分区内全部 [OptionSpec] 到 [OptionRegistry]，标题接入全局搜索。
 * - 搜索结果为可点击列表项，点击后收起搜索并滚动定位到对应分区（不内联渲染组件）。
 * - 通过 [subPages] 可把子页面内的功能并入搜索（支持多级 [HookSubPage.subPages] 递归），命中后直接打开目标子页面。
 * - 背景模糊等模块配置由 [isBlurEnabled] 控制。
 *
 * @param title 页面标题。
 * @param sections 组件分区列表。
 * @param subPages 子页面入口：把子页面（及其嵌套子页面）内的配置项并入搜索（不渲染在本页）。
 * @param isBlurEnabled 是否启用背景模糊。
 * @param extraBottomPadding 额外底部内边距（用于底部导航栏遮挡）。
 * @param topContent 页面顶部提示区（显示在搜索栏上方，搜索展开时隐藏）；为空则不显示。
 * @param onArrowClick 箭头卡片点击回调，参数为被点击的 [OptionSpec]。
 * @param customActionPackages 额外注入重启应用的包名（供二次开发直接暴露自定义应用）。
 * @param topBarActions 顶栏右侧扩展槽（显示在自动生成的「重启应用」按钮之前），为空则不显示。
 */
@Composable
fun HookOptionsPage(
    title: String,
    sections: List<HookSection>,
    subPages: List<HookSubPage> = emptyList(),
    isBlurEnabled: Boolean = true,
    extraBottomPadding: Dp = 0.dp,
    topContent: (@Composable () -> Unit)? = null,
    onArrowClick: (OptionSpec) -> Unit = {},
    customActionPackages: List<String> = emptyList(),
    topBarActions: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior()
    val hazeState = rememberBlurState()
    val blurActive = isBlurEnabled && hazeState != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface

    val listState = rememberLazyListState()
    var query by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var pendingScrollIndex by remember { mutableStateOf<Int?>(null) }
    var showQuickActions by remember { mutableStateOf(false) }

    // 递归展开子页面（记录标题路径），供搜索 / 包名汇总 / 注册使用。
    val flatSubPages = remember(subPages) { flattenSubPages(subPages) }

    // 汇总「包名列表」选项中的包名，并叠加二次开发注入的自定义包名。
    val allSpecs = sections.flatMap { it.specs } + flatSubPages.flatMap { it.first.specs }
    val configActionPackages = allSpecs
        .filter { it.type == OptionType.PACKAGE_LIST }
        .flatMap { parsePackageList(ConfigState.string(it.key, it.defaultString)) }
    val quickActionPackages = remember(configActionPackages, customActionPackages) {
        (configActionPackages + customActionPackages).distinct()
    }

    // 配置键 → 搜索目标（本页分区 / 子页面路径），含滑块主开关等被引用的键。
    val searchTargets = remember(sections, flatSubPages) {
        buildMap<String, SearchTarget> {
            sections.forEach { section ->
                section.specs.forEach { spec ->
                    put(spec.key, SearchTarget.Section(section))
                    spec.masterKey?.let { put(it, SearchTarget.Section(section)) }
                }
            }
            flatSubPages.forEach { (subPage, path) ->
                subPage.specs.forEach { spec ->
                    put(spec.key, SearchTarget.SubPage(subPage, path))
                    spec.masterKey?.let { put(it, SearchTarget.SubPage(subPage, path)) }
                }
            }
        }
    }

    LaunchedEffect(sections, flatSubPages) {
        OptionRegistry.registerAll(allSpecs)
    }

    // 点击搜索结果后，收起搜索并滚动到对应分区。
    LaunchedEffect(expanded, pendingScrollIndex) {
        val target = pendingScrollIndex
        if (!expanded && target != null) {
            listState.animateScrollToItem(target)
            pendingScrollIndex = null
        }
    }

    Scaffold(
        topBar = {
            BlurredBar(hazeState, blurActive, scrollBehavior) {
                TopAppBar(
                    title = title,
                    color = barColor,
                    scrollBehavior = scrollBehavior,
                    actions = {
                        topBarActions?.invoke()
                        if (quickActionPackages.isNotEmpty()) {
                            IconButton(onClick = { showQuickActions = true }) {
                                Icon(
                                    imageVector = MiuixIcons.Refresh,
                                    contentDescription = stringResource(R.string.quick_action_title),
                                    tint = MiuixTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    },
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(
            modifier = Modifier.blurSource(if (isBlurEnabled) hazeState else null)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .pageScrollModifiers(showTopAppBar = true, topAppBarScrollBehavior = scrollBehavior),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding() + extraBottomPadding,
                ),
            ) {
                if (!expanded && topContent != null) {
                    item(key = "top_content") { topContent() }
                }
                item {
                    SearchBar(
                        inputField = {
                            InputField(
                                query = query,
                                onQueryChange = { query = it },
                                onSearch = { expanded = false },
                                expanded = expanded,
                                onExpandedChange = { expanded = it },
                                label = stringResource(R.string.search_hint),
                            )
                        },
                        outsideEndAction = {
                            MiuixText(
                                text = stringResource(R.string.action_cancel),
                                color = MiuixTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(end = 16.dp)
                                    .clickable(
                                        interactionSource = null,
                                        indication = null,
                                    ) {
                                        expanded = false
                                        query = ""
                                    },
                            )
                        },
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 8.dp),
                    ) {
                        val results = OptionRegistry.search(context, query)
                            .filter { searchTargets.containsKey(it.key) }
                            .distinctBy { spec ->
                                when (val target = searchTargets[spec.key]) {
                                    is SearchTarget.Section -> "s:${target.section.titleRes}"
                                    is SearchTarget.SubPage -> "p:${target.path.joinToString("/")}"
                                    null -> spec.key
                                }
                            }
                        if (results.isEmpty()) {
                            MiuixText(
                                text = stringResource(R.string.search_empty),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                style = MiuixTheme.textStyles.footnote2,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        } else {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                results.forEach { spec ->
                                    val target = searchTargets[spec.key]
                                    BasicComponent(
                                        title = stringResource(spec.titleRes),
                                        summary = when (target) {
                                            is SearchTarget.Section -> hookSectionTitle(target.section)
                                            is SearchTarget.SubPage -> target.path.map { stringResource(it) }.joinToString(" / ")
                                            null -> null
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = {
                                            when (target) {
                                                is SearchTarget.Section ->
                                                    pendingScrollIndex = sections.indexOf(target.section) + 1
                                                is SearchTarget.SubPage -> target.subPage.onOpen()
                                                null -> Unit
                                            }
                                            expanded = false
                                            query = ""
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                if (!expanded) {
                    sections.forEach { section ->
                        item(key = section.titleRes) {
                            HookSectionCard(section) {
                                section.specs.forEach { spec ->
                                    HookOptionView(
                                        spec = spec,
                                        onArrowClick = { onArrowClick(spec) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showQuickActions) {
        QuickActionDialog(
            packages = quickActionPackages,
            onDismiss = { showQuickActions = false },
        )
    }
}

/**
 * 组件分区卡片：`SmallTitle`（`中文（English）`）+ 卡片容器。
 */
@Composable
fun HookSectionCard(
    section: HookSection,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SmallTitle(text = hookSectionTitle(section))
        Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
            content()
        }
    }
}
