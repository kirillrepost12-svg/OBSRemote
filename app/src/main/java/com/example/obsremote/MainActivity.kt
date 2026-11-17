package com.example.obsremote

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF8AB4F8),
                    onPrimary = Color(0xFF0B1B33),
                    background = Color(0xFF0E0E13),
                    onBackground = Color(0xFFF1F0F5),
                    surface = Color(0xFF181821),
                    onSurface = Color(0xFFF1F0F5),
                    surfaceVariant = Color(0xFF22222E),
                    onSurfaceVariant = Color(0xFFA7A6B8),
                    error = Color(0xFFFF6B5E),
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ObsRemoteApp()
                }
            }
        }
    }
}
