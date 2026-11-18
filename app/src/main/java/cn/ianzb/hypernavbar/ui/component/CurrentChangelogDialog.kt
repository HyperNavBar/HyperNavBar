package cn.ianzb.hypernavbar.ui.component

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cn.ianzb.hypernavbar.R
import cn.ianzb.hypernavbar.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 更新日志正文的资产文件名。 */
private const val CHANGELOG_ASSET = "changelog.md"

/** 「未发布」小节标题里的关键字，调试构建时允许与当前版本一起展示。 */
private const val UNRELEASED_HEADING = "未发布"

/** 小节标题里出现的版本号，如 `## 3.2.1` / `## [3.2.1] - 2026-11-08` / `## v3.2.1`。 */
private val VERSION_PATTERN = Regex("""\d+(?:\.\d+)*""")

/** 正文最多占用的高度，超出部分在对话框内滚动，避免长日志把弹层撑出屏幕。 */
private val CONTENT_MAX_HEIGHT = 320.dp

@Composable
fun CurrentChangelogDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val changelogText by produceState<String?>(initialValue = null, key1 = context) {
        value = withContext(Dispatchers.IO) {
            val version = UpdateChecker.currentVersion(context)
            val debug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
            runCatching {
                val markdown = context.assets.open(CHANGELOG_ASSET).bufferedReader().use { it.readText() }
                extractChangelogText(markdown, version, debug)
            }.getOrDefault("")
        }
    }

    AppDialog(
        show = true,
        title = stringResource(R.string.current_changelog),
        onDismissRequest = onDismiss,
    ) {
        changelogText?.let { text ->
            UpdateMarkdown(text.ifBlank { stringResource(R.string.current_changelog_unavailable) }, CONTENT_MAX_HEIGHT)
        }
        AppDialogButton(
            text = stringResource(R.string.action_cancel),
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            primary = true,
        )
    }
}

/** 一个由二级标题切出的日志小节。 */
private data class ChangelogSection(
    val heading: String,
    val body: List<String>,
)

/**
 * 按 `## ` 二级标题把日志切成小节；`# 更新日志` 之类的标题与正文都属于「标题之前」的部分，直接忽略。
 *
 * 三级标题（如 `### 变更`）不会被当作小节边界，会保留在小节正文里。
 */
private fun parseChangelogSections(markdown: String): List<ChangelogSection> {
    val sections = mutableListOf<ChangelogSection>()
    var heading: String? = null
    var body: MutableList<String> = mutableListOf()
    for (line in markdown.lines()) {
        if (line.startsWith("## ")) {
            heading?.let { sections += ChangelogSection(it, body) }
            heading = line.removePrefix("## ").trim()
            body = mutableListOf()
        } else if (heading != null) {
            body += line
        }
    }
    heading?.let { sections += ChangelogSection(it, body) }
    return sections
}

/** 取小节标题里的版本号；标题不含版本号（如「未发布」）时返回 null。 */
private fun headingVersion(heading: String): String? = VERSION_PATTERN.find(heading)?.value

/** 归一化版本号，容忍 安装包版本名中可能带上的 `v` / `V` 前缀与空白。 */
private fun normalizeVersionName(versionName: String): String =
    versionName.trim().removePrefix("v").removePrefix("V").trim()

/**
 * 从完整日志里取出应当展示的文本。
 *
 * @param markdown 完整的 `changelog.md` 文本
 * @param versionName 当前版本号，通常传 [UpdateChecker.currentVersion]
 * @param includeUnreleased 是否拼接匹配小节之前的「未发布」内容（仅调试构建）
 * @return 可展示的纯文本；无版本标题可匹配时返回完整日志文本
 */
internal fun extractChangelogText(
    markdown: String,
    versionName: String,
    includeUnreleased: Boolean,
): String {
    val normalized = markdown.replace("\r\n", "\n").replace('\r', '\n').trim()
    if (normalized.isEmpty()) return ""

    val version = normalizeVersionName(versionName)
    val sections = parseChangelogSections(normalized)
    val matchedIndex = if (version.isEmpty()) {
        -1
    } else {
        sections.indexOfFirst { headingVersion(it.heading) == version }
    }
    // 没有版本标题（或没有匹配到当前版本）：退化为整篇日志。
    if (matchedIndex < 0) return normalized

    val picked = buildList {
        if (includeUnreleased) {
            addAll(sections.take(matchedIndex).filter { it.heading.contains(UNRELEASED_HEADING) })
        }
        add(sections[matchedIndex])
    }
    return picked
        .joinToString("\n\n") { section ->
            (listOf("## ${section.heading}") + section.body).joinToString("\n")
        }
        .trim()
}
