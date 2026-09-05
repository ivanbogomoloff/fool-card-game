package com.example.foolcardgame.ui.components.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpponentDisplayNameTest {

    @Test
    fun tomHanks_twoLinesWithoutEllipsis() {
        assertEquals("Том" to "Хэнкс", formatCompactOpponentName("Том Хэнкс"))
    }

    @Test
    fun longSingleWord_oneLineWithEllipsis() {
        assertEquals("Алексан…" to null, formatCompactOpponentName("Александрович"))
    }

    @Test
    fun multipleSpaces_splitOnlyOnFirst() {
        assertEquals("Роберт" to "Дауни-м…", formatCompactOpponentName("Роберт Дауни-младший"))
        assertEquals("Иван" to "Иванови…", formatCompactOpponentName("Иван Иванович Петров"))
    }

    @Test
    fun shortWithoutSpace_unchanged() {
        assertEquals("Зендея" to null, formatCompactOpponentName("Зендея"))
    }

    @Test
    fun empty_returnsEmpty() {
        assertEquals("" to null, formatCompactOpponentName("   "))
        assertNull(formatCompactOpponentName("   ").second)
    }

    @Test
    fun singleLine_keepsSpaceWithoutSecondLine() {
        assertEquals("Том Хэнкс" to null, formatCompactOpponentName("Том Хэнкс", singleLine = true))
    }

    @Test
    fun singleLine_longName_truncatesTo16WithEllipsis() {
        assertEquals(
            "Роберт Дауни-мл…" to null,
            formatCompactOpponentName("Роберт Дауни-младший", singleLine = true),
        )
    }
}
