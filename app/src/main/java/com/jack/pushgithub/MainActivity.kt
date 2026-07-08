package com.jack.pushgithub

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import com.jack.pushgithub.ui.MainScreen
import com.jack.pushgithub.ui.theme.PushGithubTheme
import com.jack.pushgithub.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    // 使用 viewModels() 委托获取 ViewModel（非 Composable 方式）
    private val mainViewModel: MainViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        mainViewModel.checkStoragePermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PushGithubTheme {
                LaunchedEffect(Unit) {
                    mainViewModel.checkStoragePermission()
                }

                MainScreen(
                    viewModel = mainViewModel,
                    onRequestStoragePermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            intent.data = Uri.parse("package:$packageName")
                            requestPermissionLauncher.launch(intent)
                        }
                    }
                )
            }
        }
    }
}
