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

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // 从设置页面返回后，重新检查权限状态
        val vm = viewModel<MainViewModel>()  // 这里可以这样调用，因为在 Composable 之外
        vm.checkStoragePermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PushGithubTheme {
                val viewModel: MainViewModel = viewModel()

                LaunchedEffect(Unit) {
                    viewModel.checkStoragePermission()
                }

                MainScreen(
                    viewModel = viewModel,
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
