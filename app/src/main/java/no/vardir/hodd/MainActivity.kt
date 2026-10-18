package no.vardir.hodd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import no.vardir.hodd.ui.configureHoddSystemBars
import no.vardir.hodd.ui.HoddApp
import no.vardir.hodd.ui.HoddTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureHoddSystemBars()
        setContent { HoddTheme { HoddApp() } }
    }
}
