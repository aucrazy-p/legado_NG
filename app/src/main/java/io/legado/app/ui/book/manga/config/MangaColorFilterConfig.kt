package io.legado.app.ui.book.manga.config

import io.legado.app.utils.GSON
import com.google.gson.annotations.SerializedName

data class MangaColorFilterConfig(
    @SerializedName("r") var r: Int = 0,
    @SerializedName("g") var g: Int = 0,
    @SerializedName("b") var b: Int = 0,
    @SerializedName("a") var a: Int = 0,
    @SerializedName("l") var l: Int = 0
) {
    fun toJson(): String {
        if (r == 0 && g == 0 && b == 0 && a == 0 && l == 0) {
            return ""
        }
        return GSON.toJson(this)
    }
}
