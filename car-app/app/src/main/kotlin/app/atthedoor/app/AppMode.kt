package app.atthedoor.app

import android.content.Context
import androidx.core.content.edit

/** 실제 Pleos SDK와 모의 응답 중 어느 쪽으로 동작할지. 기본값은 빌드 설정(atthedoor.useFakes)이다. */
object AppMode {
    const val FUSED_LOCATION_PERMISSION = "pleos.car.permission.FUSED_LOCATION"
    private const val PREFS = "app_mode"
    private const val KEY_FAKE = "fake"

    fun isFake(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_FAKE, BuildConfig.USE_FAKES)

    fun setFake(context: Context, fake: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_FAKE, fake) }
    }
}
