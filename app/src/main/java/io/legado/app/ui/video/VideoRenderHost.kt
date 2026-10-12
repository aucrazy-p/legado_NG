package io.legado.app.ui.video

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.help.gsyVideo.VideoPlaybackBridge
import io.legado.app.help.gsyVideo.VideoPlaybackState
import io.legado.app.help.gsyVideo.BiliDanmukuParser
import io.legado.app.help.gsyVideo.DanmakuAdapter
import io.legado.app.model.VideoPlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import master.flame.danmaku.controller.DrawHandler
import master.flame.danmaku.danmaku.loader.android.DanmakuLoaderFactory
import master.flame.danmaku.danmaku.model.BaseDanmaku
import master.flame.danmaku.danmaku.model.DanmakuTimer
import master.flame.danmaku.danmaku.model.IDisplayer
import master.flame.danmaku.danmaku.model.android.DanmakuContext
import master.flame.danmaku.danmaku.model.android.SpannedCacheStuffer
import master.flame.danmaku.ui.widget.DanmakuView
import kotlin.math.abs

/** Only the output texture and danmaku renderer are Views; all controls live in Compose. */
@Composable
fun VideoRenderHost(owner: Any, state: VideoPlaybackState, danmakuVisible: Boolean,
                    modifier: Modifier = Modifier, allowDanmaku: Boolean = true) {
    Box(modifier) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
            TextureView(context).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    private var output: Surface? = null
                    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                        output = Surface(texture).also { VideoPlaybackBridge.attach(owner, it) }
                    }
                    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit
                    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                        output?.let { VideoPlaybackBridge.detach(owner, it); it.release() }
                        output = null
                        return true
                    }
                }
            }
        }, update = { texture ->
            val width = texture.width.toFloat()
            val height = texture.height.toFloat()
            if (width > 0 && height > 0) {
                val matrix = android.graphics.Matrix()
                matrix.postRotate(state.rotation.toFloat(), width / 2, height / 2)
                if (state.rotation % 180 != 0) matrix.postScale(width / height, height / width, width / 2, height / 2)
                texture.setTransform(matrix)
            }
        })
        if (allowDanmaku && (VideoPlay.danmakuFile != null || !VideoPlay.danmakuStr.isNullOrBlank())) {
            key(state.mediaRevision) { VideoDanmaku(state, danmakuVisible) }
        }
    }
}

@Composable
private fun VideoDanmaku(state: VideoPlaybackState, visible: Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val view = remember { DanmakuView(context) }
    val currentState by rememberUpdatedState(state)
    val currentVisible by rememberUpdatedState(visible)
    var prepared by remember { mutableStateOf(false) }
    DisposableEffect(view) { onDispose { view.release() } }
    LaunchedEffect(view) {
        val file = VideoPlay.danmakuFile
        val text = VideoPlay.danmakuStr
        val parser = withContext(Dispatchers.IO) {
            val loader = DanmakuLoaderFactory.create(DanmakuLoaderFactory.TAG_BILI)
            runCatching {
                if (file != null) loader.load(file.inputStream())
                else if (text != null && text.startsWith("http", true)) loader.load(text)
                else loader.load(text.orEmpty().byteInputStream())
                BiliDanmukuParser().apply { load(loader.dataSource) }
            }.getOrNull()
        } ?: return@LaunchedEffect
        val config = DanmakuContext.create()
            .setDanmakuStyle(IDisplayer.DANMAKU_STYLE_STROKEN, 3f)
            .setDuplicateMergingEnabled(false)
            .setScrollSpeedFactor(VideoPlay.danmakuSpeed)
            .setScaleTextSize(1f)
            .setCacheStuffer(SpannedCacheStuffer(), DanmakuAdapter(view))
            .setMaximumLines(hashMapOf(BaseDanmaku.TYPE_SCROLL_RL to 5))
            .preventOverlapping(hashMapOf(BaseDanmaku.TYPE_SCROLL_RL to true, BaseDanmaku.TYPE_FIX_TOP to true))
        view.setCallback(object : DrawHandler.Callback {
            override fun updateTimer(timer: DanmakuTimer?) = Unit
            override fun drawingFinished() = Unit
            override fun danmakuShown(danmaku: BaseDanmaku?) = Unit
            override fun prepared() {
                view.start(currentState.position)
                if (!currentState.playing) view.pause()
                if (!currentVisible) view.hide()
                prepared = true
            }
        })
        view.enableDanmakuDrawingCache(true)
        view.prepare(parser, config)
    }
    LaunchedEffect(prepared, state.playing, state.seekRevision, visible, state.position) {
        if (prepared) {
            if (visible) view.show() else view.hide()
            if (abs(view.currentTime - state.position) > 1000 || state.speed != 1f) view.seekTo(state.position)
            if (state.playing && !state.buffering) view.resume() else view.pause()
        }
    }
    LaunchedEffect(prepared, state.seekRevision) {
        if (prepared) view.seekTo(state.position)
    }
    AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
}
