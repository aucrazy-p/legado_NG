package io.legado.app.ui.book.manga.config

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class MangaFooterConfig(
    @SerializedName("hideFooter") val hideFooter: Boolean = false,
    @SerializedName("left") val left: Int = MangaFooterContent.CHAPTER_NAME,
    @SerializedName("middle") val middle: Int = MangaFooterContent.IMAGE_NUMBER,
    @SerializedName("right") val right: Int = MangaFooterContent.TIME,
)

internal object MangaFooterContent {
    const val NONE = 0
    const val BOOK_NAME = 1
    const val CHAPTER_NAME = 2
    const val IMAGE_NUMBER = 3
    const val CHAPTER_NUMBER = 4
    const val PROGRESS = 5
    const val TIME = 6
    const val BATTERY = 7
    const val TIME_BATTERY = 8
}

/** 每项内容只占一个位置；留空可以同时用于多个位置。 */
internal fun MangaFooterConfig.withContent(position: Int, content: Int): MangaFooterConfig {
    require(position in 0..2)
    require(content in MangaFooterContent.NONE..MangaFooterContent.TIME_BATTERY)
    fun value(index: Int, current: Int) = when {
        index == position -> content
        content != MangaFooterContent.NONE && current == content -> MangaFooterContent.NONE
        else -> current
    }
    return copy(left = value(0, left), middle = value(1, middle), right = value(2, right))
}
