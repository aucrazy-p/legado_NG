package io.legado.app.model.jsSource

import io.legado.app.exception.NoStackTraceException
import java.security.MessageDigest

/** A main script can reference its JSON-owned library without duplicating large resources. */
internal object JsSourceLibraryReference {

    const val SUPPORT_BINDING = "__legadoJsLibraryReferenceVersion"
    const val VERSION = 1
    private const val PREFIX = "legado.js.library.ref/1:"
    private val digestPattern = Regex("[0-9a-f]{64}")

    fun resolve(value: String, externalJsLib: String?): String {
        if (!value.startsWith("legado.js.library.ref/")) return value
        val expected = value.removePrefix(PREFIX)
        if (!value.startsWith(PREFIX) || !digestPattern.matches(expected)) {
            throw NoStackTraceException("外置 JS 库引用格式不支持")
        }
        if (externalJsLib.isNullOrBlank()) {
            throw NoStackTraceException("此 JS 书源依赖外置库，请导入完整 JSON 书源")
        }
        val actual = MessageDigest.getInstance("SHA-256")
            .digest(externalJsLib.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        if (actual != expected) {
            throw NoStackTraceException("外置 JS 库与主脚本不匹配，请重新导入完整 JSON 书源")
        }
        return externalJsLib
    }
}
