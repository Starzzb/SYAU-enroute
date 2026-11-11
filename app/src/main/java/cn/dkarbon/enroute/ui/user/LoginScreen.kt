package cn.dkarbon.enroute.ui.user

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.ui.TimetableViewModel
import cn.dkarbon.enroute.ui.theme.InkTokens
import cn.dkarbon.enroute.ui.theme.Stroke

/**
 * 登录页（2026-11-10 主人要求：未登录状态点一下就能唤起）。
 *
 * 三条不变的红线：
 *   · 账号密码**只在这次登录时使用**，不写 Prefs、不写日志、不上传
 *   · 只在校内网可用（URP 是 10.2.0.47 内网地址）
 *   · 只读：只拉课表/考试/成绩，不调用任何写接口
 */
@Composable
fun LoginScreen(vm: TimetableViewModel, onDone: () -> Unit) {
    val t = InkTokens.current
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text("登录教务系统", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = t.ink)
        Spacer(Modifier.height(6.dp))
        Text(
            "仅校园网可用 · 密码只用一次，不保存",
            fontSize = 10.5.sp,
            lineHeight = 16.sp,
            color = t.inkFaint,
        )

        Spacer(Modifier.height(20.dp))
        Text("学号", fontSize = 10.5.sp, color = t.inkSoft)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = user,
            onValueChange = { user = it },
            singleLine = true,
            shape = RectangleShape,
            placeholder = { Text("例如 2024156048", fontSize = 12.sp, color = t.inkFaint) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))
        Text("密码", fontSize = 10.5.sp, color = t.inkSoft)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = pass,
            onValueChange = { pass = it },
            singleLine = true,
            shape = RectangleShape,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(20.dp))
        // 硬朗风主按钮：方角 + 实心农械橘
        Column(
            Modifier
                .fillMaxWidth()
                .background(if (user.isNotBlank() && pass.isNotBlank()) t.primary else t.cardAlt)
                .clickable(enabled = user.isNotBlank() && pass.isNotBlank()) {
                    vm.login(user.trim(), pass)
                    onDone()
                }
                .padding(vertical = 14.dp),
        ) {
            Text(
                "登录",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (user.isNotBlank() && pass.isNotBlank()) {
                    androidx.compose.ui.graphics.Color.White
                } else {
                    t.inkFaint
                },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        Spacer(Modifier.height(30.dp))
    }
}
