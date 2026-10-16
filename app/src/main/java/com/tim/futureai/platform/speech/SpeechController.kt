package com.tim.futureai.platform.speech

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Thin TextToSpeech adapter. It owns no chat state and reports only playback status/errors. */
class SpeechController(
    context: Context,
    private val onSpeakingChanged: (String?) -> Unit,
    private val onError: (String) -> Unit,
) {
    private lateinit var engine: TextToSpeech
    @Volatile private var ready = false
    @Volatile private var languageSupported = false
    @Volatile private var pending: Pair<String, String>? = null

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            if (status != TextToSpeech.SUCCESS) {
                onError("此设备无法初始化文字转语音")
                return@TextToSpeech
            }
            val result = engine.setLanguage(Locale.SIMPLIFIED_CHINESE)
            languageSupported = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            ready = true
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) { onSpeakingChanged(utteranceId) }
                override fun onDone(utteranceId: String?) { onSpeakingChanged(null) }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    onSpeakingChanged(null)
                    onError("朗读失败，请检查设备语音服务")
                }
            })
            pending?.also { pending = null; speak(it.first, it.second) }
        }
    }

    fun speak(id: String, text: String): Boolean {
        if (text.isBlank()) return false
        if (!ready) { pending = id to text; return true }
        if (!languageSupported) {
            onError("设备缺少中文语音数据，请在系统设置中安装语音服务")
            return false
        }
        engine.stop()
        onSpeakingChanged(id)
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), id)
        if (result == TextToSpeech.ERROR) {
            onSpeakingChanged(null)
            onError("无法开始朗读，请检查设备语音服务")
            return false
        }
        return true
    }

    fun stop() {
        pending = null
        if (ready) engine.stop()
        onSpeakingChanged(null)
    }

    fun shutdown() {
        pending = null
        if (::engine.isInitialized) engine.shutdown()
        ready = false
    }
}

object SpeechRecognitionMessages {
    const val ERROR_INSUFFICIENT_PERMISSIONS = 9

    fun failure(errorCode: Int?): String = when (errorCode) {
        ERROR_INSUFFICIENT_PERMISSIONS -> "语音识别权限被拒绝，请在系统语音服务中允许麦克风访问"
        else -> "没有识别到语音或设备不支持语音识别，请重试或直接输入文字"
    }
}
