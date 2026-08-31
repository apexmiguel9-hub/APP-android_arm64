package com.epai.oblender

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.epai.oblfiles.InstallOBLFiles

class LauncherActivity : ComponentActivity() {
    companion object {
        private const val TAG = "OBL.Launcher"
    }

    private var homePath: String = ""
    private var configPath: String = ""
    private val pathsReady = mutableStateOf(false)

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        Log.d(TAG, "storage permission result received")
        onPermissionResult()
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        Log.d(TAG, "permission result: $it")
        onPermissionResult()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")

        ScreenUtils.fullScreen(window)

        val fromIntent = intent
        if (fromIntent != null && fromIntent.hasExtra("HomePath")) {
            homePath = fromIntent.getStringExtra("HomePath") ?: ""
            configPath = fromIntent.getStringExtra("ConfigPath") ?: ""
            pathsReady.value = true
            Log.d(TAG, "paths from intent: home=$homePath config=$configPath")
        }

        setContent {
            MainContent()
        }

        if (!pathsReady.value) {
            checkAndProceed()
        }
    }

    private fun hasPathsFromIntent(): Boolean {
        return pathsReady.value
    }

    private fun onPermissionResult() {
        if (checkStoragePermission()) {
            Log.d(TAG, "permission granted after request")
            installOBLFiles()
        } else {
            Log.d(TAG, "permission still denied after request")
            Toast.makeText(this, "Storage permission required", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkAndProceed() {
        if (checkStoragePermission()) {
            Log.d(TAG, "storage permission already granted")
            installOBLFiles()
        } else {
            Log.d(TAG, "requesting storage permission")
            requestStoragePermission()
        }
    }

    private fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:$packageName")
            storagePermissionLauncher.launch(intent)
        } else {
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    private fun installOBLFiles() {
        Log.d(TAG, "installing OBL files")
        try {
            val installOBLFiles = InstallOBLFiles()
            val oblFilePath = installOBLFiles.installOBLFiles(this)
            homePath = oblFilePath.mStringHomePath
            configPath = oblFilePath.mStringConfigPath
            pathsReady.value = true
            Log.d(TAG, "installed: home=$homePath config=$configPath")
        } catch (e: Exception) {
            Log.e(TAG, "installOBLFiles failed", e)
            Toast.makeText(this, "Failed to install files: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun launchBlender() {
        Log.d(TAG, "launchBlender() called, home='$homePath' config='$configPath'")
        if (!pathsReady.value) {
            Log.e(TAG, "paths not ready, cannot launch")
            Toast.makeText(this, "Still initializing...", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val intent = Intent(this, OBLNativeActivity::class.java).apply {
                putExtra("HomePath", homePath)
                putExtra("ConfigPath", configPath)
            }
            Log.d(TAG, "starting OBLNativeActivity...")
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "launchBlender failed", e)
        }
    }

    @Composable
    private fun MainContent() {
        val ready by pathsReady
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
                onLaunchBlender = { launchBlender() },
                pathsReady = ready
            )
        }
    }
}

@Composable
private fun LauncherScreen(onLaunchBlender: () -> Unit, pathsReady: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
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
                    if (!pathsReady) {
                        Text(
                            text = "Requesting storage permission...",
                            fontSize = 14.sp,
                            color = Color(0xFFFF9800),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = {
                    Log.d("OBL.Launcher", "Button clicked, pathsReady=$pathsReady")
                    onLaunchBlender()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = pathsReady,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF9800),
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFF555555),
                    disabledContentColor = Color(0xFF999999),
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
