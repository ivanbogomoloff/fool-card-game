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
    private val playArea = Rect(0f, 100f, 800f, 400f)
    private val actionBar = Rect(0f, 400f, 800f, 500f)
    private val hand = Rect(0f, 500f, 800f, 620f)
    private val layout = Rect(0f, 0f, 800f, 800f)
    private val cardSize = DraggedCardSizePx(width = 56f, height = 80f)

    private fun dropZone() = tableDropZone(
        playAreaBounds = playArea,
        actionBarBounds = actionBar,
        handBounds = hand,
        layoutBounds = layout,
    )

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
        val widePlayArea = Rect(0f, 0f, 800f, 300f)
        val action = resolveTableDrop(
            position = Offset(20f, 150f),
            tablePairs = listOf(beatenPair(id = 1)),
            tableBounds = widePlayArea,
            attackCardBounds = mapOf(1 to Rect(360f, 80f, 416f, 160f)),
        )

        assertEquals(TableDropAction.Attack, action)
    }

    @Test
    fun dropOnLeftOfPlayArea_emptyTable_isAttack() {
        val widePlayArea = Rect(0f, 0f, 800f, 300f)
        val action = resolveTableDrop(
            position = Offset(20f, 150f),
            tablePairs = emptyList(),
            tableBounds = widePlayArea,
            attackCardBounds = emptyMap(),
        )

        assertEquals(TableDropAction.Attack, action)
    }

    @Test
    fun dropOnLeftOfPlayArea_unbeaten_isDefend() {
        val widePlayArea = Rect(0f, 0f, 800f, 300f)
        val action = resolveTableDrop(
            position = Offset(20f, 150f),
            tablePairs = listOf(unbeatenPair(id = 1)),
            tableBounds = widePlayArea,
            attackCardBounds = mapOf(1 to Rect(360f, 80f, 416f, 160f)),
        )

        assertEquals(TableDropAction.Defend(1), action)
    }

    @Test
    fun dropAbovePlayArea_isIgnored() {
        val boundedPlayArea = Rect(0f, 100f, 800f, 400f)
        val action = resolveTableDrop(
            position = Offset(200f, 20f),
            tablePairs = listOf(beatenPair(id = 1)),
            tableBounds = boundedPlayArea,
            attackCardBounds = mapOf(1 to attackBounds),
        )

        assertNull(action)
    }

    @Test
    fun dropOnBeatenPairSlop_outsidePlayArea_isAttack() {
        val boundedPlayArea = Rect(100f, 0f, 800f, 300f)
        val pairBounds = Rect(20f, 80f, 76f, 160f)
        val action = resolveTableDrop(
            position = Offset(30f, 120f),
            tablePairs = listOf(beatenPair(id = 1)),
            tableBounds = boundedPlayArea,
            attackCardBounds = mapOf(1 to pairBounds),
        )

        assertEquals(TableDropAction.Attack, action)
    }

    @Test
    fun tableDropZone_excludesActionBarAboveHand() {
        val zone = tableDropZone(
            playAreaBounds = playArea,
            actionBarBounds = actionBar,
            handBounds = hand,
            layoutBounds = layout,
        )
        assertEquals(Rect(0f, 100f, 800f, 400f), zone)
    }

    @Test
    fun dropOnActionBar_allBeaten_isIgnored() {
        val action = resolveTableDrop(
            position = Offset(40f, 450f),
            tablePairs = listOf(beatenPair(id = 1)),
            tableBounds = dropZone(),
            attackCardBounds = mapOf(1 to Rect(360f, 180f, 416f, 260f)),
            playAreaBounds = playArea,
        )

        assertNull(action)
    }

    @Test
    fun dropOnActionBar_unbeaten_isIgnored() {
        val action = resolveTableDrop(
            position = Offset(40f, 450f),
            tablePairs = listOf(unbeatenPair(id = 1)),
            tableBounds = dropZone(),
            attackCardBounds = mapOf(1 to Rect(360f, 180f, 416f, 260f)),
            playAreaBounds = playArea,
        )

        assertNull(action)
    }

    @Test
    fun dropWithFingerInHand_cardLiftedAboveHand_onlyOverActionBar_isIgnored() {
        val action = resolveTableDrop(
            position = Offset(40f, 530f),
            tablePairs = listOf(beatenPair(id = 1)),
            tableBounds = dropZone(),
            attackCardBounds = emptyMap(),
            cardSize = cardSize,
            handTop = hand.top,
            playAreaBounds = playArea,
        )

        assertNull(action)
    }

    @Test
    fun dropWithFingerInHand_cardLiftedAboveHand_overlapsPlayArea_isAttack() {
        val action = resolveTableDrop(
            position = Offset(40f, 250f),
            tablePairs = listOf(beatenPair(id = 1)),
            tableBounds = dropZone(),
            attackCardBounds = emptyMap(),
            cardSize = cardSize,
            handTop = hand.top,
            playAreaBounds = playArea,
        )

        assertEquals(TableDropAction.Attack, action)
    }

    @Test
    fun dropWithFingerInHand_cardOverlapsZone_allBeaten_isAttack() {
        val action = resolveTableDrop(
            position = Offset(40f, 350f),
            tablePairs = listOf(beatenPair(id = 1)),
            tableBounds = dropZone(),
            attackCardBounds = mapOf(1 to Rect(360f, 180f, 416f, 260f)),
            cardSize = cardSize,
            handTop = hand.top,
            playAreaBounds = playArea,
        )

        assertEquals(TableDropAction.Attack, action)
    }

    @Test
    fun dropWithFingerInHand_notLifted_isIgnored() {
        val action = resolveTableDrop(
            position = Offset(40f, 580f),
            tablePairs = listOf(beatenPair(id = 1)),
            tableBounds = dropZone(),
            attackCardBounds = emptyMap(),
            cardSize = cardSize,
            handTop = hand.top,
            playAreaBounds = playArea,
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
