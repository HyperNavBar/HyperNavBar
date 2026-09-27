package cn.ianzb.hypernavbar.hook.rule

/**
 * 宽松版本号比较器。
 *
 * 按「数字段 / 文本段」交替切分后逐段比较：数字段按数值比较，文本段忽略大小写按字典序比较，
 * 因此可以正确处理以下常见形态：
 * - `8.01.02.7722-260904-09221533-R`（应用版本名）
 * - `OS4.0.0.33.XPMCNXM`（HyperOS 版本）
 * - `35`（Android SDK）
 *
 * 分隔符（`.` `-` `_` `+` `/` `:` 空格）会被忽略。
 */
class Version private constructor(val raw: String) : Comparable<Version> {

    private val tokens: List<Token> = tokenize(raw)

    override fun compareTo(other: Version): Int {
        val a = tokens
        val b = other.tokens
        val size = maxOf(a.size, b.size)
        for (i in 0 until size) {
            val x = a.getOrNull(i) ?: return -1
            val y = b.getOrNull(i) ?: return 1
            val result = x.compareTo(y)
            if (result != 0) return result
        }
        return 0
    }

    override fun equals(other: Any?): Boolean = other is Version && compareTo(other) == 0

    override fun hashCode(): Int = tokens.hashCode()

    override fun toString(): String = raw

    private sealed interface Token : Comparable<Token> {
        data class Num(val value: Long) : Token {
            override fun compareTo(other: Token): Int = when (other) {
                is Num -> value.compareTo(other.value)
                is Text -> -1 // 数字段排在文本段之前
            }

            override fun toString(): String = value.toString()
        }

        data class Text(val value: String) : Token {
            override fun compareTo(other: Token): Int = when (other) {
                is Num -> 1
                is Text -> value.compareTo(other.value, ignoreCase = true)
            }

            override fun toString(): String = value
        }
    }

    companion object {

        fun of(raw: String?): Version = Version(raw.orEmpty())

        fun of(value: Int): Version = Version(value.toString())

        fun of(value: Long): Version = Version(value.toString())

        private val PATTERN = Regex("([0-9]+)|([^0-9]+)")
        private val SEPARATORS = charArrayOf('.', '-', '_', '+', '/', ':', ' ')

        private fun tokenize(raw: String): List<Token> =
            PATTERN.findAll(raw)
                .mapNotNull { match ->
                    val digits = match.groups[1]?.value
                    if (digits != null) {
                        Token.Num(digits.toLongOrNull() ?: 0L)
                    } else {
                        val text = match.groups[2]?.value?.trim(*SEPARATORS).orEmpty()
                        if (text.isEmpty()) null else Token.Text(text)
                    }
                }
                .toList()
    }
}
