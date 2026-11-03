package app.atthedoor.platform.pleos

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import app.atthedoor.domain.port.SpeechPort
import app.atthedoor.domain.port.SpeechText
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Locale

/**
 * SpeechToText SDK 3.3.1 어댑터.
 * ON_DEVICE 모드를 쓴다. HYBRID·SERVER는 Pleos Playground My Project 자격 증명으로 registerApp 이 필요하다.
 * 이 SDK는 인식 텍스트만 돌려주고 명령을 실행하지 않는다. 조건 추출은 앱이 한다.
 */
class PleosSpeechPort(context: Context) : SpeechPort {

    private val stt = SpeechToText(context.applicationContext, Mode.ON_DEVICE).also {
        it.initialize()
        it.setLocale(Locale.KOREA)
    }

    class SpeechException(val type: ErrorType) : Exception("STT error: $type")

    override fun listen(): Flow<SpeechText> = callbackFlow {
        val listener = object : ResultListener {
            override fun onUpdated(stt: String, completed: Boolean) {
                trySend(SpeechText(stt, completed))
                if (completed) close()
            }

            override fun onError(errorType: ErrorType) {
                close(SpeechException(errorType))
            }

            override fun onUpdatedEpdData(p0: Long, p1: Long) = Unit
            override fun onStartedRecognition() = Unit
            override fun onEndedRecognition() = Unit
            override fun onReady() = Unit
            override fun onUpdatedRms(rms: Double) = Unit
        }
        stt.addListener(listener)
        stt.request()
        awaitClose {
            stt.stop()
            stt.removeListener(listener)
        }
    }

    override fun stop() = stt.stop()

    fun release() = stt.release()
}
