package io.legado.app.help.source

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.legado.app.data.AppDatabase
import io.legado.app.data.dao.deleteByKeysChunked
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.Cache
import io.legado.app.data.entities.RssSource
import io.legado.app.help.CacheManager
import io.legado.app.help.config.matchingSourceConfigKeys
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [35])
class SourceVariableCleanupTest {
    private lateinit var db: AppDatabase
    private val memoryKeys = mutableSetOf<String>()
    private val source = "https://example.invalid/source_a"

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        memoryKeys.forEach(CacheManager::deleteMemory)
        db.close()
    }

    private fun stateKeys(url: String) = listOf(
        "v_${url}_token", "v_${url}_under_score_token", "v_${url}_",
        "userInfo_$url", "loginHeader_$url", "sourceVariable_$url", "infoMap_$url"
    )

    private fun seed(keys: List<String>) {
        db.cacheDao.insert(*keys.map { Cache(it, "value:$it") }.toTypedArray())
        keys.forEach {
            memoryKeys.add(it)
            CacheManager.putMemory(it, "value:$it")
        }
    }

    private fun deleteBooks(urls: List<String>) {
        db.runInTransaction {
            db.bookSourceDao.deleteByKeysChunked(urls)
            db.cacheDao.deleteSourceVariables(urls)
        }
    }

    private fun assertPresent(keys: List<String>) {
        keys.forEach {
            assertEquals(it, "value:$it", db.cacheDao.get(it)?.value)
            assertEquals(it, "value:$it", CacheManager.getFromMemory(it))
        }
    }

    private fun assertAbsent(keys: List<String>) {
        keys.forEach {
            assertNull(it, db.cacheDao.get(it))
            assertNull(it, CacheManager.getFromMemory(it))
        }
    }

    @Test
    fun singleBookDeletionPreservesPrefixNeighborAndUnrelatedMemory() {
        val other = "${source}_other"
        db.bookSourceDao.insert(BookSource(bookSourceUrl = source), BookSource(bookSourceUrl = other, enabled = false))
        val selected = stateKeys(source)
        val retained = stateKeys(other) + listOf("source_cache_shared:value", "nh-123-4-0-text")
        seed(selected + retained)
        val memoryOnly = "v_${source}_memory_only"
        memoryKeys.add(memoryOnly)
        CacheManager.putMemory(memoryOnly, "memory")

        deleteBooks(listOf(source))

        assertAbsent(selected)
        assertNull(CacheManager.getFromMemory(memoryOnly))
        assertPresent(retained)
        assertEquals(listOf(other), db.cacheDao.sourceKeys())
    }

    @Test
    fun wildcardCharactersAndCaseAreLiteral() {
        val selected = "https://example.invalid/a_b%Case"
        val others = listOf("https://example.invalid/axbZZCase", "https://example.invalid/a_b%case")
        db.bookSourceDao.insert(* (listOf(selected) + others).map { BookSource(bookSourceUrl = it) }.toTypedArray())
        seed((listOf(selected) + others).flatMap(::stateKeys))

        deleteBooks(listOf(selected))

        assertAbsent(stateKeys(selected))
        assertPresent(others.flatMap(::stateKeys))
    }

    @Test
    fun retainedShorterSourceProtectsAmbiguousVariableNames() {
        val longer = "${source}_other"
        db.bookSourceDao.insert(BookSource(bookSourceUrl = source), BookSource(bookSourceUrl = longer))
        val keys = stateKeys(longer)
        seed(keys)

        deleteBooks(listOf(longer))

        // Also readable by the shorter source as source.get("other_token"), etc.
        assertPresent(keys.filter { it.startsWith("v_") })
        assertAbsent(keys.filterNot { it.startsWith("v_") })
    }

    @Test
    fun batchRemovesAllSelectedPrefixNeighborsAfterTheirRowsAreDeleted() {
        val urls = listOf(source, "${source}_other", "${source}_other_third")
        db.bookSourceDao.insert(*urls.map { BookSource(bookSourceUrl = it) }.toTypedArray())
        seed(urls.flatMap(::stateKeys))

        deleteBooks(urls + source)

        assertTrue(db.cacheDao.allKeys().isEmpty())
        assertAbsent(urls.flatMap(::stateKeys))
    }

    @Test
    fun sameUrlRssKeepsSharedStateUntilLastSourceIsDeleted() {
        db.bookSourceDao.insert(BookSource(bookSourceUrl = source))
        db.rssSourceDao.insert(RssSource(sourceUrl = source))
        seed(stateKeys(source))

        deleteBooks(listOf(source))
        assertPresent(stateKeys(source))

        db.runInTransaction {
            db.rssSourceDao.delete(source)
            db.cacheDao.deleteSourceVariables(listOf(source))
        }
        assertAbsent(stateKeys(source))
    }

    @Test
    fun rssDeletionAndRenamePreserveBookAndNewRssPrefixes() {
        val book = "${source}_book"
        val renamed = "${source}_renamed"
        db.bookSourceDao.insert(BookSource(bookSourceUrl = book))
        db.rssSourceDao.insert(RssSource(sourceUrl = source))
        seed((stateKeys(source) + stateKeys(book) + stateKeys(renamed)))

        db.runInTransaction {
            db.rssSourceDao.delete(source)
            db.rssSourceDao.insert(RssSource(sourceUrl = renamed))
            db.cacheDao.deleteSourceVariables(listOf(source))
        }

        assertAbsent(stateKeys(source))
        assertPresent(stateKeys(book) + stateKeys(renamed))
    }

    @Test
    fun largeBatchesCrossSqliteBindingLimit() {
        val urls = (0..950).map { "https://example.invalid/source#$it" }
        val keys = urls.map { "v_${it}_token" }
        db.bookSourceDao.insert(*urls.map { BookSource(bookSourceUrl = it) }.toTypedArray())
        seed(keys)

        deleteBooks(urls)

        assertTrue(db.cacheDao.allKeys().isEmpty())
        assertTrue(db.cacheDao.sourceKeys().isEmpty())
        assertAbsent(keys)
    }

    @Test
    fun emptyOrUnknownSelectionDoesNotClearOtherSourcesMemory() {
        seed(stateKeys(source))
        deleteBooks(emptyList())
        deleteBooks(listOf("https://example.invalid/missing"))
        assertPresent(stateKeys(source))
    }

    @Test
    fun sourceScoresUseBoundariesAndProtectAmbiguousRetainedSources() {
        val other = "${source}_other"
        val adjacent = "${source}2"
        val keys = listOf(source, "${source}_book_author", other, "${other}_book_author", adjacent)
        assertEquals(listOf(source, "${source}_book_author"), matchingSourceConfigKeys(keys, listOf(source), listOf(other, adjacent)))
        assertTrue(matchingSourceConfigKeys(keys, listOf(other), listOf(source)).isEmpty())
        assertEquals(keys.dropLast(1), matchingSourceConfigKeys(keys, listOf(source, other), listOf(adjacent)))
    }

    @Test
    fun selectionHandlesEmptyAndUnderscoredVariableNamesWithoutRelaxingPrefixes() {
        val selection = SourceVariableCacheSelection(listOf(source), emptyList())
        assertTrue(selection.contains("v_${source}_"))
        assertTrue(selection.contains("v_${source}_a_b_c"))
        assertFalse(selection.contains("vX${source}_token"))
        assertFalse(selection.contains("v_${source}2_token"))
        assertFalse(selection.contains("loginHeader_${source}_other"))
    }
}
