package com.sledje.agentpro.core.format

/**
 * Indian mobile numbers, with exactly the backend rule (`private.normalize_in_phone`, docs/BACKEND.md):
 * remove spaces, `(`, `)`, `.` and `-`; then the number must be `+91`, `91` or `0` followed by
 * `[6-9]` and 9 digits, or just `[6-9]` and 9 digits. It is stored as `+91XXXXXXXXXX`.
 * Landlines and foreign numbers are rejected.
 */
object Phone {
    private val separators = Regex("""[\s().\-]""")
    private val withPlus91 = Regex("""^\+91([6-9]\d{9})$""")
    private val with91 = Regex("""^91([6-9]\d{9})$""")
    private val with0 = Regex("""^0([6-9]\d{9})$""")
    private val bare = Regex("""^([6-9]\d{9})$""")

    /** `+91XXXXXXXXXX`, or null when [input] isn't a valid Indian mobile number. */
    fun normalize(input: String): String? {
        val v = input.replace(separators, "")
        val national = listOf(withPlus91, with91, with0, bare).firstNotNullOfOrNull { it.matchEntire(v) }
            ?.groupValues?.get(1) ?: return null
        return "+91$national"
    }

    fun isValid(input: String): Boolean = normalize(input) != null

    /** "+91 98765 43210" for display; returns [input] unchanged when it can't be normalised. */
    fun display(input: String): String {
        val e164 = normalize(input) ?: return input
        val national = e164.removePrefix("+91")
        return "+91 ${national.take(5)} ${national.drop(5)}"
    }
}
