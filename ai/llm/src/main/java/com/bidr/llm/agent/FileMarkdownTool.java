package com.bidr.llm.agent;

import com.bidr.llm.parse.FileMarkdownService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.lang.ref.SoftReference;

/**
 * Title: FileMarkdownTool
 * Description: 通用「读文件」agent 工具（框架级，业务零绑定）：LLM 拿到文件地址（http(s) URL
 * 或服务器本地路径）就能调 read_file 拿 Markdown 正文，能力即 {@link FileMarkdownService}
 * 门面的全部格式面（文本直读/POI/PDFBox；classpath 引了转录腿实现如 core/ai/mineru 时
 * pdf/图片先走外部转录，失败回落门面本地路径——挂不挂腿对模型不可见）。
 * <p>
 * 长文件<b>分页续读</b>（2026-10-18 用户拍板"必须做，不然后边读不进来"）：入参带 {@code offset}
 * （0 起字符位置），每次返回一段 {@code maxChars}，未到末尾时末尾注记下一页起始 offset，
 * 模型续读传那个数——已读段落不再重灌上下文（区别于"调大 maxChars 从头重取"）。
 * <p>
 * 续读取<b>同一份解析快照</b>（单条 SoftReference 缓存，GC 可清）：既省逐页重新解析/重新转录
 * 的成本，也保证分页期间各段来自同一版正文（中途重解析可能在文件变更后让 offset 错位）。
 * 工具语义照 {@link AgentAskUserTool}：失败<b>回文本</b>不抛（模型可自纠重试）。
 * 业务侧只认自己句柄（fileId 等）的场景＝自己写薄壳把句柄解成地址/字节，本工具不管。
 * 装配见 {@code LlmDefaultAutoConfiguration#fileMarkdownTool}，挂进 agent 工具清单即用。
 *
 * @author Sharp
 * @since 2026/10/18
 */
@Slf4j
public class FileMarkdownTool {

    /** 默认每段返回字符上限：对齐 AGENT_TOOL_RECALL_MAX_CHARS 口径，防大文档灌爆上下文 */
    private static final int DEFAULT_MAX_CHARS = 20000;

    /** 单次窗口硬上限（PagedText 防线）：模型请求再大也收口在此，不暴露给模型 */
    private static final int HARD_MAX_CHARS = 50000;

    private final FileMarkdownService fileMarkdownService;

    /** 续读快照缓存（单条、SoftReference：分页大件不逐页重解析，GC 压力下可清后重解析） */
    private final Object snapshotLock = new Object();

    private SoftReference<Snapshot> snapshotRef;

    public FileMarkdownTool(FileMarkdownService fileMarkdownService) {
        this.fileMarkdownService = fileMarkdownService;
    }

    @Tool("把一份文件读取为 Markdown 正文。入参是文件地址：http(s) URL 或服务器本地路径；"
            + "支持文本/office/pdf/图片等常见格式（扫描件与图片会经转录或多模态转文字）。"
            + "当你持有文件地址且需要其内容时调用。长文件分页读：本次返回 offset 起的 maxChars 字，"
            + "没读到底会在末尾注记下一页的起始 offset，续读就传那个数；第一次读传 offset=0")
    public String readFile(@P("文件地址：http(s) URL 或服务器本地路径") String urlOrPath,
                           @P("本次最多返回的字符数，传 0 或负数按默认 2 万，超过 5 万按 5 万") int maxChars,
                           @P("本次读取的起始字符位置（0 起），续读传上次末尾注记的 offset") int offset) {
        if (urlOrPath == null || urlOrPath.trim().isEmpty()) {
            return "拒绝：文件地址为空，请给出 http(s) 地址或服务器本地路径后重试";
        }
        String location = urlOrPath.trim();
        String markdown;
        try {
            markdown = snapshotOf(location);
        } catch (IOException | RuntimeException e) {
            log.warn("[LLM工具] read_file 读取失败: {} 原因: {}", location, e.getMessage());
            return "读取失败：" + e.getMessage()
                    + "。请核对地址可访问、文件存在且格式支持，修正后重试；仍不可用请改用其他途径获取该内容";
        }
        if (markdown.isEmpty()) {
            releaseSnapshot();
            return "读取成功但文件内容为空：" + location;
        }
        int total = markdown.length();
        int from = PagedText.offset(offset);
        if (from >= total) {
            return "已到文件末尾：" + PagedText.beyond(from, total);
        }
        int limit = PagedText.limit(maxChars, DEFAULT_MAX_CHARS, HARD_MAX_CHARS);
        int to = Math.min(from + limit, total);
        String window = markdown.substring(from, to);
        log.info("[LLM工具] read_file: {} offset={}..{} / 共 {} 字", location, from, to, total);
        if (to == total) {
            releaseSnapshot();
        }
        if (to < total || from > 0) {
            // 统一进度注记（PagedText）：未到底带下一页 offset 指令、到底明说；整读小文件保持纯正文
            return window + "\n\n[" + PagedText.progress(from, to, total) + "]";
        }
        return window;
    }

    /**
     * 取（或解析并缓存）该地址的正文快照：命中单条缓存直接复用；未命中走门面解析后存入。
     * 门面抛出的异常原样上抛（readFile 统一回文本），缓存只在成功解析时建立
     */
    private String snapshotOf(String location) throws IOException {
        synchronized (snapshotLock) {
            SoftReference<Snapshot> ref = snapshotRef;
            if (ref != null) {
                Snapshot snapshot = ref.get();
                if (snapshot != null && snapshot.source.equals(location)) {
                    return snapshot.markdown;
                }
            }
        }
        String markdown = fileMarkdownService.toMarkdown(location);
        synchronized (snapshotLock) {
            snapshotRef = new SoftReference<>(new Snapshot(location, markdown));
        }
        return markdown;
    }

    /** 读完（到末尾/空文件）即弃快照：不留大正文在堆里 */
    private void releaseSnapshot() {
        synchronized (snapshotLock) {
            snapshotRef = null;
        }
    }

    /** 分页快照：source 为地址原文（trim 后），markdown 为解析全文 */
    private static final class Snapshot {

        private final String source;

        private final String markdown;

        private Snapshot(String source, String markdown) {
            this.source = source;
            this.markdown = markdown;
        }
    }
}
