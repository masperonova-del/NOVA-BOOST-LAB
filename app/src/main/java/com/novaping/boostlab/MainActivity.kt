package com.novaping.boostlab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModelProvider

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val optimizerViewModel = ViewModelProvider(this)[OptimizerViewModel::class.java]
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF07111F),
                    surface = Color(0xFF101F32),
                    surfaceVariant = Color(0xFF172A42),
                    primary = Color(0xFF63B3FF),
                    secondary = Color(0xFF55E0B5),
                    onBackground = Color.White,
                    onSurface = Color.White,
                    onSurfaceVariant = Color(0xFF9CAFC5)
                )
            ) {
                OptimizerScreen(optimizerViewModel)
            }
        }
    }
}
