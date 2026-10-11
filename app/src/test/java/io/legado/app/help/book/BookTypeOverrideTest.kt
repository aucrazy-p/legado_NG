package io.legado.app.help.book

import com.google.gson.annotations.SerializedName
import io.legado.app.constant.BookSourceType
import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSource
import io.legado.app.help.source.getBookType
import io.legado.app.model.jsSource.JsSourceMarshaller
import io.legado.app.utils.GSON
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class BookTypeOverrideTest {

    @Test
    fun manuallySelectedImageSurvivesRepeatedTextSourceRefresh() {
        val book = Book(type = BookType.text)
        book.setBookTypeFromEditor(BookType.image, manuallySelected = true)
        repeat(3) { book.updateTypeFromSource(BookType.text) }
        assertEquals(BookType.image, book.type)
        assertEquals(BookType.image, book.readConfig?.customBookType)
    }

    @Test
    fun untouchedBookContinuesFollowingSourceChanges() {
        val book = Book(type = BookType.text)
        book.updateTypeFromSource(BookType.image)
        assertEquals(BookType.image, book.type)
        book.updateTypeFromSource(BookType.audio)
        assertEquals(BookType.audio, book.type)
        assertNull(book.readConfig)
    }

    @Test
    fun savingOtherFieldsDoesNotCreateManualType() {
        val book = Book(type = BookType.text)
        book.setBookTypeFromEditor(BookType.text, manuallySelected = false)
        book.updateTypeFromSource(BookType.image)
        assertEquals(BookType.image, book.type)
        assertNull(book.readConfig)
    }

    @Test
    fun explicitSelectionOfCurrentTypeStillRecordsManualChoice() {
        val book = Book(type = BookType.image)
        book.setBookTypeFromEditor(BookType.image, manuallySelected = true)
        book.updateTypeFromSource(BookType.text)
        assertEquals(BookType.image, book.type)
    }

    @Test
    fun changingManualTypeAndSavingOtherFieldsKeepsLatestChoice() {
        val book = Book(type = BookType.text)
        book.setBookTypeFromEditor(BookType.image, manuallySelected = true)
        book.setBookTypeFromEditor(BookType.text, manuallySelected = true)
        book.setBookTypeFromEditor(BookType.text, manuallySelected = false)
        book.updateTypeFromSource(BookType.audio)
        assertEquals(BookType.text, book.type)
        assertEquals(BookType.text, book.readConfig?.customBookType)
    }

    @Test
    fun manualTypesPreserveLocalArchiveAndShelfStateBits() {
        val states = BookType.local or BookType.archive or BookType.notShelf or BookType.updateError
        listOf(BookType.text, BookType.image, BookType.audio, BookType.video).forEach { selected ->
            val book = Book(type = BookType.text or states)
            book.setBookTypeFromEditor(selected, manuallySelected = true)
            book.updateTypeFromSource(BookType.text)
            assertEquals(selected or states, book.type)
        }
    }

    @Test
    fun editingBookWithEmptyTypeBitsPreservesExistingLocalOriginDetection() {
        val book = Book(origin = BookType.localTag, type = 0)
        book.setBookTypeFromEditor(BookType.image, manuallySelected = true)
        assertEquals(BookType.image or BookType.local, book.type)
        assertEquals(BookType.image, book.readConfig?.customBookType)
    }

    @Test
    fun downloadSourceKeepsItsCapabilityBitWithManualMediaType() {
        val source = BookSource(bookSourceType = BookSourceType.file)
        val book = Book(type = BookType.image)
        book.setBookTypeFromEditor(BookType.image, manuallySelected = true)
        book.updateTypeFromSource(source.getBookType())
        assertEquals(BookType.image or BookType.webFile, book.type)
        assertFalse(book.isType(BookType.text))
    }

    @Test
    fun storedOverrideSurvivesReadConfigAndBookJsonRoundTrips() {
        val book = Book(bookUrl = "test://comic", type = BookType.text)
        book.setBookTypeFromEditor(BookType.image, manuallySelected = true)
        val converters = Book.Converters()
        val configJson = converters.readConfigToString(book.readConfig)
        assertEquals(BookType.image, GSON.fromJson(configJson, Book.ReadConfig::class.java).customBookType)
        val restored = Book(type = BookType.text, readConfig = converters.stringToReadConfig(configJson))
        restored.updateTypeFromSource(BookType.text)
        assertEquals(BookType.image, restored.type)
        val restoredBook = GSON.fromJson(GSON.toJson(book), Book::class.java)
        restoredBook.updateTypeFromSource(BookType.text)
        assertEquals(BookType.image, restoredBook.type)
        assertEquals("customBookType", Book.ReadConfig::class.java.getDeclaredField("customBookType")
            .getAnnotation(SerializedName::class.java).value)
    }

    @Test
    fun olderConfigWithoutOverrideContinuesFollowingSource() {
        val config = Book.Converters().stringToReadConfig("""{"reverseToc":true,"pageAnim":2}""")!!
        assertNull(config.customBookType)
        val book = Book(type = BookType.text, readConfig = config)
        book.updateTypeFromSource(BookType.image)
        assertEquals(BookType.image, book.type)
        assertEquals(true, book.config.reverseToc)
        assertEquals(2, book.config.pageAnim)
    }

    @Test
    fun sourceCannotTurnAnInvalidOverrideIntoLocalOrStateFlags() {
        listOf(0, BookType.local, BookType.image or BookType.audio, -1).forEach { invalid ->
            val book = Book(readConfig = Book.ReadConfig(customBookType = invalid))
            book.updateTypeFromSource(BookType.text)
            assertEquals(BookType.text, book.type)
        }
    }

    @Test
    fun latestConfigIsAppliedAfterRefreshSnapshotIsReplaced() {
        val refreshing = Book(type = BookType.text)
        refreshing.updateTypeFromSource(BookType.text)
        val edited = refreshing.copy()
        edited.setBookTypeFromEditor(BookType.image, manuallySelected = true)
        refreshing.readConfig = edited.readConfig
        refreshing.applyCustomBookType()
        assertEquals(BookType.image, refreshing.type)
    }

    @Test
    fun changingSourceImmediatelyAppliesBookOwnedOverride() {
        val original = Book(type = BookType.image)
        original.setBookTypeFromEditor(BookType.image, manuallySelected = true)
        val migrated = original.migrateTo(Book(type = BookType.text), emptyList())
        assertEquals(BookType.image, migrated.type)
        val updated = original.updateTo(Book(type = BookType.audio))
        assertEquals(BookType.image, updated.type)
    }

    @Test
    fun jsDetailsHonorManualTypeButUntouchedBooksAcceptReturnedType() {
        val source = BookSource()
        val json = """{"type":${BookType.text}}"""
        val manual = Book(type = BookType.image)
        manual.setBookTypeFromEditor(BookType.image, manuallySelected = true)
        JsSourceMarshaller.mergeBookInfo(manual, json, source, canReName = true)
        assertEquals(BookType.image, manual.type)
        val automatic = Book(type = BookType.image)
        JsSourceMarshaller.mergeBookInfo(automatic, json, source, canReName = true)
        assertEquals(BookType.text, automatic.type)
        assertNull(automatic.readConfig)
    }
}
