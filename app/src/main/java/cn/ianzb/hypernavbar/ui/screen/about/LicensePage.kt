package cn.ianzb.hypernavbar.ui.screen.about

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import cn.ianzb.hypernavbar.R
import cn.ianzb.hypernavbar.ui.util.BlurredBar
import cn.ianzb.hypernavbar.ui.util.blurSource
import cn.ianzb.hypernavbar.ui.util.pageScrollModifiers
import cn.ianzb.hypernavbar.ui.util.rememberBlurState
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.basic.Text as MiuixText

/** 单个第三方库 / 参考项目信息。 */
data class LibraryInfo(
    val name: String,
    val version: String,
    val license: String,
    val website: String,
)

/** 第三方库分组。 */
private data class LicenseSection(
    val titleRes: Int,
    val libraries: List<LibraryInfo>,
)

private val licenseSections: List<LicenseSection> = listOf(
    LicenseSection(
        titleRes = R.string.licenses_section_core,
        libraries = listOf(
            LibraryInfo("Kotlin", "2.4.20", "Apache-2.0", "https://kotlinlang.org/"),
            LibraryInfo(
                "Kotlin Coroutines",
                "-",
                "Apache-2.0",
                "https://github.com/Kotlin/kotlinx.coroutines",
            ),
        ),
    ),
    LicenseSection(
        titleRes = R.string.licenses_section_ui,
        libraries = listOf(
            LibraryInfo(
                "Miuix (compose-miuix-ui)",
                "0.9.4",
                "Apache-2.0",
                "https://github.com/compose-miuix-ui/miuix",
            ),
            LibraryInfo(
                "Jetpack Compose",
                "BOM 2026.09.00",
                "Apache-2.0",
                "https://developer.android.com/jetpack/compose",
            ),
            LibraryInfo(
                "Compose Material3",
                "BOM 2026.09.00",
                "Apache-2.0",
                "https://developer.android.com/jetpack/androidx/releases/compose-material3",
            ),
            LibraryInfo(
                "Material Icons Core",
                "1.7.8",
                "Apache-2.0",
                "https://developer.android.com/jetpack/androidx/compose/material-icons",
            ),
            LibraryInfo(
                "Haze",
                "1.7.3",
                "Apache-2.0",
                "https://github.com/chrisbanes/haze",
            ),
        ),
    ),
    LicenseSection(
        titleRes = R.string.licenses_section_androidx,
        libraries = listOf(
            LibraryInfo(
                "AndroidX Core KTX",
                "1.19.1",
                "Apache-2.0",
                "https://developer.android.com/jetpack/androidx/releases/core",
            ),
            LibraryInfo(
                "AndroidX Activity Compose",
                "1.13.0",
                "Apache-2.0",
                "https://developer.android.com/jetpack/androidx/releases/activity",
            ),
            LibraryInfo(
                "AndroidX Lifecycle Runtime KTX",
                "2.11.0",
                "Apache-2.0",
                "https://developer.android.com/jetpack/androidx/releases/lifecycle",
            ),
        ),
    ),
    LicenseSection(
        titleRes = R.string.licenses_section_hook,
        libraries = listOf(
            LibraryInfo(
                "libxposed API",
                "102.0.0",
                "Apache-2.0",
                "https://github.com/libxposed/api",
            ),
            LibraryInfo(
                "libxposed Service",
                "102.0.0",
                "Apache-2.0",
                "https://github.com/libxposed/service",
            ),
            LibraryInfo(
                "DexKit",
                "2.2.0",
                "LGPL-3.0 / Apache-2.0",
                "https://github.com/LuckyPray/DexKit",
            ),
        ),
    ),
    LicenseSection(
        titleRes = R.string.licenses_section_refs,
        libraries = listOf(
            LibraryInfo(
                "SukiSU-Ultra",
                "-",
                "GPL-3.0",
                "https://github.com/SukiSU-Ultra/SukiSU-Ultra",
            ),
            LibraryInfo(
                "HyperCeiler",
                "-",
                "AGPL-3.0",
                "https://github.com/ReChronoRain/HyperCeiler",
            ),
            LibraryInfo(
                "HyperLight",
                "-",
                "非开源，仅思路参考",
                "https://github.com/KiminonaResa/HyperLight",
            ),
            LibraryInfo(
                "HyperModifier",
                "-",
                "GPL-3.0，手势提示线（小白条）显示控制实现参考，默认应用规则已合并",
                "https://github.com/AritxOnly/HyperModifier",
            ),
        ),
    ),
)

@Composable
fun LicensePageContent(
    onBack: () -> Unit,
    isBlurEnabled: Boolean = true,
) {
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val hazeState = rememberBlurState()
    val blurActive = isBlurEnabled && hazeState != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface

    Scaffold(
        topBar = {
            BlurredBar(hazeState, blurActive, topAppBarScrollBehavior) {
                TopAppBar(
                    title = stringResource(R.string.third_party_licenses_title),
                    color = barColor,
                    scrollBehavior = topAppBarScrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            val layoutDirection = LocalLayoutDirection.current
                            Icon(
                                modifier = Modifier.graphicsLayer {
                                    if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                                },
                                imageVector = MiuixIcons.Back,
                                contentDescription = stringResource(R.string.action_back),
                                tint = colorScheme.onBackground,
                            )
                        }
                    },
                )
            }
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        val uriHandler = LocalUriHandler.current
        val lazyListState = rememberLazyListState()
        val layoutDirection = LocalLayoutDirection.current
        val cutoutPadding = WindowInsets.displayCutout.asPaddingValues()
        val contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding(),
            bottom = innerPadding.calculateBottomPadding(),
            start = innerPadding.calculateStartPadding(layoutDirection) + cutoutPadding.calculateLeftPadding(LayoutDirection.Ltr),
            end = innerPadding.calculateEndPadding(layoutDirection) + cutoutPadding.calculateRightPadding(LayoutDirection.Ltr),
        )

        Box(
            modifier = Modifier.blurSource(if (isBlurEnabled) hazeState else null)
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .pageScrollModifiers(
                        showTopAppBar = true,
                        topAppBarScrollBehavior = topAppBarScrollBehavior,
                    )
                    .padding(
                        start = contentPadding.calculateStartPadding(layoutDirection),
                        end = contentPadding.calculateEndPadding(layoutDirection),
                    ),
                contentPadding = PaddingValues(
                    top = contentPadding.calculateTopPadding(),
                    bottom = contentPadding.calculateBottomPadding(),
                ),
            ) {
                item {
                    MiuixText(
                        text = stringResource(R.string.licenses_header),
                        color = colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.footnote2,
                        textAlign = TextAlign.Start,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 28.dp, vertical = 12.dp),
                    )
                }

                licenseSections.forEach { section ->
                    item(key = "section_${section.titleRes}") {
                        SmallTitle(text = stringResource(section.titleRes))
                    }
                    items(
                        items = section.libraries,
                        key = { "${section.titleRes}_${it.name}" },
                    ) { library ->
                        Card(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp)
                        ) {
                            ArrowPreference(
                                title = library.name,
                                summary = buildString {
                                    if (library.version.isNotBlank() && library.version != "-") {
                                        append("v").append(library.version).append(" · ")
                                    }
                                    append(library.license)
                                },
                                onClick = {
                                    uriHandler.openUri(library.website)
                                },
                            )
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}
