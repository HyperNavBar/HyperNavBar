package cn.ianzb.hypernavbar.ui.screen.about

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cn.ianzb.hypernavbar.R
import cn.ianzb.hypernavbar.ui.component.AppArrowPreference
import cn.ianzb.hypernavbar.ui.component.AppSectionCard
import cn.ianzb.hypernavbar.ui.component.SectionTitle
import cn.ianzb.hypernavbar.ui.component.SubPageScaffold
import cn.ianzb.hypernavbar.ui.util.LocalSubPageScrollBehavior
import cn.ianzb.hypernavbar.ui.util.pageScrollModifiers
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
            LibraryInfo("commonmark-java", "0.30.0", "BSD-2-Clause", "https://github.com/commonmark/commonmark-java"),
            LibraryInfo("Kotlin", "2.4.21", "Apache-2.0", "https://kotlinlang.org/"),
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
                "0.9.5-beta02",
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
                "Compose Material Icons Core",
                "1.7.8",
                "Apache-2.0",
                "https://developer.android.com/jetpack/androidx/releases/compose-material",
            ),
            LibraryInfo(
                "Miuix iOS liquid glass example",
                "-",
                "Apache-2.0",
                "https://github.com/compose-miuix-ui/miuix/tree/main/example/shared/src/commonMain/kotlin/component/liquid",
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
    SubPageScaffold(
        title = stringResource(R.string.third_party_licenses_title),
        isBlurEnabled = isBlurEnabled,
        onBack = onBack,
    ) { contentPadding ->
        val uriHandler = LocalUriHandler.current
        val lazyListState = rememberLazyListState()
        val scrollBehavior = LocalSubPageScrollBehavior.current
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize().then(
                if (scrollBehavior != null) Modifier.pageScrollModifiers(
                    showTopAppBar = true,
                    topAppBarScrollBehavior = scrollBehavior,
                    contentState = lazyListState,
                ) else Modifier,
            ),
            contentPadding = contentPadding,
        ) {
            item {
                MiuixText(
                    text = stringResource(R.string.licenses_header),
                    color = colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote2,
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 28.dp, end = 28.dp, top = 12.dp),
                )
            }

            licenseSections.forEach { section ->
                item(key = "section_${section.titleRes}") {
                    SectionTitle(text = stringResource(section.titleRes), followsContent = false)
                }
                itemsIndexed(
                    items = section.libraries,
                    key = { _, library -> "${section.titleRes}_${library.name}" },
                ) { _, library ->
                    AppSectionCard {
                        AppArrowPreference(
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
