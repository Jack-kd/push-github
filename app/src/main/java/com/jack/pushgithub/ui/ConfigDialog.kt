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


                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                    },
                    label = {
                        Text("邮箱")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )


                Spacer(
                    modifier = Modifier.height(12.dp)
                )


                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                    },
                    label = {
                        Text("用户名")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )


                Spacer(
                    modifier = Modifier.height(12.dp)
                )


                OutlinedTextField(
                    value = token,
                    onValueChange = {
                        token = it
                    },
                    label = {
                        Text("Token")
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
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

                        logs += it + "\n"
    
                    }
    

    
                checking = false
    
    
    
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
                .padding(20.dp),

            shape =
            RoundedCornerShape(16.dp)
        ){

            Column(
                modifier =
                Modifier.padding(24.dp)
            ){


                Text(
                    "如何获取 GitHub Token",
                    style =
                    MaterialTheme.typography.titleLarge
                )


                Spacer(
                    Modifier.height(16.dp)
                )


                Text(
                    """
                    1. 登录 GitHub

                    2. 点击头像

                    3. Settings

                    4. Developer settings

                    5. Personal access tokens

                    6. 创建 Token

                    7. 勾选 repo 权限
                    """.trimIndent()
                )


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
                        onClick={
                            showTokenHelp=false
                        }
                    ){

                        Text("知道了")

                    }

                }


            }

        }

    }

}



//验证日志弹窗
if(checking){

    Dialog(
        onDismissRequest = {}
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
