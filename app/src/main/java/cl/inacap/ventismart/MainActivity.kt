package cl.inacap.ventismart

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.inacap.ventismart.ui.VentiSmartApp
import cl.inacap.ventismart.ui.theme.VentiTheme
import cl.inacap.ventismart.vm.AppViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VentiTheme {
                val vm: AppViewModel = viewModel()
                VentiSmartApp(vm)
            }
        }
    }
}
