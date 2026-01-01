package com.example.open_autoglm_android.ui.screen

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.provider.Settings.Secure
import android.widget.Toast
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.open_autoglm_android.data.InputMode
import com.example.open_autoglm_android.ui.viewmodel.SettingsViewModel
import com.example.open_autoglm_android.util.AuthHelper
import kotlin.math.roundToInt

// 网络服务相关接口和类（顶层定义）
interface ApiService {
    @POST("/api/check_member")
    suspend fun checkMember(
        @Body request: MemberCheckRequest,
        @Header("Authorization") authorization: String
    ): Response<MemberCheckResponse>
}

// 请求数据类
data class MemberCheckRequest(
    val username: String,
    val device_id: String
)

// 响应数据类
data class MemberCheckResponse(
    val code: Int,
    val message: String,
    val data: ResponseData? = null
)

data class ResponseData(
    val is_member: Boolean,
    val is_bound: Boolean,
    val is_same_device: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(),
    onNavigateToAdvancedAuth: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val hasWriteSecureSettings = remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val androidId = remember { mutableStateOf<String?>(null) }
    val phoneNumber = remember { mutableStateOf("") }
    val bindingResult = remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // 创建OkHttp客户端，添加日志拦截器
    val okHttpClient = remember {
        val logging = HttpLoggingInterceptor()
        logging.level = HttpLoggingInterceptor.Level.BODY
        
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // 创建Retrofit实例
    val retrofit = remember {
        Retrofit.Builder()
            .baseUrl("http://192.168.31.167:5500")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val apiService = remember { retrofit.create(ApiService::class.java) }

    // 绑定按钮点击事件
    fun onBindClick() {
        scope.launch {
            val phone = phoneNumber.value.trim()
            val androidIdValue = androidId.value

            if (phone.isEmpty() || androidIdValue.isNullOrEmpty()) {
                bindingResult.value = "请输入手机号并确保已获取Android ID"
                return@launch
            }

            try {
                val response = withContext(Dispatchers.IO) {
                    apiService.checkMember(
                        MemberCheckRequest(phone, androidIdValue),
                        "Bearer 123456qazplm"
                    )
                }

                // 根据HTTP状态码显示友好提示词
                if (response.isSuccessful) {
                    val body = response.body()
                    when (body?.code) {
                        200 -> {
                            bindingResult.value = "绑定成功"
                            // 保存手机号到本地存储
                            viewModel.saveBoundPhoneNumber(phone)
                        }
                        400 -> bindingResult.value = "请检查输入的手机号是否正确"
                        401 -> bindingResult.value = "身份验证失败，请联系管理员"
                        403 -> bindingResult.value = "您当前没有权限进行此操作"
                        404 -> bindingResult.value = "未找到该用户信息"
                        500 -> bindingResult.value = "服务器繁忙，请稍后再试"
                        else -> bindingResult.value = body?.message ?: "绑定成功"
                    }
                } else {
                    // 处理HTTP错误码
                    bindingResult.value = when (response.code()) {
                        400 -> "请检查输入的手机号是否正确"
                        401 -> "身份验证失败，请联系管理员"
                        403 -> "您当前没有权限进行此操作"
                        404 -> "未找到该用户信息"
                        500 -> "服务器繁忙，请稍后再试"
                        else -> "绑定失败，错误码：${response.code()}"
                    }
                    Log.e("SettingsScreen", "HTTP错误: ${response.code()} ${response.message()}")
                }
            } catch (e: Exception) {
                // 添加详细日志以便定位错误
                Log.e("SettingsScreen", "网络连接失败: ${e.message}", e)
                bindingResult.value = "网络连接失败，请检查网络设置后重试"
            }
        }
    }

    // 加载已绑定的手机号
    LaunchedEffect(Unit) {
        // 应用启动时，如果有已绑定的手机号，自动填充到输入框
        if (uiState.boundPhoneNumber?.isNotEmpty() == true) {
            phoneNumber.value = uiState.boundPhoneNumber.orEmpty()
        }
    }

    DisposableEffect(Unit) {
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasWriteSecureSettings.value = AuthHelper.hasWriteSecureSettingsPermission(context)
                viewModel.checkAccessibilityService()
                viewModel.checkOverlayPermission()
                viewModel.checkImeStatus()
                
                // 检查Android版本是否为10或更高
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // 获取Android ID
                    androidId.value = Secure.getString(context.contentResolver, Secure.ANDROID_ID)
                } else {
                    // 版本低于Android 10，显示提示
                    Toast.makeText(
                        context,
                        "系统不支持，仅支持安卓10+请升级操作系统版本",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        }
    }

    Scaffold(
        
    ) { paddingValues ->
        Column(
            modifier = modifier
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 无障碍服务状态
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (uiState.isAccessibilityEnabled) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    }
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "无障碍服务", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = if (uiState.isAccessibilityEnabled) "已启用" else "未启用 - 点击前往设置",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (!uiState.isAccessibilityEnabled) {
                            Button(onClick = {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            }) { Text("前往设置") }
                        }
                    }
                }
            }
 
 

            Divider()

            // 实验型功能
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Science,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "实验型功能", style = MaterialTheme.typography.titleMedium)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "图片压缩", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = "发送给模型前压缩图片，减少流量消耗和延迟",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(
                            checked = uiState.imageCompressionEnabled,
                            onCheckedChange = { viewModel.setImageCompressionEnabled(it) }
                        )
                    }

                    if (uiState.imageCompressionEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "压缩级别: ${uiState.imageCompressionLevel}%",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = uiState.imageCompressionLevel.toFloat(),
                            onValueChange = { viewModel.setImageCompressionLevel(it.roundToInt()) },
                            valueRange = 10f..100f,
                            steps = 8
                        )
                    }
                }
            }

            Divider()

            // Android ID显示功能
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Science,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Android ID", style = MaterialTheme.typography.titleMedium)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    androidId.value?.let { id ->
                        Text(
                            text = "ID: $id",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Divider()

            // 手机号绑定功能
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Science,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "绑定购买的手机号", style = MaterialTheme.typography.titleMedium)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    TextField(
                        value = phoneNumber.value,
                        onValueChange = { phoneNumber.value = it },
                        placeholder = { Text("请输入购买的手机号") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            disabledContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    // 显示已绑定的手机号（如果有）
                    uiState.boundPhoneNumber?.let { boundPhone ->
                        if (boundPhone.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "已绑定手机号: $boundPhone",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "限绑定1台安卓设备,若修改设备请联系管理员",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { onBindClick() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "绑定")
                    }

                    bindingResult.value?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (it.contains("成功") || it.contains("Success")) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    }
                }
            }

            Divider()

            uiState.saveSuccess?.let {
                if (it) {
                   
                }
            }
            uiState.error?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = error); TextButton(onClick = { viewModel.clearError() }) {
                        Text(
                            "关闭"
                        )
                    }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            
        }
    }

}
