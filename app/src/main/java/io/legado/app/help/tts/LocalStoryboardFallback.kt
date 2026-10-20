package io.legado.app.help.tts

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookCharacter
import io.legado.app.help.ai.AiTtsStoryboardHelper
import io.legado.app.ui.book.character.ChapterStoryboard
import io.legado.app.ui.book.character.StoryboardScene
import io.legado.app.ui.book.character.StoryboardSegment
import io.legado.app.ui.book.character.StoryboardSegmentType

/**
 * 即时起播用的本地规则分镜：只用引号 / 冒号等纯正则判断哪一段是对白、哪一段是心声，
 * 不产生任何网络请求，也绝不猜说话人名字（猜错会把段落绑到错误角色上）。
 *
 * 产出的分镜只用于「首声先出来」这一段窗口期：真分镜生成完成后由
 * HttpReadAloudService 按段落边界接管后续段落，因此这里只求快和不出错。
 */
internal object LocalStoryboardFallback {

    private val femaleCues = listOf(
        "她", "姑娘", "小姐", "夫人", "女子", "女人", "女声", "女郎", "丫头", "少女"
    )
    private val maleCues = listOf(
        "他", "公子", "先生", "少年", "男子", "男人", "男声", "汉子", "老者", "老头"
    )
    private const val GENDER_WINDOW = 24

    fun build(
        book: Book,
        chapterIndex: Int,
        chapterTitle: String,
        content: String,
        characters: List<BookCharacter>
    ): ChapterStoryboard? {
        val paragraphs = AiTtsStoryboardHelper.paragraphsFromContent(content)
        if (paragraphs.isEmpty()) return null
        val segments = arrayListOf<StoryboardSegment>()
        paragraphs.forEachIndexed { index, text ->
            segments += segmentsForParagraph(index, text)
        }
        if (segments.isEmpty()) return null
        val scene = StoryboardScene(
            index = 0,
            title = chapterTitle.ifBlank { book.name.orEmpty() },
            summary = "本地规则预分镜",
            characters = characters.filter { it.enabled && it.name.isNotBlank() }
                .map { it.name },
            segments = segments,
            contextText = ""
        )
        return ChapterStoryboard(
            chapterTitle = chapterTitle,
            scenes = listOf(scene),
            sourceChapterIndex = chapterIndex
        )
    }

    private fun segmentsForParagraph(
        paragraphIndex: Int,
        text: String
    ): List<StoryboardSegment> {
        if (text.isBlank()) return emptyList()
        val spans = AiTtsStoryboardHelper.localTextSpans(text)
            .filter { it.end > it.start && it.start >= 0 && it.end <= text.length }
        if (spans.isEmpty()) {
            return listOf(narrationSegment(paragraphIndex, text, 0, text.length))
        }
        val result = arrayListOf<StoryboardSegment>()
        var cursor = 0
        spans.forEach { span ->
            val start = maxOf(span.start, cursor)
            if (start <= cursor && span.end <= cursor) return@forEach
            if (start > cursor) {
                result += narrationSegment(paragraphIndex, text, cursor, start)
            }
            val type = when (span.roleHint) {
                "thought" -> StoryboardSegmentType.THOUGHT
                "narrator" -> StoryboardSegmentType.NARRATION
                else -> StoryboardSegmentType.DIALOGUE
            }
            result += StoryboardSegment(
                type = type,
                paragraphIndex = paragraphIndex,
                text = text.substring(start, span.end),
                speakerName = null,
                evidence = "本地规则",
                speakerGender = if (type == StoryboardSegmentType.NARRATION) {
                    StoryboardSegment.SpeakerGender.UNKNOWN
                } else {
                    inferGender(text, start, span.end)
                },
                start = start,
                end = span.end
            )
            cursor = span.end
        }
        if (cursor < text.length) {
            result += narrationSegment(paragraphIndex, text, cursor, text.length)
        }
        return result.filter { it.end > it.start && it.text.isNotBlank() }
    }

    private fun narrationSegment(
        paragraphIndex: Int,
        text: String,
        start: Int,
        end: Int
    ): StoryboardSegment {
        return StoryboardSegment(
            type = StoryboardSegmentType.NARRATION,
            paragraphIndex = paragraphIndex,
            text = text.substring(start, end),
            speakerName = null,
            evidence = "旁白",
            start = start,
            end = end
        )
    }

    /**
     * 只看紧邻引号前的一小段提示语，男女线索同时出现或都没有时保持未知，
     * 交由引擎的默认对白音色兜底，宁可不猜也不乱猜。
     */
    private fun inferGender(text: String, start: Int, end: Int): String {
        val before = text.substring(maxOf(0, start - GENDER_WINDOW), start)
        val female = femaleCues.any { before.contains(it) }
        val male = maleCues.any { before.contains(it) }
        return when {
            female && !male -> StoryboardSegment.SpeakerGender.FEMALE
            male && !female -> StoryboardSegment.SpeakerGender.MALE
            else -> StoryboardSegment.SpeakerGender.UNKNOWN
        }
    }
}
