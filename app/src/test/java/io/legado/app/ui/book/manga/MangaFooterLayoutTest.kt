package io.legado.app.ui.book.manga

import org.junit.Assert.*
import org.junit.Test

class MangaFooterLayoutTest {
    @Test fun emptyMiddleLetsChapterUseAllSpaceBeforeClock() {
        val slots = mangaFooterSlotBounds(360, 300, 0, 40, 8)
        assertEquals(MangaFooterSlotBounds(0, 300), slots[0])
        assertEquals(MangaFooterSlotBounds(320, 40), slots[2])
    }

    @Test fun overflowingChapterDoesNotTruncateShortClock() {
        val slots = mangaFooterSlotBounds(360, 500, 0, 40, 8)
        assertEquals(312, slots[0].width)
        assertEquals(MangaFooterSlotBounds(320, 40), slots[2])
        val reverse = mangaFooterSlotBounds(360, 40, 0, 500, 8)
        assertEquals(40, reverse[0].width)
        assertEquals(MangaFooterSlotBounds(48, 312), reverse[2])
    }

    @Test fun aSingleItemCanUseTheWholeBarAtItsConfiguredPosition() {
        for (position in 0..2) {
            val natural = IntArray(3).also { it[position] = 500 }
            val slots = mangaFooterSlotBounds(360, natural[0], natural[1], natural[2], 8)
            assertEquals(MangaFooterSlotBounds(0, 360), slots[position])
            assertEquals(0, slots.filterIndexed { index, _ -> index != position }.sumOf { it.width })
        }
    }

    @Test fun shortMiddleStaysAtPhysicalCenterWithUnequalSides() {
        val slots = mangaFooterSlotBounds(360, 300, 60, 40, 8)
        assertEquals(MangaFooterSlotBounds(150, 60), slots[1])
        assertEquals(MangaFooterSlotBounds(320, 40), slots[2])
        assertEquals(142, slots[0].width)
    }

    @Test fun longMiddleYieldsEnoughSpaceForShortSideInformation() {
        val slots = mangaFooterSlotBounds(360, 40, 500, 0, 8)
        assertEquals(MangaFooterSlotBounds(0, 40), slots[0])
        assertEquals(MangaFooterSlotBounds(48, 264), slots[1])
    }

    @Test fun geometryNeverOverlapsOrEscapesEvenForNarrowBarsAndLongNames() {
        for (width in listOf(0, 1, 2, 9, 31, 100, 359, 360)) {
            for (left in listOf(0, 1, 40, 500)) {
                for (middle in listOf(0, 1, 60, 500)) {
                    for (right in listOf(0, 1, 40, 500)) {
                        val slots = mangaFooterSlotBounds(width, left, middle, right, 8)
                        slots.forEach {
                            assertTrue(it.x >= 0)
                            assertTrue(it.width >= 0)
                            assertTrue(it.x + it.width <= width)
                        }
                        val visible = slots.filter { it.width > 0 }
                        visible.zipWithNext().forEach { (a, b) -> assertTrue(a.x + a.width <= b.x) }
                        if (slots[1].width > 0) {
                            assertTrue(kotlin.math.abs(slots[1].x * 2 + slots[1].width - width) <= 1)
                        }
                    }
                }
            }
        }
    }
}
