package cn.ianzb.hypernavbar.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val markdownParser = Parser.builder().build()
private val markdownRenderer = HtmlRenderer.builder().escapeHtml(true).sanitizeUrls(true).build()

@Composable
internal fun UpdateMarkdown(markdown: String, maxHeight: Dp = 240.dp) {
    val linkColor = MiuixTheme.colorScheme.primary
    val text = remember(markdown, linkColor) {
        val html = markdownRenderer.render(markdownParser.parse(markdown))
            .replace("<code>", "<tt>")
            .replace("</code>", "</tt>")
        AnnotatedString.fromHtml(
            html,
            linkStyles = TextLinkStyles(
                style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
            ),
        )
    }
    BasicText(
        text = text,
        style = MiuixTheme.textStyles.body2.copy(color = MiuixTheme.colorScheme.onSurfaceVariantSummary),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 12.dp),
    )
}
