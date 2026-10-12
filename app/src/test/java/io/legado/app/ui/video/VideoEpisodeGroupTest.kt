package io.legado.app.ui.video

import org.junit.Assert.*
import org.junit.Test

class VideoEpisodeGroupTest {
    private data class Chapter(val title: String, val volume: Boolean = false, val playable: Boolean = true)
    private fun groups(vararg chapters: Chapter) = videoEpisodeGroups(chapters.toList(), Chapter::volume, Chapter::playable)

    @Test fun ordinaryEpisodesKeepSourceIdentityAndIndex() {
        val first = Chapter("第一集")
        val second = Chapter("第二集")
        val result = groups(first, second).single()
        assertNull(result.volume)
        assertSame(first, result.items[0].chapter)
        assertSame(second, result.items[1].chapter)
        assertEquals(listOf(0, 1), result.items.map { it.episodeIndex })
        assertEquals(listOf(0, 0), result.items.map { it.volumeIndex })
    }

    @Test fun browsingAnotherSeasonDoesNotChangeOriginalObjects() {
        val season1 = Chapter("第一季", true, false)
        val season2 = Chapter("第二季", true, false)
        val episode = Chapter("第二季 第一集")
        val result = groups(season1, Chapter("第一集"), Chapter("第二集"), season2, episode)
        assertSame(season1, result[0].volume)
        assertSame(season2, result[1].volume)
        assertSame(episode, result[1].items.single().chapter)
        assertEquals(1, result[1].items.single().volumeIndex)
        assertEquals(0, result[1].items.single().episodeIndex)
        assertEquals(2, result[0].items.size)
    }

    @Test fun descendingAndFilteringKeepPlaybackAddress() {
        val result = groups(Chapter("第一季", true), Chapter("第一集"), Chapter("第二集"),
            Chapter("第二季", true), Chapter("第一集"), Chapter("第二集"))
        val display = filterVideoEpisodes(result, 0, "第二集", true, Chapter::title).first()
        assertEquals(1, display.volumeIndex)
        assertEquals(1, display.episodeIndex)
    }

    @Test fun searchFindsOtherSeasonsAndClearReturnsToBrowsedSeason() {
        val result = groups(Chapter("第一季", true), Chapter("开场"),
            Chapter("第二季", true), Chapter("THE END"))
        val found = filterVideoEpisodes(result, 0, " end ", false, Chapter::title).single()
        assertEquals(1, found.volumeIndex)
        assertEquals(0, found.episodeIndex)
        assertEquals("开场", filterVideoEpisodes(result, 0, "", false, Chapter::title).single().chapter.title)
        assertTrue(filterVideoEpisodes(result, 0, "不存在", false, Chapter::title).isEmpty())
    }

    @Test fun searchByEpisodeNumberKeepsSeasonAddresses() {
        val result = groups(Chapter("第一季", true), Chapter("开场"),
            Chapter("第二季", true), Chapter("续篇"))
        val found = filterVideoEpisodes(result, 1, "1", false, Chapter::title)
        assertEquals(listOf(0, 1), found.map { it.volumeIndex })
        assertEquals(listOf(0, 0), found.map { it.episodeIndex })
    }

    @Test fun moviesWithOnlyPlayableLinesUseTheVolumeItself() {
        val line1 = Chapter("线路一", true)
        val line2 = Chapter("线路二", true)
        val result = groups(line1, line2)
        assertSame(line1, result[0].items.single().chapter)
        assertSame(line2, result[1].items.single().chapter)
        assertEquals(1, result[1].items.single().volumeIndex)
        assertEquals(0, result[1].items.single().episodeIndex)
    }

    @Test fun unplayableEmptyVolumeIsRetainedButCannotBeSelected() {
        val result = groups(Chapter("空季", true, false), Chapter("可播放季", true, false), Chapter("第一集"))
        assertEquals(2, result.size)
        assertTrue(result[0].items.isEmpty())
        assertEquals(1, result[1].items.single().volumeIndex)
    }

    @Test fun volumeWithChildrenNeverDuplicatesTheVolumeAsAnEpisode() {
        val volume = Chapter("线路", true)
        val result = groups(volume, Chapter("第一集"))
        assertEquals(1, result.single().items.size)
        assertFalse(result.single().items.any { it.chapter === volume })
    }

    @Test fun emptyCatalogHasAnEmptyGroup() {
        assertTrue(groups().single().items.isEmpty())
    }

    @Test fun largeCatalogAndDuplicateTitlesRetainDistinctAddresses() {
        val source = (0 until 1000).map { Chapter("同名剧集") }
        val result = videoEpisodeGroups(source, Chapter::volume, Chapter::playable).single().items
        assertEquals(1000, result.size)
        assertSame(source[999], result.last().chapter)
        assertEquals(999, result.last().episodeIndex)
        assertEquals(1000, result.map { it.volumeIndex to it.episodeIndex }.distinct().size)
    }
}
