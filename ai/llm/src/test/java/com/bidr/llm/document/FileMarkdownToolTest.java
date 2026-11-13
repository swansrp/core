package com.bidr.llm.document;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bidr.document.FileMarkdownService;
import dev.langchain4j.agent.tool.Tool;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Title: FileMarkdownToolTest
 * Description: read_file 工具判据（2026-10-18 加 offset 分后续读；工具随 M16 修复轮搬进
 * tool/document，C′ 迁移 2026-11-07 随 lc4j 触点整体迁 ai/llm，判据面一字不动、
 * 断言框架随 llm 测试集翻成 JUnit4）：
 * 小文件整读纯正文不带注记、分页截断注记给出下一页 offset、续读段拼起来等于原文（同一快照的
 * 一致性）、末页/越界有明确到底信号、默认 2 万上限、空地址拒绝、失败回文本不抛。
 * 走真实 {@link FileMarkdownService}＋本地临时文件，零外部依赖。
 *
 * @author Sharp
 * @since 2026/10/18
 */
public class FileMarkdownToolTest {

    private Path tempDir;

    private FileMarkdownTool tool;

    @Before
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("file-markdown-tool");
        tool = new FileMarkdownTool(new FileMarkdownService(null, null));
    }

    @After
    public void tearDown() throws IOException {
        if (tempDir != null) {
            Files.walk(tempDir).sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(p -> p.toFile().delete());
        }
    }

    private Path fileOf(String name, String content) throws IOException {
        Path file = tempDir.resolve(name);
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private String repeat(String unit, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(unit);
        }
        return sb.toString();
    }

    /** m-r2-1：INFO 日志不得携带完整预签名 URL（查询串＝一小时读权），只留 host+路径尾段 */
    @Test
    public void readFileLogMasksPresignedUrl() throws IOException {
        FileMarkdownService service = mock(FileMarkdownService.class);
        String url = "https://oss.example.com/bucket/agent-session/1/s/abcdefgh.docx"
                + "?X-Amz-Signature=SECRET&OSSAccessKeyId=AK";
        when(service.toMarkdown(url)).thenReturn("正文");
        FileMarkdownTool maskedTool = new FileMarkdownTool(service);

        Logger logger = (Logger) LoggerFactory.getLogger(FileMarkdownTool.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertEquals("正文", maskedTool.readFile(url, 0, 0));
        } finally {
            logger.detachAppender(appender);
        }

        StringBuilder logs = new StringBuilder();
        for (ILoggingEvent event : appender.list) {
            logs.append(event.getFormattedMessage()).append('\n');
        }
        assertFalse("签名查询串不得进日志", logs.toString().contains("SECRET"));
        assertFalse("签名参数名不得进日志", logs.toString().contains("X-Amz"));
        assertFalse("AccessKey 参数不得进日志", logs.toString().contains("OSSAccessKeyId"));
        assertTrue("日志保留 host 便于排障", logs.toString().contains("oss.example.com"));
        assertTrue("日志保留路径尾段", logs.toString().contains("pathTail="));
    }

    /** m-r2-1：本地路径日志只留文件名（不泄露目录结构） */
    @Test
    public void maskLocationKeepsOnlyFilenameForLocalPath() {
        assertEquals("855.docx", FileMarkdownTool.maskLocation("C:\\data\\uploads\\855.docx"));
        assertEquals("855.docx", FileMarkdownTool.maskLocation("/var/lib/files/855.docx"));
        assertFalse(FileMarkdownTool.maskLocation("https://oss.example/x?Signature=SECRET").contains("SECRET"));
    }

    @Test
    public void 小文件整读返回纯正文不带注记() throws IOException {
        Path file = fileOf("note.txt", "第一行\n第二行");
        assertEquals("offset=0 一次读全＝纯正文", "第一行\n第二行", tool.readFile(file.toString(), 0, 0));
    }

    @Test
    public void 分页首段截断并注记下一页offset() throws IOException {
        Path file = fileOf("big.txt", repeat("字", 300));
        String result = tool.readFile(file.toString(), 100, 0);
        assertTrue(result.startsWith(repeat("字", 100)));
        assertTrue("截断注记须给下一页 offset（模型照抄续读）",
                result.contains("本次返回 offset=0..100 / 全文 300字") && result.contains("传 offset=100"));
    }

    @Test
    public void 续读段落与首段拼起来等于原文() throws IOException {
        Path file = fileOf("page.txt", repeat("甲", 150) + repeat("乙", 150));
        String first = tool.readFile(file.toString(), 100, 0);
        String second = tool.readFile(file.toString(), 100, 100);
        String third = tool.readFile(file.toString(), 100, 200);
        String joined = first.substring(0, 100) + second.substring(0, 100) + stripNote(third);
        assertEquals("三段拼合须逐字还原原文（同一快照的一致性）",
                repeat("甲", 150) + repeat("乙", 150), joined);
        assertTrue("末段须带到底信号", third.contains("[已到文件末尾") && third.contains("offset=200..300"));
    }

    @Test
    public void offset超出末尾给明确到底信号() throws IOException {
        Path file = fileOf("small.txt", repeat("a", 10));
        String result = tool.readFile(file.toString(), 0, 999);
        assertTrue(result.contains("offset=999 已超出末尾") && result.contains("全文 10字"));
        assertTrue("须指路回 offset=0", result.contains("传 offset=0"));
    }

    @Test
    public void 零或负上限回落默认两万字() throws IOException {
        Path file = fileOf("big2.txt", repeat("a", 25000));
        String result = tool.readFile(file.toString(), 0, 0);
        assertTrue("默认上限应为 2 万字（对齐回捞口径）", result.contains("offset=0..20000 / 全文 25000字"));
    }

    @Test
    public void 超大请求被硬上限收口() throws IOException {
        Path file = fileOf("huge.txt", repeat("a", 60000));
        String result = tool.readFile(file.toString(), 999999, 0);
        assertTrue("模型请求再大也收口在 5 万（PagedText 防线）", result.contains("offset=0..50000 / 全文 60000字"));
    }

    @Test
    public void 连续换文件后回读首个文件仍正确() throws IOException {
        Path a = fileOf("a.txt", repeat("A", 50));
        Path b = fileOf("b.txt", repeat("B", 50));
        tool.readFile(a.toString(), 10, 0);
        tool.readFile(b.toString(), 10, 0);
        assertEquals("换源后再回读＝按新地址重新解析", repeat("A", 50), tool.readFile(a.toString(), 0, 0));
    }

    @Test
    public void 空地址拒绝而非抛() {
        assertTrue(tool.readFile("  ", 0, 0).startsWith("拒绝：文件地址为空"));
    }

    @Test
    public void 读取失败回文本不抛且带原因() {
        String result = tool.readFile(tempDir.resolve("不存在.txt").toString(), 0, 0);
        assertTrue("失败须回可自纠文本", result.startsWith("读取失败："));
        assertTrue(result.contains("修正后重试"));
    }

    @Test
    public void 空内容文件明说() throws IOException {
        Path file = fileOf("empty.txt", "");
        assertFalse(tool.readFile(file.toString(), 0, 0).isEmpty());
        assertTrue(tool.readFile(file.toString(), 0, 0).startsWith("读取成功但文件内容为空"));
    }

    @Test
    public void 工具注册名与登记件对账() {
        // ai/agent-console 的 file-read 能力包登记的字面量是 "readFile"（ai 族不依 tool 族，
        // 那边取不到本类的 @Tool 真名）——对账腿挪到宿主这一侧：改名必须同时改登记件，否则勾选面与工具面静默失配
        Set<String> names = new LinkedHashSet<>();
        for (Method method : FileMarkdownTool.class.getDeclaredMethods()) {
            Tool tool = method.getAnnotation(Tool.class);
            if (tool != null) {
                names.add(tool.name().isEmpty() ? method.getName() : tool.name());
            }
        }
        assertEquals("@Tool 实际注册名必须等于 agent-console 登记的 readFile",
                new LinkedHashSet<>(Collections.singletonList("readFile")), names);
    }

    /** 剥掉末尾的分页注记，只留正文段 */
    private static String stripNote(String page) {
        int idx = page.indexOf("\n\n[");
        return idx >= 0 ? page.substring(0, idx) : page;
    }
}
