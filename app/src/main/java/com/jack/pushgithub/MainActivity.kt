package com.jack.pushgithub

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import com.jack.pushgithub.ui.MainScreen
import com.jack.pushgithub.ui.theme.PushGithubTheme
import com.jack.pushgithub.viewmodel.MainViewModel
import java.io.PrintWriter
import java.io.StringWriter

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        mainViewModel.checkStoragePermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 设置全局未捕获异常处理器，防止应用直接闪退
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val stackTrace = sw.toString()
            Log.e("PushGithub", "未捕获异常: $stackTrace")
            // 把异常信息写入 ViewModel 的日志中
            mainViewModel.addLog("❌ 应用崩溃: ${throwable.message}")
            mainViewModel.addLog(stackTrace)
            // 延迟一下，确保日志有机会更新到界面
            Thread.sleep(500)
            // 仍然退出应用，但至少日志会保留在界面上（可在下次打开时查看）
            finish()
        }

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
