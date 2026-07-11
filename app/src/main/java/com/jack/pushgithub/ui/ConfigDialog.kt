package com.jack.pushgithub.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.jack.pushgithub.network.GithubVerify




@Composable
fun ConfigDialog(
    title: String,
    negativeText: String,
    onNegative: () -> Unit,
    onPositive: (email: String, username: String, token: String) -> Unit,
    initialEmail: String = "",
    initialUsername: String = "",
    initialToken: String = ""
) {

    var email by remember { mutableStateOf(initialEmail) }
    var username by remember { mutableStateOf(initialUsername) }
    var token by remember { mutableStateOf(initialToken) }


    //新增
    var checking by remember {
        mutableStateOf(false)
    }

    var logs by remember {
        mutableStateOf("")
    }

    val clipboard =
        LocalClipboardManager.current


    var showResult by remember {
        mutableStateOf(false)
    }
    
    var resultText by remember {
        mutableStateOf("")
    }
    // 控制Token说明弹窗
    var showTokenHelp by remember {
        mutableStateOf(false)
    }


    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {

            Column(
                modifier = Modifier.padding(24.dp)
            ) {


                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )


                Spacer(
                    modifier = Modifier.height(16.dp)
                )


                BasicTextField(
                    value = email,
                    onValueChange = { email = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )


                Spacer(
                    modifier = Modifier.height(12.dp)
                )


                BasicTextField(
                    value = username,
                    onValueChange = { username = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )


                Spacer(
                    modifier = Modifier.height(12.dp)
                )


                BasicTextField(
                    value = token,
                    onValueChange = { token = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )


                // 新增按钮
                TextButton(
                    onClick = {
                        showTokenHelp = true
                    }
                ) {
                    Text("如何获取Token")
                }


                Spacer(
                    modifier = Modifier.height(16.dp)
                )


                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {

                    TextButton(
                        onClick = onNegative
                    ) {
                        Text(negativeText)
                    }


                    Button(
    onClick = {

        checking = true

        logs = "开始验证...\n"


        CoroutineScope(
            Dispatchers.Main
        ).launch {


            val result =
    GithubVerify.verify(
        username.trim(),
        email.trim(),
        token.trim()
    ){

        logs += "$it\n"

    }



if(result){

    logs += "\n====================\n"
    logs += "✅ 验证成功\n"
    logs += "====================\n"


    // 等待用户看到成功日志
    kotlinx.coroutines.delay(1000)


    // 自动关闭日志弹窗
    checking = false



}else{


    logs += "\n====================\n"
    logs += "❌ 验证失败\n"
    logs += "请检查配置信息是否正确\n"
    logs += "====================\n"


    //失败不自动关闭
    //用户点击空白关闭


}



resultText =
    if(result){

        "检验正确"

    }else{

        "检验失败，请检查配置信息是否正确"

    }


            showResult = true


            if(result){

                onPositive(
                    email.trim(),
                    username.trim(),
                    token.trim()
                )

            }

        }

    }
){

    Text("确定")

                    }
                }
            }
        }
    }



// Token获取说明弹窗
if(showTokenHelp){

    Dialog(
        onDismissRequest = {
            showTokenHelp=false
        }
    ){

        Card(
    modifier =
    Modifier
        .fillMaxWidth()
        .fillMaxHeight(0.85f)
        .padding(20.dp),

    shape =
    RoundedCornerShape(16.dp)
){

    Column(
                modifier =
                Modifier
                    .padding(24.dp)
                    
            ){


                Text(
                    "如何获取 GitHub Token",
                    style =
                    MaterialTheme.typography.titleLarge
                )


                Spacer(
                    Modifier.height(16.dp)
                )


                Column(
    modifier =
    Modifier
        .weight(1f)
        .verticalScroll(
            rememberScrollState()
        )
){

    Text(
        """
        📌 GitHub Token 获取步骤


        1️⃣ 登录 GitHub


        2️⃣ 点击右上角头像


        3️⃣ 进入 Settings（设置）


        4️⃣ 找到 Developer settings


        5️⃣ 点击 Personal access tokens


        6️⃣ 点击 Tokens (classic)


        7️⃣ 点击 Generate new token


        8️⃣ 选择 Generate new token (classic)


        9️⃣ 输入 GitHub 密码确认身份


        🔟 在 Note 中填写名称
           （名称可以随意填写）


        1️⃣1️⃣ 选择 Token 有效时间


        1️⃣2️⃣ 勾选 repo 权限


        1️⃣3️⃣ 点击 Generate token


        ⚠️ 注意：
        Token 生成后只显示一次，
        请及时复制保存。
        """.trimIndent()
    )

}

                Spacer(
                    Modifier.height(20.dp)
                )


                Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.End
){

    Button(
        onClick = {
            showTokenHelp = false
        }
    ){

        Text("知道了")

    }

}

            }   // Column结束

        }       // Card结束

    }           // Dialog结束

}               // if(showTokenHelp)结束


//验证日志弹窗
if(checking){

    Dialog(
        onDismissRequest = {

            if(!logs.contains("验证完成")){

                checking=false

            }

        }
    ){

        Card{

            Column(
                modifier =
                Modifier.padding(20.dp)
            ){

                Text("正在验证")


                Spacer(
                    Modifier.height(10.dp)
                )


                Text(logs)

Spacer(
    Modifier.height(20.dp)
)


Row(
    modifier =
    Modifier.fillMaxWidth(),

    horizontalArrangement =
    Arrangement.End
){

    Button(

        onClick = {

            clipboard.setText(
                AnnotatedString(logs)
            )

        }

    ){

        Text("复制日志")

    }


    Spacer(
        Modifier.width(10.dp)
    )


    Button(

        onClick = {

            checking = false

        }

    ){

        Text("返回")

    }

}

            }

        }

    }

}



//验证结果弹窗
if(showResult){

    AlertDialog(

        onDismissRequest = {
            showResult=false
        },


        title = {
            Text("验证结果")
        },


        text = {
            Text(resultText)
        },


        confirmButton = {

            TextButton(
                onClick={
                    showResult=false
                }
            ){

                Text("知道了")

            }

        }

    )

}


//关闭 ConfigDialog
}
