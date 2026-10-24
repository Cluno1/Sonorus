package io.github.cluno1.sonorus.features.catalog.presentation

import java.text.Normalizer
import java.util.Locale

/**
 * issue 116: client-side check of a Work tag draft before saving. Mirrors the backend
 * `normalize_work_tags` (Issue 109) so the user sees errors next to the input; the server stays
 * authoritative and normalizes again.
 */
object CatalogWorkTagsPolicy {
    const val MAX_TAGS = 50
    const val MAX_LENGTH = 40

    enum class Problem { EMPTY, TOO_LONG, DUPLICATE }

    sealed interface Check {
        /** [tags] is the normalized list to send. */
        data class Valid(val tags: List<String>) : Check

        /** [problems] maps draft index → problem; [tooMany] when over [MAX_TAGS]. */
        data class Invalid(val problems: Map<Int, Problem>, val tooMany: Boolean) : Check
    }

    /** NFC, trims and collapses inner whitespace — the backend's display label. */
    fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFC).trim().split(Regex("\\s+")).filter(String::isNotEmpty)
            .joinToString(" ")

    /** Case-insensitive identity, like Python `casefold()` for the scripts tags use. */
    fun key(value: String): String = normalize(value).lowercase(Locale.ROOT)

    fun check(draft: List<String>): Check {
        val problems = linkedMapOf<Int, Problem>()
        val seen = mutableSetOf<String>()
        val result = mutableListOf<String>()
        draft.forEachIndexed { index, raw ->
            val label = normalize(raw)
            val length = label.codePointCount(0, label.length)
            when {
                length == 0 -> problems[index] = Problem.EMPTY
                length > MAX_LENGTH -> problems[index] = Problem.TOO_LONG
                !seen.add(key(label)) -> problems[index] = Problem.DUPLICATE
                else -> result += label
            }
        }
        val tooMany = draft.size > MAX_TAGS
        return if (problems.isEmpty() && !tooMany) Check.Valid(result) else Check.Invalid(problems, tooMany)
    }
}
