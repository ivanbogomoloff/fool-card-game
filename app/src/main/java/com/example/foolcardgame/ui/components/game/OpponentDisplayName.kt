package com.example.foolcardgame.ui.components.game

private const val CompactLineLimit = 8
private const val Ellipsis = "…"

/**
 * Compact opponent name for the table plate.
 *
 * Default: at most two lines, 8 chars each (split on the first space).
 * [singleLine]: one line up to 16 chars, spaces preserved.
 */
fun formatCompactOpponentName(
    full: String,
    singleLine: Boolean = false,
): Pair<String, String?> {
    val trimmed = full.trim()
    if (trimmed.isEmpty()) return "" to null

    if (singleLine) {
        return truncateWithEllipsis(trimmed, CompactLineLimit * 2) to null
    }

    val spaceIndex = trimmed.indexOf(' ')
    if (spaceIndex < 0) {
        return truncateWithEllipsis(trimmed, CompactLineLimit) to null
    }

    val first = trimmed.substring(0, spaceIndex)
    val rest = trimmed.substring(spaceIndex + 1).trimStart()
    return truncateWithEllipsis(first, CompactLineLimit) to
        truncateWithEllipsis(rest, CompactLineLimit).ifEmpty { null }
}

private fun truncateWithEllipsis(value: String, maxChars: Int): String {
    if (value.length <= maxChars) return value
    if (maxChars <= 1) return Ellipsis
    return value.take(maxChars - 1) + Ellipsis
}
