# core/llm — langchain4j 基础框架模块

为业务模块提供开箱即用的大模型调用基础设施：**配置热刷新模型**、**流式生成稳定性包装**、**输出清洗**。本模块不依赖任何业务表和业务类，通过 `ModelConfigProvider` 接口把"配置从哪来"留给业务侧实现。

基于 langchain4j `0.33.0`（`langchain4j` + `langchain4j-open-ai`），兼容一切 OpenAI 协议的模型服务（DashScope、vLLM、Ollama 等）。

## 模块结构

```
com.bidr.llm
├── provider
│   └── ModelConfigProvider          模型配置提供者接口（业务侧实现）
├── model
│   ├── RefreshableChatModel         可热刷新的同步模型（实现 ChatLanguageModel）
│   └── RefreshableStreamingChatModel 可热刷新的流式模型（实现 StreamingChatLanguageModel）
├── execute
│   ├── StreamingContentExecutor     流式生成稳定性包装器（@Service，自动装配）
│   └── ModelOutputSanitizer         模型输出清洗静态工具
├── sse
│   ├── SseStreamingResponseHandler  langchain4j 流式回调 → SseEmitter 桥接器（真 SSE 推流）
│   └── LlmChatDemoController        三种拿数据方式的内置示例接口（可用 llm.chat-sse.enabled=false 关闭）
├── parse
│   ├── FileMarkdownService          文件 → Markdown 统一入口（url/file/路径/InputStream，自动装配）
│   ├── VisionModelConfig            多模态模型配置 DTO（外部显式传入时使用）
│   └── converter                    docx/xlsx/pptx/html → Markdown 转换器（POI/Jsoup）
├── provider
│   └── DbAwareModelConfigProvider   sys_config 数据库配置 → yaml → 默认模型的三级回落实现（支持 VISION purpose）
└── store
    ├── StreamAnswerStore            流式回答状态存储接口
    ├── StreamAnswerState            回答状态快照（content/finish/extra）
    ├── RedisStreamAnswerStore       Redis 实现（带写入节流，类路径有 core/redis 时自动装配）
    └── InMemoryStreamAnswerStore    内存兜底实现（类路径无 core/redis 时自动装配，单实例适用）
```

## 快速接入

### 1. 加依赖

```xml
<dependency>
    <groupId>com.bidr</groupId>
    <artifactId>llm</artifactId>
    <version>${jarVersion}</version>
</dependency>
```

应用扫描基包为 `com.bidr` 时（继承 `BaseApplication` 即是），`StreamingContentExecutor` 会自动注册为 Bean，无需额外配置。

### 2. 实现 ModelConfigProvider

告诉框架"某个用途（purpose）的模型配置是什么"。配置可以来自数据库、yaml 或配置中心：

```java
@Service
public class MyModelConfigProvider implements ModelConfigProvider {

    @Override
    public String getBaseUrl(String purposeType) { ... }

    /** userId 可为 null，回退策略由实现决定（如返回系统默认 Key） */
    @Override
    public String getApiKey(String purposeType, Long userId) { ... }

    @Override
    public String getModelName(String purposeType) { ... }

    @Override
    public long getTimeoutSeconds(String purposeType) { ... }

    /**
     * 配置签名（不含 Key）。签名变化 = 配置变化 = 触发底层模型重建。
     * 建议拼接 baseUrl/modelName/timeout 等所有会影响连接的字段。
     */
    @Override
    public String getConfigSignatureWithoutKey(String purposeType) { ... }
}
```

purpose 是开放字符串，由业务自行定义（如 `GENERATION`、`FILE_EXTRACT`），不预设取值。

参考实现：`mcp-bid` 的 `BidModelConfigProvider`（数据库双表 + ConcurrentHashMap 缓存 + 用户组 Key 解析）。

### 3. 装配模型 Bean

```java
@Configuration
public class MyLlmConfig {

    @Value("${mcp.xxx.llm.max-attempts:1}")
    private int maxAttempts;
    @Value("${llm.proxy.enable:false}")
    private boolean proxyEnable;
    @Value("${llm.proxy.host:}")
    private String proxyHost;
    @Value("${llm.proxy.port:0}")
    private int proxyPort;

    @Bean
    public ChatLanguageModel chatModel(MyModelConfigProvider provider) {
        // userIdSupplier 每次调用时取值，用于按用户隔离 Key；无用户概念时传 () -> null
        return new RefreshableChatModel(provider, "GENERATION", maxAttempts, buildProxy(), AccountContext::getUserId);
    }

    @Bean
    public StreamingChatLanguageModel streamingChatModel(MyModelConfigProvider provider) {
        return new RefreshableStreamingChatModel(provider, "GENERATION", buildProxy(), AccountContext::getUserId);
    }

    private Proxy buildProxy() {
        if (!proxyEnable || !StringUtils.hasText(proxyHost) || proxyPort <= 0) {
            return null;
        }
        return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
    }
}
```

拿到的 Bean 就是标准的 langchain4j `ChatLanguageModel` / `StreamingChatLanguageModel`，直接 `model.generate(prompt)` 使用。

**热刷新机制**：每次调用前，包装器会取 `userId + 配置签名 + 用户 Key` 作为缓存键；管理端改了配置或用户换了 Key，下一次调用自动重建底层 `OpenAiChatModel`，**无需重启应用**。缓存上限 256 个实例，超限整体清空重建。

### 4. 流式长文本生成（可选）

裸调流式接口在断流、卡住、超时场景下会让任务挂死。`StreamingContentExecutor` 封装了完整兜底：

```java
@Autowired
private StreamingContentExecutor streamingContentExecutor;

String content = streamingContentExecutor.generateContent(
        sectionId,                    // 内容标识，仅用于日志
        sectionTitle,                 // 内容名称，仅用于日志
        prompt,                       // 提示词
        streamingChatModel,           // 流式模型（主）
        chatModel,                    // 同步模型（流式失败时自动降级）
        () -> taskService.isCanceled(taskId),   // 取消检查，每 3 秒轮询
        draft -> pushToFrontend(draft),         // 草稿回调，每个 token 触发（打字机效果）
        ModelOutputSanitizer::sanitize);        // 最终内容清洗函数
```

内置行为：

| 场景 | 行为 |
|---|---|
| 正常生成 | 每个 token 累积后回调 `draftUpdater`，完成后经 `finalNormalizer` 清洗返回 |
| 断流（已有内容但 15s 无新 token） | 不报错，按当前草稿收口返回 |
| 流式调用抛异常 | 自动降级到同步模型重新生成 |
| 总时长超 1 小时 | 抛 `IllegalStateException("流式生成超时")` |
| `cancelChecker` 返回 true / 线程被中断 | 抛 `InterruptedException("任务已取消")` |

### 5. 输出清洗

`ModelOutputSanitizer.sanitize(text)` 静态方法，可单独使用：移除模型误输出的 `<think>`/`<thinking>` 标签、英文推理前缀（`Here's a thinking process:` 等）、多余空白行。

### 6. 流式回答状态存储（可选）

用于"生产端持续写入、消费端按需读取"的**拉模式**场景（如三方平台刷新回调、前端轮询、SSE 断线重连补发）。真 SSE 直推场景（见第 7 节）不需要本能力，内容直接流过连接不落地。

存储实现按类路径自动二选一，业务代码只注入接口，部署形态变化不改代码：

| 部署形态 | 实现 | 装配条件 |
|---|---|---|
| 多实例 / 请求会打到不同节点 | `RedisStreamAnswerStore` | 类路径存在 core/redis |
| 单实例且允许重启丢流 | `InMemoryStreamAnswerStore` | 类路径不存在 core/redis |

```java
@Autowired
private StreamAnswerStore streamAnswerStore;

// 生产端：流式回调里持续写入（覆盖式全量内容）
streamAnswerStore.updateContent(streamId, currentFullText, false);
// 结束时写终态（终态写入永不被节流跳过）
streamAnswerStore.updateContent(streamId, finalText, true);

// 消费端：按需读取当前状态
StreamAnswerState state = streamAnswerStore.getState(streamId);
```

Redis 实现内置**写入节流**：中间态更新距上次写入不足 `min-write-interval-ms` 时跳过（内容是全量覆盖，下次写入不丢数据），避免长文场景每个 token 写一次 Redis 打爆带宽。业务扩展数据（如图片列表）自行序列化后放 `StreamAnswerState.extra`。

仅使用本能力、不用 langchain4j 模型的模块，加依赖时可排除 `dev.langchain4j` 的两个构件（参考 `mcp-wechat/pom.xml`）。使用方：`mcp-wechat` 企微 AI 机器人（FastGPT 流式回答 + 企微 stream 刷新回调）。

### 7. SSE 聊天推流与内置示例接口（可选，Web 应用）

**一句话判断法（怎么选拿数据的方式）：**

| 问自己 | 答案 | 结论 |
|---|---|---|
| 消费端能和生产端保持同一条连接吗？ | 能（浏览器直连你的服务） | 方式一，不需要 store |
| 不能保持连接，读的人是自己前端？ | 是，且单实例部署 | 方式二 + 内存版 |
| 同上，但多实例部署？ | 是 | 方式二 + Redis |
| 读的人是第三方服务器（回调）？ | 是 | 方式三，基本必 Redis |

框架内置示例 Controller `LlmChatDemoController`（引入 llm 依赖即生效，取应用中唯一的 `StreamingChatLanguageModel` Bean；`llm.chat-sse.enabled=false` 可关闭），三种方式各有端点：

| 方式 | 端点 | 说明 |
|---|---|---|
| 一：真 SSE 直推 | `GET/POST /web/api/llm/chat/sse` | 连接保持，token 直接流过连接不落地 |
| 二：轮询 | `POST /chat/poll/start` 发起拿 streamId；`GET /chat/poll/{streamId}` 定时取状态 | 跨请求靠 `StreamAnswerStore` 存中间态 |
| 三：回调刷新 | `POST /chat/callback`（body 带 streamId） | 模拟三方服务器来拉，生产端与方式二共用 |

方式一的桥接由 `SseStreamingResponseHandler` 完成（langchain4j 回调 → Spring `SseEmitter`），事件协议：

| 事件 | data | 说明 |
|---|---|---|
| `delta` | token 增量片段 | 前端按序拼接即可实现打字机效果 |
| `done` | 完整内容 | 生成结束，连接随后关闭 |
| `error` | 错误信息 | 生成失败，连接随后关闭（不走容器错误页） |

自己业务 Controller 里写方式一只需三行：

```java
@GetMapping(value = "/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter chat(@RequestParam("prompt") String prompt) {
    SseEmitter emitter = new SseEmitter(0L);   // 0 = 不限时，由生成结束/失败关闭
    streamingModel.generate(prompt, new SseStreamingResponseHandler(emitter));
    return emitter;                            // generate 立即返回，token 在回调线程中推送
}
```

前端接法一：`EventSource`（浏览器原生，仅支持 GET）：

```js
const es = new EventSource(`/web/api/llm/chat/sse?prompt=${encodeURIComponent(prompt)}`);
es.addEventListener('delta', e => { output += e.data; render(output); });
es.addEventListener('done',  e => { render(e.data); es.close(); });
es.addEventListener('error', e => { showError(e.data); es.close(); });
```

前端接法二：`fetch` + ReadableStream（POST 带 body，实际聊天场景用这种）：

```js
const resp = await fetch('/web/api/llm/chat/sse', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ prompt })
});
const reader = resp.body.getReader();
const decoder = new TextDecoder();
let buffer = '';
while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    // SSE 帧以空行分隔，逐帧解析 event/data 字段
    const frames = buffer.split('\n\n');
    buffer = frames.pop(); // 末尾可能是不完整帧，留到下轮
    for (const frame of frames) {
        let event = 'message', data = '';
        for (const line of frame.split('\n')) {
            if (line.startsWith('event:')) event = line.slice(6).trim();
            else if (line.startsWith('data:')) data += line.slice(5).trim();
        }
        if (event === 'delta') { output += data; render(output); }
        else if (event === 'done') render(data);
        else if (event === 'error') showError(data);
    }
}
```

方式二前端接法（轮询）：

```js
const streamId = await fetch('/web/api/llm/chat/poll/start', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ prompt })
}).then(r => r.text());

const timer = setInterval(async () => {
    const state = await fetch(`/web/api/llm/chat/poll/${streamId}`).then(r => r.json());
    if (!state) return;              // 未就绪或已过期
    render(state.content);
    if (state.finish) clearInterval(timer);
}, 2000);
```

注意：`spring-webmvc` 在本模块中是 optional 依赖，仅 Web 应用可使用本能力（应用自身必有 spring-web，无需额外加依赖）。若前后端之间有 Nginx，需为 SSE 路径关闭缓冲（`proxy_buffering off`），否则流会被攒包。

### 8. 文件解析为 Markdown（parse）

把用户上传的各种文件拆成文字喂给 LLM 的统一入口：传入 **url / File / 文件路径 / InputStream** 任意一种，返回 **Markdown** 文本。

```java
@Autowired
private FileMarkdownService fileMarkdownService;

// 本地路径或 http(s) 地址（自动识别）
String md = fileMarkdownService.toMarkdown("D:/upload/合同.docx");
String md2 = fileMarkdownService.toMarkdown("https://oss.example.com/report.pdf");

// File / Path
String md3 = fileMarkdownService.toMarkdown(file);

// InputStream（必须带文件名，用于识别格式）
String md4 = fileMarkdownService.toMarkdown(inputStream, "清单.xlsx");
```

支持的格式与处理方式：

| 类型 | 处理 |
|---|---|
| md/txt/csv/json/xml/sql/yml/properties/log 等纯文本 | 直接读取（UTF-8 严格解码，失败回落 GBK） |
| html/htm | Jsoup 按语义转 Markdown（标题/列表/表格/代码块，剔除 script） |
| docx | POI XWPF：标题样式 → `#` 级标题，列表 → `-` 项，表格 → Markdown 表格 |
| xlsx/xls | POI：每 Sheet 一个 Markdown 表格，首行视为表头，单 Sheet 上限 1000 行 |
| pptx/ppt | POI：每页一个二级标题，文本框与表格按顺序输出 |
| doc | POI WordExtractor 纯文本提取 |
| pdf | PDFBox 逐页提取；某页文本过少判定为扫描页 → 渲染 PNG → **多模态模型转录** |
| png/jpg/jpeg/gif/webp/bmp | 直接走**多模态模型**转录 |
| 未知扩展名 | 二进制检测（NUL 字节）：文本则按文本读，否则拒绝 |

**多模态模型配置**（扫描件/图片解析用）三级回落，与现有 LLM 调用机制一致：

1. **调用时显式传入**（最高优先级）：

```java
VisionModelConfig vision = VisionModelConfig.builder()
        .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
        .apiKey("sk-xxx")
        .modelName("qwen-vl-max")
        .timeoutSeconds(180)
        .build();
String md = fileMarkdownService.toMarkdown(file, vision);
```

2. **sys_config 数据库参数**（经 `ModelConfigProvider`）：`VISION_BASE_URL` / `VISION_API_KEY` / `VISION_MODEL_NAME` / `VISION_TIMEOUT_SECONDS`，已注册为 `LlmParam`，系统参数页可直接维护；任一项留空则该项继续回落下一级。
3. **yaml 默认**：`llm.vision.*`（见下表），其中 base-url/api-key/model-name 留空时回落默认模型配置（`llm.*`）。

内置约束：单文件上限 100MB；PDF 走多模态的页数上限 50 页（超出部分跳过并注明）；渲染 DPI 150。

## yaml 配置项

| 键 | 默认值 | 说明 |
|---|---|---|
| `llm.streaming.idle-timeout-ms` | 15000 | 流式空闲收口超时（毫秒），已有内容但该时长无新 token 时按草稿收口 |
| `llm.streaming.max-wait-ms` | 3600000 | 流式生成硬超时（毫秒） |
| `llm.proxy.enable` | false | 大模型调用 HTTP 代理开关（约定键，由业务装配类读取） |
| `llm.proxy.host` | 空 | 代理主机 |
| `llm.proxy.port` | 0 | 代理端口 |
| `llm.stream-answer.key-prefix` | `llm:stream:answer:` | 流式回答存储的 Redis key 前缀 |
| `llm.stream-answer.ttl-seconds` | 600 | 运行中状态过期时间（秒） |
| `llm.stream-answer.finished-ttl-seconds` | 300 | 流结束后的短过期时间（秒） |
| `llm.stream-answer.min-write-interval-ms` | 300 | 中间态写入节流间隔（毫秒），0 表示不节流 |
| `llm.chat-sse.enabled` | true | 内置示例接口 `LlmChatDemoController` 的开关 |
| `llm.vision.base-url` | 空 | 多模态模型服务地址；留空回落默认模型地址 |
| `llm.vision.api-key` | 空 | 多模态模型密钥；留空回落默认模型密钥 |
| `llm.vision.model-name` | 空 | 多模态模型名（须支持图片输入，如 qwen-vl-max）；留空回落默认模型 |
| `llm.vision.timeout-seconds` | 180 | 多模态模型调用超时（秒） |

`llm.proxy.*` 与框架全局 REST 代理（`my.rest.proxy.*`）相互独立，可单独为大模型调用开代理。

## 注意事项

- **线程池上下文**：在自定义线程池中异步调用模型时，`userIdSupplier`（如 `AccountContext::getUserId`）依赖 ThreadLocal，务必给线程池挂上 `com.bidr.authorization.config.async.ContextCopyingTaskDecorator`，否则工作线程取不到用户导致 Key 隔离失效。
- **配置签名要完整**：`getConfigSignatureWithoutKey` 漏掉字段会导致改了该字段但模型不重建；多拼无害，少拼有坑。
- **`generateContent` 会阻塞当前线程**直至生成完成/超时/取消，应在异步任务线程中调用，不要占用 Web 请求线程。
