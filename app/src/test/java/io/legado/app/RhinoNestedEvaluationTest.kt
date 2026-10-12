package io.legado.app

import com.script.ScriptBindings
import com.script.rhino.RhinoClassShutter
import com.script.rhino.RhinoContext
import com.script.rhino.RhinoScriptEngine
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Job
import kotlinx.coroutines.yield
import org.htmlunit.corejs.javascript.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RhinoNestedEvaluationTest {
    private fun library() = RhinoScriptEngine.getRuntimeScope(ScriptBindings()).also {
        RhinoScriptEngine.eval(
            """
            function readBinding() { return this.label; }
            function getSecretKey() { let {source} = this; return source.getLoginHeader(); }
            function readIndirect() { return (0,eval)('label'); }
            function readConstructed() { return Function('return label')(); }
            """.trimIndent(), it
        )
    }

    private fun outer(bridge: Bridge) = bridge.scope("outer")

    @Test
    fun nestedHeaderUsesItsOwnHostDespiteOuterSourceVariable() {
        for (compiled in listOf(false, true)) {
            val bridge = Bridge(library(), compiled)
            assertEquals("inner", RhinoScriptEngine.eval(
                "let source='番茄听书'; bridge.eval('inner','getSecretKey()')", outer(bridge)
            ))
        }
    }

    @Test
    fun compiledOuterAndInnerDoNotCapturePreviousBindings() {
        val bridge = Bridge(library(), true)
        val script = RhinoScriptEngine.compile(
            "readBinding()+'|'+bridge.eval('inner','readBinding()')+'|'+readBinding()"
        )
        assertEquals("outer|inner|outer", script.eval(bridge.scope("outer")))
        assertEquals("next|inner|next", script.eval(bridge.scope("next")))
    }

    @Test
    fun multipleNestedLevelsRestoreEachCaller() {
        val bridge = Bridge(library())
        assertEquals("middle|deep|middle|outer", RhinoScriptEngine.eval(
            """bridge.eval('middle', "readBinding()+'|'+bridge.eval('deep','readBinding()')+'|'+readBinding()")+'|'+readBinding()""",
            outer(bridge)
        ))
    }

    @Test
    fun innerFailureRestoresCallerScopeAndExecutionGate() {
        for (compiled in listOf(false, true)) {
            val bridge = Bridge(library(), compiled)
            assertEquals("outer|true|next", RhinoScriptEngine.eval(
                """
                try { bridge.eval('inner', "throw new Error('expected')"); } catch(e) {}
                readBinding()+'|'+bridge.isAllowed()+'|'+bridge.eval('next','getSecretKey()')
                """.trimIndent(), outer(bridge)
            ))
            assertNull(Context.getCurrentContext())
        }
    }

    @Test
    fun indirectEvalAndFunctionConstructorUseInnerRealmThenRestoreOuter() {
        val bridge = Bridge(library())
        assertEquals("inner|inner|outer|outer", RhinoScriptEngine.eval(
            """bridge.eval('inner', "readIndirect()+'|'+readConstructed()")+'|'+readIndirect()+'|'+readConstructed()""",
            outer(bridge)
        ))
    }

    @Test
    fun explicitReceiverIsNotReplacedByEvaluationScope() {
        val bridge = Bridge(library())
        assertEquals("explicit|outer", RhinoScriptEngine.eval(
            """bridge.eval('inner', "readBinding.call({label:'explicit'})")+'|'+readBinding()""",
            outer(bridge)
        ))
    }

    @Test
    fun innerGlobalWritesDoNotLeakIntoOuterOrSharedLibrary() {
        val shared = library()
        val bridge = Bridge(shared)
        assertEquals("inner|undefined|outer", RhinoScriptEngine.eval(
            """bridge.eval('inner', "globalThis.privateNote='inner';readBinding()")+'|'+typeof privateNote+'|'+readBinding()""",
            outer(bridge)
        ))
        assertEquals("undefined", RhinoScriptEngine.eval("typeof privateNote", bridge.scope("another")))
        assertEquals("undefined", RhinoScriptEngine.eval("typeof privateNote", shared))
    }

    @Test
    fun e4xNamespaceIsRestoredAfterNestedEvaluation() {
        val bridge = Bridge(library())
        assertEquals("urn:outer|urn:inner|urn:outer", RhinoScriptEngine.eval(
            """
            default xml namespace = 'urn:outer';
            var before = String(<item/>.namespace().uri);
            var inner = bridge.eval('inner', "default xml namespace='urn:inner';String(<item/>.namespace().uri)");
            before+'|'+inner+'|'+String(<item/>.namespace().uri)
            """.trimIndent(), outer(bridge)
        ))
    }

    @Test
    fun nestedEvaluationCannotExposeEngineGuardOrBypassBookSourceClassPolicy() {
        val bridge = Bridge(library(), true)
        val blocked = """
            (String(Packages.org.htmlunit.corejs.javascript.NestedScriptScope).indexOf('[JavaPackage ')===0)+'|'+
            (String(Packages.java.lang.Runtime).indexOf('[JavaPackage ')===0)+'|'+
            (String(Packages.android.webkit.CookieManager).indexOf('[JavaPackage ')===0)
        """.trimIndent()
        RhinoClassShutter.withBookSourceClassPolicy(true, "outer-source") {
            val scope = outer(bridge).apply { this["blockedScript"] = blocked }
            assertEquals("true|true|true", RhinoScriptEngine.eval(
                "bridge.eval('inner',blockedScript)", scope
            ))
            assertFalse(RhinoClassShutter.visibleToScripts("java.lang.Runtime"))
        }
        assertNull(Context.getCurrentContext())
    }

    @Test
    fun rawFunctionsRemainForbiddenAfterRootEvaluation() {
        val scope = outer(Bridge(library()))
        val function = RhinoScriptEngine.eval("(function(){return 'unauthorized'})", scope)
            as org.htmlunit.corejs.javascript.Function
        val cx = Context.enter() as RhinoContext
        try {
            assertFalse(cx.allowScriptRun)
            val error = runCatching { function.call(cx, scope, scope.globalThis, emptyArray()) }.exceptionOrNull()
            assertTrue(error?.message.orEmpty().contains("Not allow run script"))
            assertFalse(cx.allowScriptRun)
        } finally {
            Context.exit()
        }
    }

    @Test
    fun cancellationRestoresScopeAndClosesRootExecutionGate() {
        for (compiled in listOf(false, true)) {
            val bridge = Bridge(library(), compiled)
            val scope = outer(bridge)
            val error = runCatching {
                RhinoScriptEngine.eval("bridge.cancelled()", scope)
            }.exceptionOrNull()
            assertTrue(error != null)
            assertNull(Context.getCurrentContext())
            assertEquals("outer|inner", RhinoScriptEngine.eval(
                "readBinding()+'|'+bridge.eval('inner','getSecretKey()')", scope
            ))
            assertNull(Context.getCurrentContext())
        }
    }

    @Test
    fun suspendedScriptCanResumeAndEvaluateNestedHeader() = runBlocking {
        for (compiled in listOf(false, true)) {
            val bridge = Bridge(library(), compiled)
            val script = """
                let source='番茄听书';
                bridge.pause()+'|'+bridge.eval('inner','getSecretKey()')+'|'+readBinding()
            """.trimIndent()
            val result = if (compiled) RhinoScriptEngine.compile(script).evalSuspend(outer(bridge))
                else RhinoScriptEngine.evalSuspend(script, outer(bridge))
            assertEquals("resumed|inner|outer", result)
            assertNull(Context.getCurrentContext())
        }
    }

    class Header(private val label: String) {
        fun getLoginHeader() = label
    }

    class Bridge(private val shared: ScriptBindings, private val compiled: Boolean = false) {
        private val scripts = mutableMapOf<String, com.script.CompiledScript>()

        fun scope(label: String) = ScriptBindings().apply {
            this["source"] = Header(label)
            this["label"] = label
            this["bridge"] = this@Bridge
            chainTo(shared)
        }

        fun eval(label: String, script: String): Any? = if (compiled) {
            scripts.getOrPut(script) { RhinoScriptEngine.compile(script) }.eval(scope(label))
        } else RhinoScriptEngine.eval(script, scope(label))

        fun isAllowed() = (Context.getCurrentContext() as RhinoContext).allowScriptRun

        fun cancelled(): Any? {
            val job = Job().apply { cancel() }
            val script = "while(true) {}"
            return if (compiled) RhinoScriptEngine.compile(script).eval(scope("cancelled"), job)
                else RhinoScriptEngine.eval(script, scope("cancelled"), job)
        }

        fun pause(): Nothing {
            val pending = Context.getCurrentContext().captureContinuation()
            pending.applicationState = suspend { yield(); "resumed" }
            throw pending
        }
    }
}
