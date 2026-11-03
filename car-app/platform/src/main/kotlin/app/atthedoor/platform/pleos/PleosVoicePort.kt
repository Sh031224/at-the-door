package app.atthedoor.platform.pleos

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.Mode
import android.content.Context
import app.atthedoor.domain.port.VoicePort
import java.util.Locale

/** TextToSpeech SDK 3.3.1 어댑터. ON_DEVICE 모드(앱 등록 불필요). */
class PleosVoicePort(context: Context) : VoicePort {

    private val tts = TextToSpeech(context.applicationContext, Mode.ON_DEVICE).also {
        it.initialize()
        it.setLocale(Locale.KOREA)
    }

    override fun speak(text: String) {
        tts.speak(text, true)
    }

    fun release() = tts.release()
}
