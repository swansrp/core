package com.bidr.llm.agent.file;

import com.bidr.document.FileMarkdownService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Title: AgentFileParseService
 * Description: 会话附件<b>预解析</b>服务（发起前素材校验）——前端上传素材后立即调用，把
 * 「这份文件模型到底读不读得动」提前暴露在输入区：解析中显示进度、通过打勾、失败标叉，
 * 未全部通过不允许发起（2026-11-17 页面口径）。
 * <p>
 * 解析口径与模型侧 {@code read_attachment} <b>同一条腿</b>：{@link AgentFileStore#read(FileRef)}
 * 取回原字节 → {@link FileMarkdownService#toMarkdown(java.io.InputStream, String)}（装配了
 * {@code tool.document.transcriber} 时图片/扫描件走该增强转写腿，缺省 epc＝mineru）。因此
 * 「解析通过」＝模型按同一路径能读出正文；解析失败如实回人话（不吞错、不静默降级）。
 * <p>
 * <b>异步</b>：MinerU/多模态转写单件可到分钟级，端点不能同步等。状态落本实例内存
 * （{@code ConcurrentHashMap}，键 sessionId:fileId），前端按批轮询；文件不可变，完成态可复用，
 * 失败态允许重试（重新起一次覆盖）。条目数做了上限（{@link #MAX_STATES}，淘汰已完成的最旧项），
 * 进程重启后状态丢失由调用方按失败处理并重试（素材本身仍在对象存储）。
 * <p>
 * 并发闸：解析线程池固定 2 线程（对齐行业包 {@code parse_material.py --jobs 2} 口径——MinerU
 * 服务端并发会互相拖慢），队列 32；队列满时该件直接标失败并提示稍后重试，不阻塞请求线程。
 * <p>
 * 能力缺席（文档模块未装配／无存储端口）不是错误：文档解析缺席时状态置 {@link #STATUS_SKIPPED}
 * （前端按「已跳过校验」放行，不拿可选能力卡死发起流程）；存储端口缺席只可能发生在上传前，
 * 这里按失败处理（附件确实不在）。
 *
 * @author Sharp
 * @since 2026/11/17
 */
@Slf4j
@Component
public class AgentFileParseService {

    /** 排队中（已受理，未开始） */
    public static final String STATUS_PENDING = "PENDING";

    /** 解析中 */
    public static final String STATUS_PARSING = "PARSING";

    /** 解析通过（正文非空），{@code chars} 为 Markdown 字符数 */
    public static final String STATUS_DONE = "DONE";

    /** 解析失败（{@code error} 为可直接展示的人话） */
    public static final String STATUS_FAILED = "FAILED";

    /** 解析能力未装配，已跳过校验（前端按可发起处理，附注说明） */
    public static final String STATUS_SKIPPED = "SKIPPED";

    /** 状态条目上限（超限淘汰已完成的最旧项；只护内存，不做持久化） */
    private static final int MAX_STATES = 512;

    /** 单批受理上限（解析端点入参护栏） */
    public static final int MAX_BATCH = 100;

    /** 失败文案上限：转写腿异常可能带很长的服务端响应，截断后给前端 */
    private static final int MAX_ERROR_CHARS = 240;

    /** 预览正文上限（chips tooltip 用，不参与模型上下文） */
    private static final int PREVIEW_CHARS = 160;

    /** 单件保留的预解析全文上限（字符）：供沙箱放件附带解析正文；超限不保留（放件回落仅原件），
     *  与 {@link #MAX_STATES} 共同护内存（512 件 × 20 万字符上界可控） */
    private static final int MAX_RETAINED_MARKDOWN_CHARS = 200_000;

    /**
     * 解析线程池（2 线程＋有界队列 32，daemon）：与行业包 MinerU 并发口径一致；
     * 队列满＝RejectedExecution 直接标失败（不排无限队伍，也不拖死请求线程）。
     */
    private static final ExecutorService PARSE_EXECUTOR = new ThreadPoolExecutor(
            2, 2, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(32),
            runnable -> {
                Thread thread = new Thread(runnable, "agent-file-parse");
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.AbortPolicy());

    private final AgentFileCapabilityResolver fileCapabilityResolver;

    /** 文档解析门面（document 模块装配；缺席＝状态 SKIPPED，不拦发起） */
    private final ObjectProvider<FileMarkdownService> markdownServiceProvider;

    /** 解析状态（键 sessionId:fileId） */
    private final Map<String, ParseState> states = new ConcurrentHashMap<>();

    public AgentFileParseService(AgentFileCapabilityResolver fileCapabilityResolver,
                                 ObjectProvider<FileMarkdownService> markdownServiceProvider) {
        this.fileCapabilityResolver = fileCapabilityResolver;
        this.markdownServiceProvider = markdownServiceProvider;
    }

    /**
     * 受理一次解析（幂等）：排队中/解析中/已完成直接复用；失败态重新起一次（重试语义）。
     *
     * @param sessionId 会话标识（归属已由调用方校验）
     * @param fileId    会话内附件标识
     * @return 当前状态快照（不返回内部可变对象）
     */
    public ParseView start(String sessionId, String fileId) {
        String key = key(sessionId, fileId);
        ParseState existing = states.get(key);
        if (existing != null && !STATUS_FAILED.equals(existing.getStatus())) {
            return ParseView.of(existing);
        }
        AgentFileStore fileStore = fileCapabilityResolver.fileStore();
        if (fileStore == null) {
            return recordFailure(key, fileId, null, "附件能力未启用（对象存储未装配），无法解析");
        }
        FileRef ref;
        try {
            ref = fileStore.get(sessionId, fileId);
        } catch (Exception e) {
            log.warn("附件解析定位失败: sessionId={}, fileId={}, error={}", sessionId, fileId, e.getMessage());
            return recordFailure(key, fileId, null, "附件定位失败，请重新上传");
        }
        if (ref == null) {
            return recordFailure(key, fileId, null, "附件不存在或已被清理，请重新上传");
        }
        FileMarkdownService markdownService = markdownServiceProvider.getIfUnique();
        if (markdownService == null) {
            ParseState skipped = new ParseState(fileId, ref.name());
            skipped.setStatus(STATUS_SKIPPED);
            skipped.setFinishedAt(System.currentTimeMillis());
            states.put(key, skipped);
            evictIfNeeded();
            return ParseView.of(skipped);
        }
        ParseState state = new ParseState(fileId, ref.name());
        states.put(key, state);
        evictIfNeeded();
        try {
            PARSE_EXECUTOR.execute(() -> doParse(key, sessionId, ref, markdownService));
        } catch (RejectedExecutionException e) {
            fail(state, "解析队列已满，请稍后重试");
        }
        return ParseView.of(state);
    }

    /**
     * 查询解析状态（批量轮询数据源）。
     *
     * @return 未知（进程重启／记录淘汰）返回 null，由调用方按失败提示重试
     */
    public ParseView status(String sessionId, String fileId) {
        return ParseView.of(states.get(key(sessionId, fileId)));
    }

    /** 删除暂存附件时清掉状态（避免同一 fileId 复用时读到旧结论） */
    public void forget(String sessionId, String fileId) {
        states.remove(key(sessionId, fileId));
    }

    /**
     * 取预解析全文（供沙箱放件附带「外边解析过的」正文，与原件一同进沙箱）。
     * 未解析/解析中/失败/跳过/条目淘汰/全文超限未保留 → 返回 null（调用方回落仅放原件）。
     */
    public String parsedMarkdown(String sessionId, String fileId) {
        ParseState state = states.get(key(sessionId, fileId));
        if (state == null || !STATUS_DONE.equals(state.getStatus())) {
            return null;
        }
        return state.getMarkdown();
    }

    // ==================== 解析执行 ====================

    private void doParse(String key, String sessionId, FileRef ref, FileMarkdownService markdownService) {
        ParseState state = states.get(key);
        if (state == null || !STATUS_PENDING.equals(state.getStatus())) {
            return;
        }
        long startedAt = System.currentTimeMillis();
        state.setStatus(STATUS_PARSING);
        state.setStartedAt(startedAt);
        try {
            AgentFileStore fileStore = fileCapabilityResolver.fileStore();
            FileRef full = fileStore == null ? null : fileStore.get(sessionId, ref.fileId());
            if (full == null) {
                fail(state, "附件不存在或已被清理，请重新上传");
                return;
            }
            if (!markdownService.isProcessable(full.name())) {
                fail(state, "不支持的文件格式：" + extensionOf(full.name()));
                return;
            }
            byte[] bytes = fileStore.read(full);
            if (bytes == null || bytes.length == 0) {
                fail(state, "文件内容为空，无法解析");
                return;
            }
            String markdown = markdownService.toMarkdown(new ByteArrayInputStream(bytes), full.name());
            if (markdown == null || markdown.trim().isEmpty()) {
                fail(state, "解析结果为空（空文件或无法识别的内容）");
                return;
            }
            state.setChars(markdown.length());
            state.setPreview(previewOf(markdown));
            // 保留全文供沙箱放件附带解析正文（超限不保留，放件回落仅原件）
            if (markdown.length() <= MAX_RETAINED_MARKDOWN_CHARS) {
                state.setMarkdown(markdown);
            }
            state.setStatus(STATUS_DONE);
            state.setFinishedAt(System.currentTimeMillis());
            state.setSeconds((state.getFinishedAt() - startedAt) / 1000L);
            log.info("会话附件解析通过: sessionId={}, fileId={}, name={}, size={}, chars={}, seconds={}",
                    sessionId, full.fileId(), full.name(), full.size(), state.getChars(), state.getSeconds());
        } catch (Throwable e) {
            // 转写腿失败如实回人话（模型侧同一条腿也会失败）；异常细节只进日志
            fail(state, humanError(e));
            log.warn("会话附件解析失败: sessionId={}, fileId={}, name={}, error={}",
                    sessionId, ref.fileId(), ref.name(), e.getMessage());
        }
    }

    /** 受理期即失败（附件不存在/能力缺席/队列满）：同样落状态，前端轮询能读到 FAILED 而非"状态丢失" */
    private ParseView recordFailure(String key, String fileId, String name, String error) {
        ParseState state = new ParseState(fileId, name);
        fail(state, error);
        states.put(key, state);
        evictIfNeeded();
        return ParseView.of(state);
    }

    private static void fail(ParseState state, String error) {
        state.setStatus(STATUS_FAILED);
        state.setError(error);
        state.setFinishedAt(System.currentTimeMillis());
    }

    /** 异常 → 可展示人话（去换行、截断；空消息回类名，避免只显示「解析失败」） */
    private static String humanError(Throwable e) {
        String message = e == null ? null : e.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = e == null ? "解析失败" : e.getClass().getSimpleName();
        }
        String oneLine = message.replaceAll("[\\r\\n]+", " ").trim();
        if (oneLine.length() > MAX_ERROR_CHARS) {
            oneLine = oneLine.substring(0, MAX_ERROR_CHARS) + "…";
        }
        return oneLine;
    }

    /** 预览：压缩空白后截断（chips tooltip 展示用） */
    private static String previewOf(String markdown) {
        String oneLine = markdown.replaceAll("\\s+", " ").trim();
        return oneLine.length() > PREVIEW_CHARS ? oneLine.substring(0, PREVIEW_CHARS) + "…" : oneLine;
    }

    private static String extensionOf(String name) {
        if (name == null) {
            return "未知";
        }
        int dot = name.lastIndexOf('.');
        return dot >= 0 && dot < name.length() - 1 ? name.substring(dot + 1).toLowerCase() : "未知";
    }

    private static String key(String sessionId, String fileId) {
        return sessionId + ':' + fileId;
    }

    /** 淘汰已完成的最旧条目，只在超上限时触发（Map 上限护内存，不做持久化） */
    private void evictIfNeeded() {
        if (states.size() <= MAX_STATES) {
            return;
        }
        states.entrySet().stream()
                .filter(entry -> {
                    String status = entry.getValue().getStatus();
                    return STATUS_DONE.equals(status) || STATUS_FAILED.equals(status)
                            || STATUS_SKIPPED.equals(status);
                })
                .sorted(Comparator.comparingLong(entry -> entry.getValue().getFinishedAt()))
                .limit(states.size() - MAX_STATES)
                .forEach(entry -> states.remove(entry.getKey(), entry.getValue()));
    }

    /** 解析状态（内部可变；对外一律经 {@link ParseView} 快照） */
    @Data
    static class ParseState {

        private final String fileId;

        private final String name;

        private String status = STATUS_PENDING;

        private Integer chars;

        private Long seconds;

        private String preview;

        /** 预解析全文（供沙箱放件附带；超限/未解析为 null，不入 HTTP 视图） */
        private String markdown;

        private String error;

        private long startedAt;

        private long finishedAt;

        ParseState(String fileId, String name) {
            this.fileId = fileId;
            this.name = name;
        }
    }

    /** 对外状态快照（HTTP 视图；字段口径见前端 AttachmentItem 解析态） */
    public record ParseView(String fileId, String name, String status, boolean ok,
                            Integer chars, Long seconds, String preview, String error) {

        public static ParseView of(ParseState state) {
            if (state == null) {
                return null;
            }
            boolean ok = STATUS_DONE.equals(state.getStatus()) || STATUS_SKIPPED.equals(state.getStatus());
            return new ParseView(state.getFileId(), state.getName(), state.getStatus(), ok,
                    state.getChars(), state.getSeconds(), state.getPreview(), state.getError());
        }

        static ParseView failed(String fileId, String name, String error) {
            return new ParseView(fileId, name, STATUS_FAILED, false, null, null, null, error);
        }

        /** 状态丢失（进程重启/条目淘汰）：按失败回，前端提示重试 */
        public static ParseView missing(String fileId) {
            return failed(fileId, null, "解析状态已丢失（服务重启或记录过期），请重试");
        }
    }
}