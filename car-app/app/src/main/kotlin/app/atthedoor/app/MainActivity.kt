package app.atthedoor.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import app.atthedoor.app.ui.TripScreen

class MainActivity : ComponentActivity() {

    private val container by lazy { AppContainer(this) }

    private val viewModel: TripViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = TripViewModel(container) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container.start()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                TripScreen(viewModel)
            }
        }
    }

    override fun onDestroy() {
        container.stop()
        super.onDestroy()
    }
}
