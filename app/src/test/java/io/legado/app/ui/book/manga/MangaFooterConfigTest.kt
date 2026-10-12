package io.legado.app.ui.book.manga

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import io.legado.app.ui.book.manga.config.MangaFooterConfig
import io.legado.app.ui.book.manga.config.MangaFooterContent
import io.legado.app.ui.book.manga.config.withContent
import io.legado.app.ui.book.manga.entities.MangaPage
import org.junit.Assert.*
import org.junit.Test

class MangaFooterConfigTest {
    @Test fun slotIdsAndSerializedFieldsRemainStable() {
        assertArrayEquals(intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8), intArrayOf(
            MangaFooterContent.NONE, MangaFooterContent.BOOK_NAME, MangaFooterContent.CHAPTER_NAME,
            MangaFooterContent.IMAGE_NUMBER, MangaFooterContent.CHAPTER_NUMBER, MangaFooterContent.PROGRESS,
            MangaFooterContent.TIME, MangaFooterContent.BATTERY, MangaFooterContent.TIME_BATTERY,
        ))
        val names = MangaFooterConfig::class.java.declaredFields.associate {
            it.name to it.getAnnotation(SerializedName::class.java)?.value
        }
        assertEquals(mapOf("hideFooter" to "hideFooter", "left" to "left",
            "middle" to "middle", "right" to "right"), names)
        val config = MangaFooterConfig(true, MangaFooterContent.NONE,
            MangaFooterContent.BOOK_NAME, MangaFooterContent.TIME_BATTERY)
        val gson = Gson()
        assertEquals("{\"hideFooter\":true,\"left\":0,\"middle\":1,\"right\":8}", gson.toJson(config))
        assertEquals(config, gson.fromJson(gson.toJson(config), MangaFooterConfig::class.java))
    }

    @Test fun oldVisibilityIsReadWhileRemovedSwitchesDoNotInventSlotOverrides() {
        val old = Gson().fromJson("{\"hideFooter\":true,\"hideChapterName\":true," +
            "\"hidePageNumber\":true,\"hideChapter\":true,\"footerOrientation\":1}", MangaFooterConfig::class.java)
        assertEquals(MangaFooterConfig(hideFooter = true), old)
        assertEquals(MangaFooterConfig(), Gson().fromJson("{}", MangaFooterConfig::class.java))
    }

    @Test fun assigningAnExistingItemMovesItAndLeavesOtherSlotsUnchanged() {
        val original = MangaFooterConfig()
        val moved = original.withContent(2, MangaFooterContent.CHAPTER_NAME)
        assertEquals(MangaFooterContent.NONE, moved.left)
        assertEquals(MangaFooterContent.IMAGE_NUMBER, moved.middle)
        assertEquals(MangaFooterContent.CHAPTER_NAME, moved.right)
        assertEquals(MangaFooterContent.CHAPTER_NAME, original.left)
        assertEquals(moved, moved.withContent(2, MangaFooterContent.CHAPTER_NAME))
        val moveToMiddle = moved.withContent(1, MangaFooterContent.CHAPTER_NAME)
        assertEquals(MangaFooterContent.NONE, moveToMiddle.right)
        assertEquals(MangaFooterContent.CHAPTER_NAME, moveToMiddle.middle)
    }

    @Test fun emptySlotsCanRepeatAndVisibilityIsPreserved() {
        val config = MangaFooterConfig(hideFooter = true)
            .withContent(0, MangaFooterContent.NONE)
            .withContent(1, MangaFooterContent.NONE)
            .withContent(2, MangaFooterContent.NONE)
        assertEquals(MangaFooterConfig(true, 0, 0, 0), config)
    }

    @Test fun assigningAnItemClearsEveryOtherDuplicate() {
        val duplicated = MangaFooterConfig(left = MangaFooterContent.TIME,
            middle = MangaFooterContent.TIME, right = MangaFooterContent.TIME)
        assertEquals(MangaFooterConfig(left = 0, middle = MangaFooterContent.TIME, right = 0),
            duplicated.withContent(1, MangaFooterContent.TIME))
    }

    @Test fun contentsRenderIndependentlyFromTheirSlots() {
        val page = MangaPage(chapterIndex = 1, chapterSize = 3, index = 2,
            imageCount = 4, mChapterName = "第二章")
        fun text(content: Int) = mangaFooterText(content, page, "书名", "09:04", 85)
        assertEquals("", text(MangaFooterContent.NONE))
        assertEquals("书名", text(MangaFooterContent.BOOK_NAME))
        assertEquals("第二章", text(MangaFooterContent.CHAPTER_NAME))
        assertEquals("图片 3/4", text(MangaFooterContent.IMAGE_NUMBER))
        assertEquals("章节 2/3", text(MangaFooterContent.CHAPTER_NUMBER))
        assertEquals("58.3%", text(MangaFooterContent.PROGRESS))
        assertEquals("09:04", text(MangaFooterContent.TIME))
        assertEquals("85%", text(MangaFooterContent.BATTERY))
        assertEquals("09:04 85%", text(MangaFooterContent.TIME_BATTERY))
    }

    @Test fun absentPagesLeaveOnlyIndependentInformationAvailable() {
        for (content in MangaFooterContent.CHAPTER_NAME..MangaFooterContent.PROGRESS) {
            assertEquals("", mangaFooterText(content, null, "书名", "09:04", null))
        }
        assertEquals("书名", mangaFooterText(MangaFooterContent.BOOK_NAME, null, "书名", "09:04", null))
        assertEquals("09:04", mangaFooterText(MangaFooterContent.TIME, null, "", "09:04", null))
        assertEquals("--%", mangaFooterText(MangaFooterContent.BATTERY, null, "", "", null))
        assertEquals("09:04 --%", mangaFooterText(MangaFooterContent.TIME_BATTERY, null, "", "09:04", null))
    }

    @Test fun zeroDenominatorsNeverProduceInvalidNumbersOrProgress() {
        val empty = MangaPage(chapterSize = 0, imageCount = 0)
        assertEquals("图片 0/0", mangaFooterText(MangaFooterContent.IMAGE_NUMBER, empty, "", "", null))
        assertEquals("章节 0/0", mangaFooterText(MangaFooterContent.CHAPTER_NUMBER, empty, "", "", null))
        assertEquals("0.0%", mangaFooterText(MangaFooterContent.PROGRESS, empty, "", "", null))
        assertEquals("0.0%", mangaFooterText(MangaFooterContent.PROGRESS,
            empty.copy(chapterSize = 3), "", "", null))
        assertEquals("66.7%", mangaFooterText(MangaFooterContent.PROGRESS,
            empty.copy(chapterIndex = 1, chapterSize = 3), "", "", null))
    }

    @Test fun batteryUsesScaleAndHandlesMissingOrInvalidReadings() {
        assertNull(mangaBatteryPercent(-1, 100))
        assertNull(mangaBatteryPercent(10, -1))
        assertNull(mangaBatteryPercent(10, 0))
        assertEquals(85, mangaBatteryPercent(170, 200))
        assertEquals(0, mangaBatteryPercent(0, 100))
        assertEquals(100, mangaBatteryPercent(200, 100))
        assertEquals("100%", mangaFooterText(MangaFooterContent.BATTERY, null, "", "", 500))
    }
}
