package io.legado.app.ui.book.manga

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import io.legado.app.data.entities.Book
import io.legado.app.ui.book.manga.config.MangaFooterConfig
import io.legado.app.ui.book.manga.config.MangaFooterContent
import io.legado.app.ui.book.manga.config.MangaReadingStyle
import io.legado.app.ui.book.manga.entities.MangaPage
import org.junit.Assert.*
import org.junit.Test

class MangaReaderContractTest {
    @Test fun directionChangesPhysicalCornersWithoutChangingMenuOrDisablingItsEntry() {
        assertEquals(MangaTap.NEXT, mangaTapAction(5f, 95f, 100f, 100f, true, true))
        assertEquals(MangaTap.PREVIOUS, mangaTapAction(95f, 95f, 100f, 100f, true, true))
        assertEquals(MangaTap.PREVIOUS, mangaTapAction(5f, 95f, 100f, 100f, true, false))
        assertEquals(MangaTap.NEXT, mangaTapAction(95f, 95f, 100f, 100f, true, false))
        assertEquals(MangaTap.MENU, mangaTapAction(50f, 50f, 100f, 100f, false, true))
        assertEquals(MangaTap.NONE, mangaTapAction(95f, 50f, 100f, 100f, true, true))
        assertEquals(MangaTap.NONE, mangaTapAction(95f, 95f, 100f, 100f, false, false))
    }
    @Test fun appendedChapterChangesTailIdentityWhileImageIdentityRemainsStable() {
        val page = MangaPage(chapterIndex = 0, chapterSize = 2, index = 0, imageCount = 1)
        val next = page.copy(chapterIndex = 1)
        assertNotEquals(mangaTailKey(listOf(page)), mangaTailKey(listOf(page, next)))
        assertEquals("0:0", mangaPageKey(page))
        assertEquals(mangaPageKey(next), mangaPageKey(next.copy(mImageUrl = "new-image")))
    }
    @Test fun emptyVolumeTailNeverSelectsAnImageFromThePreviousChapter() {
        val previous = MangaPage(chapterIndex = 0, chapterSize = 3, index = 2, imageCount = 3)
        val volume = io.legado.app.ui.book.manga.entities.ReaderLoading(chapterIndex = 1, index = -1, isVolume = true)
        assertSame(volume, mangaTailPage(listOf(previous, volume)))
        val next = previous.copy(chapterIndex = 2)
        val end = io.legado.app.ui.book.manga.entities.ReaderLoading(chapterIndex = 2, index = 3)
        assertSame(next, mangaTailPage(listOf(previous, volume, next, end)))
    }
    @Test fun singleBookStyleHasStableJsonAndPreservesManualBookType() {
        val gson = Gson()
        val style = MangaReadingStyle(mode = 0, layout = 0, rtl = true, effect = 3)
        val config = Book.ReadConfig(mangaReadingStyle = gson.toJson(style), customBookType = 8)
        val restored = gson.fromJson(gson.toJson(config), Book.ReadConfig::class.java)
        assertEquals(style, gson.fromJson(restored.mangaReadingStyle, MangaReadingStyle::class.java))
        assertEquals(8, restored.customBookType)
        assertNull(gson.fromJson("{\"reverseToc\":true}", Book.ReadConfig::class.java).mangaReadingStyle)
        assertEquals("mangaReadingStyle", Book.ReadConfig::class.java.getDeclaredField("mangaReadingStyle")
            .getAnnotation(SerializedName::class.java)?.value)
    }
    @Test fun existingFilterAndFooterVisibilityKeepTheirFieldNames() {
        val gson = Gson()
        val filter = gson.fromJson("{\"r\":12,\"g\":34,\"b\":56,\"a\":78,\"l\":90}",
            io.legado.app.ui.book.manga.config.MangaColorFilterConfig::class.java)
        assertEquals(12, filter.r)
        assertEquals(90, filter.l)
        val footer = gson.fromJson("{\"hideFooter\":true,\"hidePageNumberLabel\":true,\"footerOrientation\":1}", MangaFooterConfig::class.java)
        assertTrue(footer.hideFooter)
        assertEquals(MangaFooterContent.CHAPTER_NAME, footer.left)
        assertEquals(MangaFooterContent.IMAGE_NUMBER, footer.middle)
        assertEquals(MangaFooterContent.TIME, footer.right)
        assertTrue(gson.toJson(footer).contains("\"hideFooter\":true"))
    }
    @Test fun footerDistinguishesCurrentImageFromChapterAndKeepsNearEndCap() {
        val page = MangaPage(chapterIndex = 999, chapterSize = 1000, index = 9998, imageCount = 10000, mChapterName = "章节")
        assertEquals("99.9%", mangaFooterText(MangaFooterContent.PROGRESS, page, "", "", null))
        assertEquals("100.0%", mangaFooterText(MangaFooterContent.PROGRESS,
            page.copy(chapterIndex = 999, index = 9999), "", "", null))
        val short = MangaPage(chapterIndex = 1, chapterSize = 3, index = 2, imageCount = 4, mChapterName = "第二章")
        assertEquals("图片 3/4", mangaFooterText(MangaFooterContent.IMAGE_NUMBER, short, "", "", null))
        assertEquals("章节 2/3", mangaFooterText(MangaFooterContent.CHAPTER_NUMBER, short, "", "", null))
    }
}
