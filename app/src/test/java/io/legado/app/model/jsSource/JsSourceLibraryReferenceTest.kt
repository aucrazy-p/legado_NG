package io.legado.app.model.jsSource

import io.legado.app.exception.NoStackTraceException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class JsSourceLibraryReferenceTest {

    private val library = """{"format":"legado.js.library/1","scripts":["var LibraryModel = {version: 6};"],"resources":{"font-model":"字形库原始数据"},"builtins":[]}"""
    private val reference = "legado.js.library.ref/1:c1cc93e4f1303636e8f7b388b3445c087b31ba7efbf727608e2e20f0af8a8e52"

    @Test
    fun resolvesTheExactUtf8LibraryIncludingAllResources() {
        assertEquals(library, JsSourceLibraryReference.resolve(reference, library))
    }

    @Test
    fun missingLibraryAndChangedResourceAreRejected() {
        assertThrows(NoStackTraceException::class.java) {
            JsSourceLibraryReference.resolve(reference, null)
        }
        assertThrows(NoStackTraceException::class.java) {
            JsSourceLibraryReference.resolve(reference, library.replace("原始数据", "其它数据"))
        }
    }

    @Test
    fun unsupportedVersionAndMalformedDigestAreRejected() {
        for (invalid in listOf(reference.replace("ref/1:", "ref/2:"), "legado.js.library.ref/1:bad")) {
            assertThrows(NoStackTraceException::class.java) {
                JsSourceLibraryReference.resolve(invalid, library)
            }
        }
    }

    @Test
    fun inlineReplacementAndExplicitRemovalKeepTheirOriginalMeaning() {
        assertEquals("var Replacement = 1;", JsSourceLibraryReference.resolve("var Replacement = 1;", library))
        assertEquals("", JsSourceLibraryReference.resolve("", library))
    }
}
