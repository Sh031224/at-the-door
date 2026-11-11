package app.atthedoor.app

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import app.atthedoor.app.ui.TripScreen
import app.atthedoor.app.ui.theme.AtTheDoorTheme

class MainActivity : ComponentActivity() {

    private val viewModel: TripViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                TripViewModel(AppContainer(applicationContext, isFake = AppMode.isFake(applicationContext))) as T
        }
    }

    private val requestPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        askRuntimePermissions()
        setContent {
            AtTheDoorTheme {
                TripScreen(viewModel, onSwitchMode = ::switchMode)
            }
        }
    }

    /** 음성 입력(마이크)과 주차 위치 저장(FusedLocation)은 dangerous 권한이라 실행 중에 묻는다. 거절해도 정형 입력으로 끝까지 동작한다. */
    private fun askRuntimePermissions() {
        // 마이크는 ‘말하기’를 누를 때 묻는다(PlanningScreen). 여기서는 주차 위치 저장에 쓰는 위치 권한만 묻는다.
        val needed = listOf(AppMode.FUSED_LOCATION_PERMISSION)
            .filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) requestPermissions.launch(needed.toTypedArray())
    }

    /** 실제 SDK ↔ 모의 응답 모드 전환. ViewModel과 SDK 연결을 새로 만들기 위해 화면을 다시 연다. */
    private fun switchMode() {
        AppMode.setFake(this, !AppMode.isFake(this))
        finish()
        startActivity(Intent(this, MainActivity::class.java))
    }
}
