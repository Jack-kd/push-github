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
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jack.pushgithub.ui.MainScreen
import com.jack.pushgithub.ui.theme.PushGithubTheme
import com.jack.pushgithub.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    // 将 ViewModel 声明为 Activity 的成员变量
    private lateinit var mainViewModel: MainViewModel

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // 从设置页面返回后，重新检查权限状态
        mainViewModel.checkStoragePermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 在 setContent 之前初始化 ViewModel
        mainViewModel = viewModel()

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
