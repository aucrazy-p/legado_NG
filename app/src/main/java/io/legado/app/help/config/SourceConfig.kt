package io.legado.app.help.config

import android.content.Context.MODE_PRIVATE
import androidx.core.content.edit
import io.legado.app.data.appDb
import splitties.init.appCtx

object SourceConfig {
    private val sp = appCtx.getSharedPreferences("SourceConfig", MODE_PRIVATE)
    fun setBookScore(origin: String, name: String, author: String, score: Int) {
        sp.edit {
            val preScore = getBookScore(origin, name, author)
            var newScore = score
            if (preScore != 0) {
                newScore = score - preScore
            }

            putInt(origin, getSourceScore(origin) + newScore)

            putInt("${origin}_${name}_${author}", score)
        }
    }

    fun getBookScore(origin: String, name: String, author: String): Int {
        return sp.getInt("${origin}_${name}_${author}", 0)
    }

    fun getSourceScore(origin: String): Int {
        return sp.getInt(origin, 0)
    }


    fun removeSource(origin: String) {
        removeSources(listOf(origin))
    }

    fun removeSources(origins: Collection<String>) {
        if (origins.isEmpty()) return
        matchingSourceConfigKeys(sp.all.keys, origins, appDb.cacheDao.sourceKeys()).let { keys ->
            sp.edit {
                keys.forEach { key ->
                    remove(key)
                }
            }
        }
    }


}

internal fun matchingSourceConfigKeys(
    keys: Collection<String>,
    origins: Collection<String>,
    retainedOrigins: Collection<String>,
): List<String> {
    fun String.belongsTo(origin: String) = this == origin || startsWith("${origin}_")
    return keys.filter { key ->
        origins.any(key::belongsTo) && retainedOrigins.none(key::belongsTo)
    }
}
