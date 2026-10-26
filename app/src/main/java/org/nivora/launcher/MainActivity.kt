package org.nivora.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Midnight = Color(0xFF10141D)
private val Cyan = Color(0xFF66D9E8)
private val Lavender = Color(0xFFC8B7FF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Cyan,
                    secondary = Lavender,
                    background = Midnight,
                    surface = Midnight,
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
                NivoraFoundationScreen()
            }
        }
    }
}

@Composable
private fun NivoraFoundationScreen() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Midnight
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Midnight)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "N",
                    color = Cyan,
                    fontSize = 76.sp,
                    fontWeight = FontWeight.Light
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Nivora",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "A calmer home for Android.",
                    color = Lavender,
                    fontSize = 15.sp
                )

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = "Clean foundation · Milestone 1",
                    color = Color.LightGray,
                    fontSize = 13.sp
                )
            }
        }
    }
}
