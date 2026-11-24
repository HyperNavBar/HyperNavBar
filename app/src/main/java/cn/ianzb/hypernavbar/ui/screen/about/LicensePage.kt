package cn.ianzb.hypernavbar.ui.screen.about

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cn.ianzb.hypernavbar.R
import cn.ianzb.hypernavbar.ui.component.AppArrowPreference
import cn.ianzb.hypernavbar.ui.component.AppLazyPage
import cn.ianzb.hypernavbar.ui.component.AppSectionCard
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.SmallTitle
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
                "0.9.5-beta03",
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
    AppLazyPage(
        title = stringResource(R.string.third_party_licenses_title),
        isBlurEnabled = isBlurEnabled,
        onBack = onBack,
    ) {
        item {
            MiuixText(
                text = stringResource(R.string.licenses_header),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
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
                val uriHandler = LocalUriHandler.current
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
    }
}
