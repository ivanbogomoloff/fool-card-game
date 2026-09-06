package com.example.foolcardgame.ui.components.game

private const val CompactLineLimit = 8
private const val Ellipsis = "…"

/**
 * Компактное имя оппонента для плашки за столом.
 *
 * По умолчанию: не более двух строк по 8 символов (разрыв по первому пробелу).
 * [singleLine]: одна строка до 16 символов, пробелы сохраняются.
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
