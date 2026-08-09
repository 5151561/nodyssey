package io.github.bbs1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import io.github.plaza.designsys.theme.PlazaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as Bbs1App).container
        setContent {
            // Defaults only for now — system dark theme, brand palette. Theme settings arrive with a
            // settings screen, and reading them will follow `:app`: collect the SSOT here, keep no copy.
            PlazaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Bbs1AppUi(container)
                }
            }
        }
    }
}
