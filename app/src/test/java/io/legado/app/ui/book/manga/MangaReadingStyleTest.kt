package io.legado.app.ui.book.manga

import com.google.gson.Gson
import io.legado.app.ui.book.manga.config.MangaReadingStyle
import io.legado.app.ui.book.manga.config.mangaScrollIntervals
import org.junit.Assert.*
import org.junit.Test

class MangaReadingStyleTest {
    @Test fun serializedModesKeepStableNamesAcrossAllSupportedCombinations() {
        val gson = Gson()
        for (mode in 0..1) for (layout in 0..1) for (rtl in listOf(false, true)) for (effect in 0..3) {
            val style = MangaReadingStyle(mode, layout, rtl, effect)
            val json = "{\"mode\":$mode,\"layout\":$layout,\"rtl\":$rtl,\"effect\":$effect}"
            assertEquals(json, gson.toJson(style))
            assertEquals(style, gson.fromJson(json, MangaReadingStyle::class.java).normalized())
            assertEquals(mode == 0, style.paged)
            assertEquals(layout == 0, style.horizontal)
        }
    }

    @Test fun invalidValuesAreBoundedWithoutTurningDirectionIntoAnotherMode() {
        val style = Gson().fromJson("{\"mode\":99,\"layout\":-1,\"rtl\":true,\"effect\":99}", MangaReadingStyle::class.java).normalized()
        assertEquals(MangaReadingStyle(1, 0, true, 3), style)
        assertEquals(MangaReadingStyle(), Gson().fromJson("{}", MangaReadingStyle::class.java))
    }

    @Test fun scrollingSpeedIsMonotonicAndIndependentFromPageInterval() {
        assertEquals(9, mangaScrollIntervals.size)
        assertTrue(mangaScrollIntervals.toList().zipWithNext().all { (slow, fast) -> slow > fast && fast > 0 })
    }
}
