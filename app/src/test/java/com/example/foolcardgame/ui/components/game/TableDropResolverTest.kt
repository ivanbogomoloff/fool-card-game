package com.example.foolcardgame.ui.components.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.example.foolcardgame.presentation.game.CardUi
import com.example.foolcardgame.presentation.game.TablePairUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TableDropResolverTest {

    private val tableBounds = Rect(0f, 0f, 400f, 300f)
    private val attackBounds = Rect(20f, 80f, 76f, 160f)

    @Test
    fun dropBesideAttackCard_onTable_withOneUnbeaten_isDefend() {
        val pairs = listOf(unbeatenPair(id = 1))
        val besideCard = Offset(x = attackBounds.right + 30f, y = attackBounds.center.y)

        val action = resolveTableDrop(
            position = besideCard,
            tablePairs = pairs,
            tableBounds = tableBounds,
            attackCardBounds = mapOf(1 to attackBounds),
        )

        assertEquals(TableDropAction.Defend(1), action)
    }

    @Test
    fun dropOnTable_withUnbeatenPair_isNotAttack() {
        val action = resolveTableDrop(
            position = Offset(300f, 150f),
            tablePairs = listOf(unbeatenPair(id = 1)),
            tableBounds = tableBounds,
            attackCardBounds = mapOf(1 to attackBounds),
        )

        assertTrue(action is TableDropAction.Defend)
    }

    @Test
    fun dropOnTable_allBeaten_isAttack() {
        val action = resolveTableDrop(
            position = Offset(200f, 150f),
            tablePairs = listOf(beatenPair(id = 1)),
            tableBounds = tableBounds,
            attackCardBounds = mapOf(1 to attackBounds),
        )

        assertEquals(TableDropAction.Attack, action)
    }

    @Test
    fun dropOnEmptyTable_isAttack() {
        val action = resolveTableDrop(
            position = Offset(200f, 150f),
            tablePairs = emptyList(),
            tableBounds = tableBounds,
            attackCardBounds = emptyMap(),
        )

        assertEquals(TableDropAction.Attack, action)
    }

    @Test
    fun dropOnTable_unbeatenWithoutBounds_snapsToThatPair() {
        val action = resolveTableDrop(
            position = Offset(200f, 150f),
            tablePairs = listOf(unbeatenPair(id = 7)),
            tableBounds = tableBounds,
            attackCardBounds = emptyMap(),
        )

        assertEquals(TableDropAction.Defend(7), action)
    }

    @Test
    fun dropOffTableAndAwayFromCards_isIgnored() {
        val action = resolveTableDrop(
            position = Offset(-80f, -80f),
            tablePairs = listOf(unbeatenPair(id = 1)),
            tableBounds = tableBounds,
            attackCardBounds = mapOf(1 to attackBounds),
        )

        assertNull(action)
    }

    @Test
    fun dropOnLeftOfPlayArea_allBeaten_isAttack() {
        val playArea = Rect(0f, 0f, 800f, 300f)
        val action = resolveTableDrop(
            position = Offset(20f, 150f),
            tablePairs = listOf(beatenPair(id = 1)),
            tableBounds = playArea,
            attackCardBounds = mapOf(1 to Rect(360f, 80f, 416f, 160f)),
        )

        assertEquals(TableDropAction.Attack, action)
    }

    @Test
    fun dropOnLeftOfPlayArea_emptyTable_isAttack() {
        val playArea = Rect(0f, 0f, 800f, 300f)
        val action = resolveTableDrop(
            position = Offset(20f, 150f),
            tablePairs = emptyList(),
            tableBounds = playArea,
            attackCardBounds = emptyMap(),
        )

        assertEquals(TableDropAction.Attack, action)
    }

    @Test
    fun dropOnLeftOfPlayArea_unbeaten_isDefend() {
        val playArea = Rect(0f, 0f, 800f, 300f)
        val action = resolveTableDrop(
            position = Offset(20f, 150f),
            tablePairs = listOf(unbeatenPair(id = 1)),
            tableBounds = playArea,
            attackCardBounds = mapOf(1 to Rect(360f, 80f, 416f, 160f)),
        )

        assertEquals(TableDropAction.Defend(1), action)
    }

    @Test
    fun dropAbovePlayArea_isIgnored() {
        val playArea = Rect(0f, 100f, 800f, 400f)
        val action = resolveTableDrop(
            position = Offset(200f, 20f),
            tablePairs = listOf(beatenPair(id = 1)),
            tableBounds = playArea,
            attackCardBounds = mapOf(1 to attackBounds),
        )

        assertNull(action)
    }

    @Test
    fun dropOnBeatenPairSlop_outsidePlayArea_isAttack() {
        val playArea = Rect(100f, 0f, 800f, 300f)
        val pairBounds = Rect(20f, 80f, 76f, 160f)
        val action = resolveTableDrop(
            position = Offset(30f, 120f),
            tablePairs = listOf(beatenPair(id = 1)),
            tableBounds = playArea,
            attackCardBounds = mapOf(1 to pairBounds),
        )

        assertEquals(TableDropAction.Attack, action)
    }

    @Test
    fun tableDropZone_includesActionBarAboveHand() {
        val zone = tableDropZone(
            playAreaBounds = Rect(0f, 100f, 400f, 400f),
            handBounds = Rect(0f, 500f, 400f, 620f),
            layoutBounds = Rect(0f, 0f, 400f, 800f),
        )
        assertEquals(Rect(0f, 100f, 400f, 500f), zone)
    }

    @Test
    fun dropOnActionBar_allBeaten_isAttack() {
        val dropZone = tableDropZone(
            playAreaBounds = Rect(0f, 100f, 800f, 400f),
            handBounds = Rect(0f, 500f, 800f, 620f),
            layoutBounds = Rect(0f, 0f, 800f, 800f),
        )
        val action = resolveTableDrop(
            position = Offset(40f, 450f),
            tablePairs = listOf(beatenPair(id = 1)),
            tableBounds = dropZone,
            attackCardBounds = mapOf(1 to Rect(360f, 180f, 416f, 260f)),
        )

        assertEquals(TableDropAction.Attack, action)
    }

    @Test
    fun dropOnActionBar_unbeaten_isDefend() {
        val dropZone = tableDropZone(
            playAreaBounds = Rect(0f, 100f, 800f, 400f),
            handBounds = Rect(0f, 500f, 800f, 620f),
            layoutBounds = Rect(0f, 0f, 800f, 800f),
        )
        val action = resolveTableDrop(
            position = Offset(40f, 450f),
            tablePairs = listOf(unbeatenPair(id = 1)),
            tableBounds = dropZone,
            attackCardBounds = mapOf(1 to Rect(360f, 180f, 416f, 260f)),
        )

        assertEquals(TableDropAction.Defend(1), action)
    }

    private val cardSize = DraggedCardSizePx(width = 56f, height = 80f)

    @Test
    fun dropWithFingerInHand_cardOverlapsZone_allBeaten_isAttack() {
        val dropZone = tableDropZone(
            playAreaBounds = Rect(0f, 100f, 800f, 400f),
            handBounds = Rect(0f, 500f, 800f, 620f),
            layoutBounds = Rect(0f, 0f, 800f, 800f),
        )
        val action = resolveTableDrop(
            position = Offset(40f, 538f),
            tablePairs = listOf(beatenPair(id = 1)),
            tableBounds = dropZone,
            attackCardBounds = mapOf(1 to Rect(360f, 180f, 416f, 260f)),
            cardSize = cardSize,
            handTop = 500f,
        )

        assertEquals(TableDropAction.Attack, action)
    }

    @Test
    fun dropWithFingerInHand_cardLiftedAboveHand_allBeaten_isAttack() {
        val dropZone = tableDropZone(
            playAreaBounds = Rect(0f, 100f, 800f, 400f),
            handBounds = Rect(0f, 500f, 800f, 620f),
            layoutBounds = Rect(0f, 0f, 800f, 800f),
        )
        val action = resolveTableDrop(
            position = Offset(40f, 530f),
            tablePairs = listOf(beatenPair(id = 1)),
            tableBounds = dropZone,
            attackCardBounds = emptyMap(),
            cardSize = cardSize,
            handTop = 500f,
        )

        assertEquals(TableDropAction.Attack, action)
    }

    @Test
    fun dropWithFingerInHand_notLifted_isIgnored() {
        val dropZone = tableDropZone(
            playAreaBounds = Rect(0f, 100f, 800f, 400f),
            handBounds = Rect(0f, 500f, 800f, 620f),
            layoutBounds = Rect(0f, 0f, 800f, 800f),
        )
        val action = resolveTableDrop(
            position = Offset(40f, 580f),
            tablePairs = listOf(beatenPair(id = 1)),
            tableBounds = dropZone,
            attackCardBounds = emptyMap(),
            cardSize = cardSize,
            handTop = 500f,
        )

        assertNull(action)
    }

    private fun unbeatenPair(id: Int) = TablePairUi(
        id = id,
        attack = card("6♣"),
        defense = null,
    )

    private fun beatenPair(id: Int) = TablePairUi(
        id = id,
        attack = card("6♣"),
        defense = card("9♣"),
    )

    private fun card(label: String) = CardUi(
        id = label,
        rankLabel = label.take(1),
        suitSymbol = label.takeLast(1),
        isRed = false,
    )
}
