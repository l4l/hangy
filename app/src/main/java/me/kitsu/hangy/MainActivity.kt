package me.kitsu.hangy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import me.kitsu.hangy.ui.HangyApp
import me.kitsu.hangy.ui.theme.HangyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HangyTheme {
                HangyApp()
            }
        }
    }
}
