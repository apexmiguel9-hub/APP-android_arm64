package com.epai.oblender

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.epai.oblfiles.InstallOBLFiles

class LauncherActivity : ComponentActivity() {
    companion object {
        private const val TAG = "OBL.Launcher"
    }

    private var homePath: String = ""
    private var configPath: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")

        ScreenUtils.fullScreen(window)

        val fromIntent = intent
        if (fromIntent != null && fromIntent.hasExtra("HomePath")) {
            homePath = fromIntent.getStringExtra("HomePath") ?: ""
            configPath = fromIntent.getStringExtra("ConfigPath") ?: ""
            Log.d(TAG, "paths from intent: home=$homePath config=$configPath")
        } else {
            Log.d(TAG, "no intent extras, installing OBL files")
            val installOBLFiles = InstallOBLFiles()
            val oblFilePath = installOBLFiles.installOBLFiles(this)
            homePath = oblFilePath.mStringHomePath
            configPath = oblFilePath.mStringConfigPath
            Log.d(TAG, "installed paths: home=$homePath config=$configPath")
        }

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFFF9800),
                    secondary = Color(0xFF80CBC4),
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E),
                    onPrimary = Color.White,
                    onSecondary = Color.Black,
                    onBackground = Color.White,
                    onSurface = Color.White,
                )
            ) {
                LauncherScreen(
                    onLaunchBlender = { launchBlender() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
    }

    private fun launchBlender() {
        Log.d(TAG, "launchBlender() called")
        try {
            val intent = Intent(this, OBLNativeActivity::class.java).apply {
                putExtra("HomePath", homePath)
                putExtra("ConfigPath", configPath)
            }
            Log.d(TAG, "starting OBLNativeActivity...")
            startActivity(intent)
            Log.d(TAG, "startActivity returned")
        } catch (e: Exception) {
            Log.e(TAG, "launchBlender failed", e)
        }
    }
}

@Composable
private fun LauncherScreen(onLaunchBlender: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val pos = event.changes.firstOrNull()
                        if (pos != null) {
                            Log.d("OBL.Launcher.Touch", "x=${pos.position.x} y=${pos.position.y} pressed=${pos.pressed}")
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
        ) {
            Text(
                text = "Blender",
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFF9800),
                textAlign = TextAlign.Center,
            )
            Text(
                text = "on Android",
                fontSize = 22.sp,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 40.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E1E1E)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Blender 3.6.22",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "3D Creation Suite",
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = {
                    Log.d("OBL.Launcher", "Button clicked")
                    onLaunchBlender()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF9800),
                    contentColor = Color.White,
                ),
            ) {
                Text(
                    text = "Launch Blender",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
