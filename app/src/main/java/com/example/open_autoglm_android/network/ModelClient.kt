
package com.example.open_autoglm_android.network

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.open_autoglm_android.network.dto.ChatMessage as NetworkChatMessage
import com.example.open_autoglm_android.network.dto.ChatRequest
import com.example.open_autoglm_android.network.dto.ChatResponse
import com.example.open_autoglm_android.network.dto.ContentItem
import com.example.open_autoglm_android.network.dto.ImageUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

data class ModelResponse(
    val thinking: String,
    val action: String
)

class ModelClient(
    private val baseUrl: String,
    private val apiKey: String
) {
    private val TAG = "ModelClient"
    private val api: AutoGLMApi
    private val json = com.google.gson.Gson()
    private val finalBaseUrl: String
    
    init {
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            Log.d("ModelClient-OkHttp", message)
        }.apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
        
        // 确保URL以斜杠结尾
        this.finalBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        
        val retrofit = Retrofit.Builder()
            .baseUrl(this.finalBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        api = retrofit.create(AutoGLMApi::class.java)
        Log.d(TAG, "ModelClient初始化完成，baseUrl: ${this.finalBaseUrl}")
    }

    /**
     * 请求模型（使用消息上下文）
     */
    suspend fun request(
        messages: List<NetworkChatMessage>,
        modelName: String,
        apiKey: String
    ): ModelResponse {
        Log.d(TAG, "开始执行request请求，模型: $modelName, 消息数量: ${messages.size}")
        
        val request = ChatRequest(
            model = modelName,
            messages = messages,
            maxTokens = 3000,
            temperature = 0.0,
            topP = 0.85,
            frequencyPenalty = 0.2,
            stream = false
        )
        
        val authHeader = if (apiKey.isNotEmpty() && apiKey != "EMPTY") {
            "Bearer $apiKey"
        } else {
            "Bearer EMPTY"
        }
        
        try {
            Log.d(TAG, "发送API请求到: ${finalBaseUrl}chat/completions")
            Log.d(TAG, "请求模型: $modelName")
            Log.d(TAG, "请求消息数量: ${messages.size}")
            val response = api.chatCompletion(authHeader, request)
            
            Log.d(TAG, "API响应状态码: ${response.code()}, 消息: ${response.message()}")
            
            if (response.isSuccessful && response.body() != null) {
                val responseBody = response.body()!!
                val content = responseBody.choices.firstOrNull()?.message?.content ?: ""
                Log.d(TAG, "API请求成功，返回内容长度: ${content.length}")
                return parseResponse(content)
            } else {
                // 尝试获取详细的错误信息
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "API请求失败: ${response.code()} ${response.message()}, 错误详情: $errorBody")
                throw Exception("API request failed: ${response.code()} ${response.message()} - $errorBody")
            }
        } catch (e: Exception) {
            Log.e(TAG, "API请求异常: ${e.message}", e)
            throw e
        }
    }

    /**
     * 创建系统消息
     */
    fun createSystemMessage(): NetworkChatMessage {
        val systemContent = """
你是一个智能Android辅助助手，能够通过分析屏幕截图和用户指令来执行各种任务。你的目标是帮助用户完成他们的请求，通过分析当前屏幕状态并执行适当的操作。

核心指令：
- 你可以通过do(action=...)格式的指令与系统交互
- 每次操作后，你会收到新的屏幕截图
- 任务完成后，使用finish(message="xxx")格式结束任务

可用操作：
- do(action="Launch", package_name="com.example.app") - 启动指定应用
- do(action="Tap", element=[x,y]) - 点击屏幕上的特定位置
- do(action="Type", text="xxx") - 输入文本
- do(action="Swipe", start=[x1,y1], end=[x2,y2]) - 滑动屏幕
- do(action="Back") - 返回上一页
- do(action="Home") - 返回主屏幕
- do(action="Wait", duration="x seconds") - 等待指定时间
- finish(message="xxx") - 完成任务

请保持回复简洁明了，专注于完成任务所需的操作。
        """.trimIndent()
        
        return NetworkChatMessage(
            role = "system",
            content = listOf(ContentItem(type = "text", text = systemContent))
        )
    }
    
    /**
     * 创建用户消息（第一次调用，包含原始任务）
     */
    fun createUserMessage(userPrompt: String, screenshot: Bitmap?, currentApp: String?): NetworkChatMessage {
        val userContent = mutableListOf<ContentItem>()
        val screenInfoJson = buildScreenInfo(currentApp)
        val textContent = "$userPrompt\n\n$screenInfoJson"

        // 对齐旧项目：先放图片，再放文本
        screenshot?.let { bitmap ->
            val base64Image = bitmapToBase64(bitmap)
            userContent.add(
                ContentItem(
                    type = "image_url",
                    imageUrl = ImageUrl(url = "data:image/jpeg;base64,$base64Image")
                )
            )
        }

        userContent.add(ContentItem(type = "text", text = textContent))

        return NetworkChatMessage(role = "user", content = userContent)
    }
    
    /**
     * 创建屏幕信息消息（后续调用，只包含屏幕信息）
     */
    fun createScreenInfoMessage(screenshot: Bitmap?, currentApp: String?): NetworkChatMessage {
        val userContent = mutableListOf<ContentItem>()
        val screenInfoJson = buildScreenInfo(currentApp)
        val textContent = "** Screen Info **\n\n$screenInfoJson"

        // 对齐旧项目：先放图片，再放文本
        screenshot?.let { bitmap ->
            val base64Image = bitmapToBase64(bitmap)
            userContent.add(
                ContentItem(
                    type = "image_url",
                    imageUrl = ImageUrl(url = "data:image/jpeg;base64,$base64Image")
                )
            )
        }

        userContent.add(ContentItem(type = "text", text = textContent))

        return NetworkChatMessage(role = "user", content = userContent)
    }
    
    /**
     * 构建屏幕信息（与旧项目保持一致，返回 JSON 字符串，如 {"current_app": "System Home"}）
     */
    private fun buildScreenInfo(currentApp: String?): String {
        val appName = currentApp ?: "Unknown"
        return """
        {
            "screen_info": {
                "current_app": "$appName"
            }
        }
        """.trimIndent()
    }
    
    /**
     * 从消息中移除图片内容，只保留文本（节省 token）
     * 参考原项目的 MessageBuilder.remove_images_from_message
     */
    fun removeImagesFromMessage(message: NetworkChatMessage): NetworkChatMessage {
        val textOnlyContent = message.content.filter { it.type == "text" }
        return NetworkChatMessage(
            role = message.role,
            content = textOnlyContent
        )
    }
    
    private fun bitmapToBase64(bitmap: Bitmap): String {
        // 首先减小图片尺寸，降低分辨率
        val maxDimension = 800 // 最大边长设为800像素
        var width = bitmap.width
        var height = bitmap.height
        
        if (width > height && width > maxDimension) {
            height = (height * maxDimension / width)
            width = maxDimension
        } else if (height > maxDimension) {
            width = (width * maxDimension / height)
            height = maxDimension
        }
        
        // 创建缩放后的 bitmap
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)
        
        val outputStream = ByteArrayOutputStream()
        // 进一步降低压缩质量
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
        val byteArray = outputStream.toByteArray()
        
        // 添加日志，监控图片大小
        Log.d(TAG, "图片压缩后大小: ${byteArray.size / 1024} KB, 尺寸: ${width}x${height}")
        
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
    
    private fun buildSystemPrompt(): String {
        // 对齐 Python 原项目的中文提示词，保持操作定义和规则一致
        return """
今天的日期是: ${java.time.LocalDate.now()}
你是一个智能体分析专家，可以根据操作历史和当前状态图执行一系列操作来完成任务。
你必须严格按照要求输出以下格式：
<think>{think}</think>
<answer>{action}</answer>

其中：
- {think} 是对你为什么选择这个操作的简短推理说明。
- {action} 是本次执行的具体操作指令，必须严格遵循下方定义的指令格式，不能输出其他格式（如[{'Type", text="西装")，这是严重的错误，会导致任务失败）。

操作指令及其作用如下：
- do(action="Launch", app="xxx")  
    Launch是启动目标app的操作，这比通过主屏幕导航更快。此操作完成后，您将自动收到结果状态的截图。
- do(action="Tap", element=[x,y])  
    Tap是点击操作，点击屏幕上的特定点。可用此操作点击按钮、选择项目、从主屏幕打开应用程序，或与任何可点击的用户界面元素进行交互。坐标系统从左上角 (0,0) 开始到右下角（999,999)结束。此操作完成后，您将自动收到结果状态的截图。
- do(action="Tap", element=[x,y], message="重要操作")  
    基本功能同Tap，点击涉及财产、支付、隐私等敏感按钮时触发。
- do(action="Type", text="xxx")  
    Type是输入操作，在当前聚焦的输入框中输入文本。使用此操作前，请确保输入框已被聚焦（先点击它）。输入的文本将像使用键盘输入一样输入。重要提示：手机可能正在使用 ADB 键盘，该键盘不会像普通键盘那样占用屏幕空间。要确认键盘已激活，请查看屏幕底部是否显示 'ADB Keyboard {ON}' 类似的文本，或者检查输入框是否处于激活/高亮状态。不要仅仅依赖视觉上的键盘显示。自动清除文本：当你使用输入操作时，输入框中现有的任何文本（包括占位符文本和实际输入）都会在输入新文本前自动清除。你无需在输入前手动清除文本——直接使用输入操作输入所需文本即可。操作完成后，你将自动收到结果状态的截图。
- do(action="Type_Name", text="xxx")  
    Type_Name是输入人名的操作，基本功能同Type。
- do(action="Interact")  
    Interact是当有多个满足条件的选项时而触发的交互操作，询问用户如何选择。
- do(action="Swipe", start=[x1,y1], end=[x2,y2])  
    Swipe是滑动操作，通过从起始坐标拖动到结束坐标来执行滑动手势。可用于滚动内容、在屏幕之间导航、下拉通知栏以及项目栏或进行基于手势的导航。坐标系统从左上角 (0,0) 开始到右下角（999,999)结束。滑动持续时间会自动调整以实现自然的移动。此操作完成后，您将自动收到结果状态的截图。
- do(action="Note", message="True")  
    记录当前页面内容以便后续总结。
- do(action="Call_API", instruction="xxx")  
    总结或评论当前页面或已记录的内容。
- do(action="Long Press", element=[x,y])  
    Long Pres是长按操作，在屏幕上的特定点长按指定时间。可用于触发上下文菜单、选择文本或激活长按交互。坐标系统从左上角 (0,0) 开始到右下角（999,999)结束。此操作完成后，您将自动收到结果状态的屏幕截图。
- do(action="Double Tap", element=[x,y])  
    Double Tap在屏幕上的特定点快速连续点按两次。使用此操作可以激活双击交互，如缩放、选择文本或打开项目。坐标系统从左上角 (0,0) 开始到右下角（999,999)结束。此操作完成后，您将自动收到结果状态的截图。
- do(action="Take_over", message="xxx")  
    Take_over是接管操作，表示在登录和验证阶段需要用户协助。
- do(action="Back")  
    导航返回到上一个屏幕或关闭当前对话框。相当于按下 Android 的返回按钮。使用此操作可以从更深的屏幕返回、关闭弹出窗口或退出当前上下文。此操作完成后，您将自动收到结果状态的截图。
- do(action="Home") 
    Home是回到系统桌面的操作，相当于按下 Android 主屏幕按钮。使用此操作可退出当前应用并返回启动器，或从已知状态启动新任务。此操作完成后，您将自动收到结果状态的截图。
- do(action="Wait", duration="x seconds")  
    等待页面加载，x为需要等待多少秒。
- finish(message="xxx")  
    finish是结束任务的操作，表示准确完整完成任务，message是终止信息。 

必须遵循的规则：
1. 【任务持续性】在执行任务过程中，始终记住用户的原始任务目标，不要偏离目标执行无关操作。
2. 【任务持续性】不要随意切换到其他应用，除非这是完成任务的必要步骤。如果需要切换应用，必须在思考中明确说明为什么需要切换。
3. 【任务持续性】当收到"继续执行任务"提示时，继续完成之前的任务目标，而不是重新开始或执行其他无关操作。
4. 在执行任何操作前，先检查当前app是否是目标app，如果不是，先执行 Launch，不要执行其他操作（如Home,Back等）。
5. 如果进入到了无关页面，先执行 Back。如果执行Back后页面没有变化，请点击页面左上角的返回键进行返回，或者右上角的X号关闭。
6. 如果页面未加载出内容，最多连续 Wait 三次，否则执行 Back重新进入。
7. 如果页面显示网络问题，需要重新加载，请点击重新加载。
8. 如果当前页面找不到目标联系人、商品、店铺等信息，可以尝试 Swipe 滑动查找。
9. 遇到价格区间、时间区间等筛选条件，如果没有完全符合的，可以放宽要求。
10. 在做小红书总结类任务时一定要筛选图文笔记。
11. 购物车全选后再点击全选可以把状态设为全不选，在做购物车任务时，如果购物车里已经有商品被选中时，你需要点击全选后再点击取消全选，再去找需要购买或者删除的商品。
12. 在做外卖任务时，如果相应店铺购物车里已经有其他商品你需要先把购物车清空再去购买用户指定的外卖。
13. 在做点外卖任务时，如果用户需要点多个外卖，请尽量在同一店铺进行购买，如果无法找到可以下单，并说明某个商品未找到。
14. 请严格遵循用户意图执行任务，用户的特殊要求可以执行多次搜索，滑动查找。比如（i）用户要求点一杯咖啡，要咸的，你可以直接搜索咸咖啡，或者搜索咖啡后滑动查找咸的咖啡，比如海盐咖啡。（ii）用户要找到XX群，发一条消息，你可以先搜索XX群，找不到结果后，将"群"字去掉，搜索XX重试。（iii）用户要找到宠物友好的餐厅，你可以搜索餐厅，找到筛选，找到设施，选择可带宠物，或者直接搜索可带宠物，必要时可以使用AI搜索。
15. 在选择日期时，如果原滑动方向与预期日期越来越远，请向反方向滑动查找。
16. 执行任务过程中如果有多个可选择的项目栏，请逐个查找每个项目栏，直到完成任务，一定不要在同一项目栏多次查找，从而陷入死循环。
17. 在执行下一步操作前请一定要检查上一步的操作是否生效，如果点击没生效，可能因为app反应较慢，请先稍微等待一下，如果还是不生效请调整一下点击位置重试，如果仍然不生效请跳过这一步继续任务，并在finish message说明点击不生效。
18. 在执行任务中如果遇到滑动不生效的情况，请调整一下起始点位置，增大滑动距离重试，如果还是不生效，有可能是已经滑到底了，请继续向反方向滑动，直到顶部或底部，如果仍然没有符合要求的结果，请跳过这一步继续任务，并在finish message说明但没找到要求的项目。
19. 在做游戏任务时如果在战斗页面如果有自动战斗一定要开启自动战斗，如果多轮历史状态相似要检查自动战斗是否开启。
20. 如果没有合适的搜索结果，可能是因为搜索页面不对，请返回到搜索页面的上一级尝试重新搜索，如果尝试三次返回上一级搜索后仍然没有符合要求的结果，执行 finish(message="原因")。
21. 在结束任务前请一定要仔细检查任务是否完整准确的完成，如果出现错选、漏选、多选的情况，请返回之前的步骤进行纠正。
""".trimIndent()
    }
    
    
    /**
     * 创建工具消息（添加到上下文）
     */
    fun createToolMessage(content: String): NetworkChatMessage {
        return NetworkChatMessage(
            role = "tool",
            content = listOf(ContentItem(type = "text", text = content))
        )
    }
    
    /**
     * 解析模型响应
     */
    private fun parseResponse(content: String): ModelResponse {
        // 首先尝试使用正则表达式提取</think>和<answer>标签中的内容（原始格式）
        val thinkRegex = "</think>(.*?)</think>".toRegex(RegexOption.DOT_MATCHES_ALL)
        val answerRegex = "<answer>(.*?)</answer>".toRegex(RegexOption.DOT_MATCHES_ALL)
        
        var think = thinkRegex.find(content)?.groupValues?.get(1)?.trim() ?: ""
        var action = answerRegex.find(content)?.groupValues?.get(1)?.trim() ?: ""
        
        // 如果没有找到<think>和<answer>标签，检查是否包含do(action=...)格式的内容
        if (think.isEmpty() && action.isEmpty()) {
            // 尝试从内容中提取动作部分（以do(action=开头的部分）
            val actionRegex = "do\\(action=.*?\\)".toRegex(RegexOption.DOT_MATCHES_ALL)
            val actionMatch = actionRegex.find(content)
            if (actionMatch != null) {
                // 动作部分
                action = actionMatch.value.trim()
                // 思考部分是动作之前的内容
                think = content.substring(0, actionMatch.range.start).trim()
            } else {
                // 既不是原始格式也不是do(action=...)格式，使用整个内容作为动作
                action = content
            }
        }
        
        return ModelResponse(think, action)
    }
    
    /**
     * 解析模型响应（与createAssistantMessage配合使用）
     */
    fun parseModelResponse(responseContent: String): Pair<String, String> {
        val modelResponse = parseResponse(responseContent)
        return Pair(modelResponse.thinking, modelResponse.action)
    }
    
    /**
     * 请求模型（使用消息上下文）
     */
    suspend fun chat(
        messages: List<NetworkChatMessage>,
        modelName: String,
        apiKey: String
    ): ChatResponse? {
        Log.d(TAG, "开始执行chat请求，模型: $modelName, 消息数量: ${messages.size}")
        
        // 直接使用NetworkChatMessage，因为它已经是ChatMessage的别名
        val request = ChatRequest(
            model = modelName,
            messages = messages,
            maxTokens = 1000, // 减少最大令牌数，避免响应过大
            temperature = 0.0,
            topP = 0.85,
            frequencyPenalty = 0.2,
            stream = false
        )
        
        val authHeader = "Bearer $apiKey"
        
        // 添加详细日志，打印完整请求体
        val requestJson = json.toJson(request)
        Log.d(TAG, "发送API请求到: ${baseUrl}chat/completions")
        Log.d(TAG, "请求体大小: ${requestJson.length} 字符")
        Log.d(TAG, "请求消息数量: ${request.messages.size}")
        Log.d(TAG, "Authorization头: $authHeader")
        
        try {
            val response = api.chatCompletion(authHeader, request)
            
            Log.d(TAG, "API响应状态码: ${response.code()}, 消息: ${response.message()}")
            
            if (response.isSuccessful && response.body() != null) {
                Log.d(TAG, "API请求成功，返回响应体")
                val responseBody = response.body()
                val responseJson = json.toJson(responseBody)
                Log.d(TAG, "响应体大小: ${responseJson.length} 字符")
                return responseBody
            } else {
                // 尝试获取详细的错误信息
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "API请求失败: ${response.code()} ${response.message()}, 错误详情: $errorBody")
                throw Exception("API request failed: ${response.code()} ${response.message()} - $errorBody")
            }
        } catch (e: Exception) {
            Log.e(TAG, "API请求异常: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * 创建助手消息（添加到上下文）
     */
    fun createAssistantMessage(thinking: String, action: String): NetworkChatMessage {
        val content = "<think>$thinking</think><answer>$action</answer>"
        return NetworkChatMessage(
            role = "assistant",
            content = listOf(ContentItem(type = "text", text = content))
        )
    }
    
    /**
     * 从内容中提取 JSON 对象
     */
    private fun extractJsonFromContent(content: String): String {
        // 尝试找到 JSON 对象（匹配嵌套的大括号）
        var startIndex = -1
        var braceCount = 0
        val candidates = mutableListOf<String>()
        
        for (i in content.indices) {
            when (content[i]) {
                '{' -> {
                    if (startIndex == -1) {
                        startIndex = i
                    }
                    braceCount++
                }
                '}' -> {
                    braceCount--
                    if (braceCount == 0 && startIndex != -1) {
                        val candidate = content.substring(startIndex, i + 1)
                        try {
                            // 验证是否是有效的 JSON
                            com.google.gson.JsonParser.parseString(candidate)
                            candidates.add(candidate)
                        } catch (e: Exception) {
                            // 不是有效 JSON，继续查找
                        }
                        startIndex = -1
                    }
                }
            }
        }
        
        // 返回第一个有效的 JSON 对象
        return candidates.firstOrNull() ?: ""
    }
    
    /**
     * 创建用户消息（包含图片）
     */
    fun createUserMessageWithImage(userPrompt: String, base64Image: String, currentApp: String?): NetworkChatMessage {
        val userContent = mutableListOf<ContentItem>()
        val screenInfoJson = buildScreenInfo(currentApp)
        val textContent = "$userPrompt\n\n$screenInfoJson"

        // 先放图片，再放文本
        userContent.add(
            ContentItem(
                type = "image_url",
                imageUrl = ImageUrl(url = "data:image/jpeg;base64,$base64Image")
            )
        )

        userContent.add(ContentItem(type = "text", text = textContent))

        return NetworkChatMessage(role = "user", content = userContent)
    }
    
    /**
     * 从消息中移除图片内容（用于减少消息大小）
     */
    private fun removeImagesFromMessage(content: List<ContentItem>): List<ContentItem> {
        return content.filter { it.type != "image_url" }
    }
    
    /**
     * 限制消息上下文大小，避免内存占用过高
     * 注意：此方法现在仅作为工具方法，不作用于内部状态
     */
    fun limitMessageContextSize(messages: MutableList<NetworkChatMessage>, maxMessages: Int = 10) {
        if (messages.size > maxMessages) {
            messages.retainAll(messages.takeLast(maxMessages))
        }
    }
}