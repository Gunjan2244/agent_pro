package com.sledje.agentpro.buildlogic.verify

import java.io.File

/**
 * Enforces DESIGN_SYSTEM.md "Enforcement" on Kotlin outside `core/designsystem`:
 * - any raw `<number>.sp` literal fails;
 * - a raw `<number>.dp` literal inside a `padding(`, `spacedBy(`, `Spacer(`, `height(` or `width(` call fails
 *   (`0.dp` is allowed: zero isn't a spacing value).
 * Comments and string literals are ignored. Spacing must come from `Tokens.Space` / the design system.
 */
object KotlinSpacingScanner {
    private val SPACING_CALLS = setOf("padding", "spacedBy", "Spacer", "height", "width")
    private val RAW_SP = Regex("""(?<![\w.])(\d+(?:\.\d+)?)[fF]?\s*\.\s*sp\b""")
    private val RAW_DP = Regex("""(?<![\w.])(\d+(?:\.\d+)?)[fF]?\s*\.\s*dp\b""")

    fun isExempt(file: File): Boolean = file.invariantSeparatorsPath.contains("/core/designsystem/")

    /** Returns `line:column: message` entries. */
    fun scan(source: String): List<String> {
        val code = stripCommentsAndStrings(source)
        val lineStarts = buildList { add(0); code.forEachIndexed { i, c -> if (c == '\n') add(i + 1) } }
        fun pos(i: Int): String {
            val line = lineStarts.indexOfLast { it <= i }
            return "${line + 1}:${i - lineStarts[line] + 1}"
        }

        val out = mutableListOf<Pair<Int, String>>()
        RAW_SP.findAll(code).forEach { m ->
            out += m.range.first to "raw .sp literal \"${m.value.replace(Regex("\\s"), "")}\"; use the design system type styles (Tokens.Text)"
        }

        val dpStarts = RAW_DP.findAll(code).filter { it.groupValues[1].toDouble() != 0.0 }.associateBy { it.range.first }
        if (dpStarts.isNotEmpty()) {
            val stack = ArrayDeque<String?>()
            var i = 0
            while (i < code.length) {
                val c = code[i]
                dpStarts[i]?.let { m ->
                    val call = stack.lastOrNull { it != null && it in SPACING_CALLS }
                    if (call != null) {
                        out += i to "raw .dp spacing \"${m.value.replace(Regex("\\s"), "")}\" in $call(...); use Tokens.Space.s/m/l"
                    }
                }
                when (c) {
                    '(' -> stack.addLast(identifierBefore(code, i))
                    ')' -> stack.removeLastOrNull()
                }
                i++
            }
        }
        return out.sortedBy { it.first }.map { (i, msg) -> "${pos(i)}: $msg" }
    }

    private fun identifierBefore(code: String, parenIndex: Int): String? {
        var end = parenIndex - 1
        while (end >= 0 && code[end].isWhitespace()) end--
        var start = end
        while (start >= 0 && (code[start].isLetterOrDigit() || code[start] == '_')) start--
        return if (end > start) code.substring(start + 1, end + 1) else null
    }

    /** Replaces comments and string/char literal contents with spaces, keeping offsets and newlines. */
    fun stripCommentsAndStrings(src: String): String {
        val sb = StringBuilder(src)
        var i = 0
        fun blank(from: Int, to: Int) { for (k in from until minOf(to, sb.length)) if (sb[k] != '\n') sb.setCharAt(k, ' ') }
        while (i < src.length) {
            when {
                src.startsWith("//", i) -> {
                    val end = src.indexOf('\n', i).let { if (it < 0) src.length else it }
                    blank(i, end); i = end
                }
                src.startsWith("/*", i) -> {
                    val end = src.indexOf("*/", i + 2).let { if (it < 0) src.length else it + 2 }
                    blank(i, end); i = end
                }
                src.startsWith("\"\"\"", i) -> {
                    val end = src.indexOf("\"\"\"", i + 3).let { if (it < 0) src.length else it + 3 }
                    blank(i + 3, end - 3); i = end
                }
                src[i] == '"' || src[i] == '\'' -> {
                    val q = src[i]
                    var j = i + 1
                    while (j < src.length && src[j] != q && src[j] != '\n') { if (src[j] == '\\') j++; j++ }
                    blank(i + 1, j); i = j + 1
                }
                else -> i++
            }
        }
        return sb.toString()
    }
}
