package com.bidr.cube;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bidr.cube.admin.AdminSandboxManager;
import com.bidr.cube.client.CubeHttpClient;
import com.bidr.cube.config.CubeSandboxProperties;
import com.bidr.cube.support.CubeFakeServer;
import com.bidr.kernel.exception.ServiceException;
import com.bidr.llm.agent.sandbox.SandboxSeedFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Title: CubeSandboxProviderTest
 * Description: CubeSandboxProvider 全行为 fake 单测（本地回环 {@link CubeFakeServer}，不打外网）。
 * 钉：ensure 顺序红线（admin mkdir → POST /sandboxes → files write×N，🔴宿主目录必须先于
 * create——hostPath 不存在 create 130545 失败）；ensure 幂等（二次零 create、只补种子）；
 * D3 metadata 对账（新实例空 map＋GET /sandboxes 命中 ⇒ 不 create 直接补种子；exec/destroy
 * 同源寻址）；对账降级（列表 500 ⇒ warn 一行＋仍 create 成功）；fetch 三腿（../ 穿越拒/
 * 绝对路径拒/fake 404 ⇒ IAE 固定话术）；destroy 二连静默（无二次 DELETE）；exec 64KB 边界
 * （多字节字符劈切退刀，照 OpenHandsSandboxProvider.truncateUtf8 用例形态）。
 *
 * @author Sharp
 * @since 2026/11/16
 */
class CubeSandboxProviderTest {

    private static final String SID = CubeFakeServer.SID;

    private CubeFakeServer fake;
    private CubeSandboxProvider provider;

    @BeforeEach
    void startFake() throws IOException {
        fake = new CubeFakeServer();
        provider = provider();
    }

    @AfterEach
    void stopFake() {
        fake.stop();
    }

    /** 新 Provider 实例（空内存 map）——重启对账用例靠它复现 */
    private CubeSandboxProvider provider() {
        CubeHttpClient client = new CubeHttpClient(fake.properties);
        return new CubeSandboxProvider(client,
                new com.bidr.cube.admin.AdminSandboxManager(client, fake.properties),
                fake.properties);
    }

    private static List<SandboxSeedFile> seeds() {
        return List.of(
                new SandboxSeedFile("a.txt", "A".getBytes(StandardCharsets.UTF_8)),
                new SandboxSeedFile("dir/b.txt", "B".getBytes(StandardCharsets.UTF_8)));
    }

    // ---------------------------------------------------------------- ensure

    @Test
    @DisplayName("ensure 顺序红线：role 对账→create admin→mkdir（admin exec）→session-id 对账→POST /sandboxes→files write×N（🔴目录先于 create）")
    void ensureCallsInOrderMkdirCreateThenWrites() {
        provider.ensure(SID, seeds());

        assertEquals(List.of(
                "list",
                "create:admin",
                "exec:mkdir -p '/data/sandbox/" + SID + "'",
                "list",
                "create:" + SID,
                "write:/workspace/a.txt",
                "write:/workspace/dir/b.txt"), fake.ops,
                "全局调用序：宿主目录预创建必须先于业务 create（hostPath 不存在 ⇒ create 130545 失败）"
                        + "；管理沙箱 role 对账（M-2）与业务 session-id 对账（D3）各在 create 前一拍");
        assertEquals(1, fake.adminCreateBodies.size());
        assertEquals(1, fake.businessCreateBodies.size());
        assertEquals(2, fake.listCalls, "首次 ensure 两次对账：管理 role＋业务 session-id 各一次");
        assertArrayEquals("A".getBytes(StandardCharsets.UTF_8), fake.files.get("/workspace/a.txt"));
        assertArrayEquals("B".getBytes(StandardCharsets.UTF_8), fake.files.get("/workspace/dir/b.txt"));
    }

    @Test
    @DisplayName("ensure 幂等：同 sid 二连 ⇒ 第二次零 create、零对账，只重复 mkdir＋种子覆盖重传")
    void ensureIdempotentSecondCallSkipsCreateAndReconcile() {
        provider.ensure(SID, seeds());
        int opsAfterFirst = fake.ops.size();
        provider.ensure(SID, seeds());

        assertEquals(1, fake.businessCreateBodies.size(), "会话已建 ⇒ 不重建");
        assertEquals(2, fake.listCalls, "map 命中 ⇒ 不再对账（两次都是首次 ensure 的 role＋session-id）");
        assertEquals(opsAfterFirst + 3, fake.ops.size(), "第二次只补 mkdir＋2 笔种子写");
        assertTrue(fake.ops.get(opsAfterFirst).startsWith("exec:mkdir -p "),
                "第二次首条仍是宿主目录 mkdir（幂等 -p）");
        assertArrayEquals("A".getBytes(StandardCharsets.UTF_8), fake.files.get("/workspace/a.txt"),
                "种子覆盖重传＝最新为准（端口契约）");
    }

    @Test
    @DisplayName("D3 对账：新实例（空 map）＋GET /sandboxes 命中 session-id ⇒ 不 create 直接补种子")
    void ensureReconcilesExistingSandboxAfterRestart() {
        fake.listedSessions.add(SID);
        provider.ensure(SID, seeds());

        assertEquals(0, fake.businessCreateBodies.size(), "对账命中 ⇒ 不 create");
        assertEquals(2, fake.listCalls, "role（管理懒建对账）＋session-id（业务对账）各一次");
        assertEquals(1, fake.adminCreateBodies.size(), "管理沙箱照常懒建");
        assertTrue(fake.ops.contains("write:/workspace/a.txt"), "对账命中后照常补种子");
        assertFalse(fake.ops.contains("create:" + SID));
    }

    @Test
    @DisplayName("D3 对账降级：列表接口 500 ⇒ warn 一行＋仍 create 成功（行为不断）")
    void ensureDegradesToCreateWhenListFails() {
        Logger logger = (Logger) LoggerFactory.getLogger(CubeSandboxProvider.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            fake.listStatus = 500;
            provider.ensure(SID, seeds());
        } finally {
            logger.detachAppender(appender);
        }
        assertEquals(1, fake.businessCreateBodies.size(), "降级＝直接 create，行为不断");
        assertTrue(appender.list.stream().anyMatch(event ->
                        event.getLevel().toString().equals("WARN")
                                && event.getFormattedMessage().contains("对账失败")),
                "应有 warn 日志一行（对账失败，降级直接新建）");
    }

    // ---------------------------------------------------------------- exec

    @Test
    @DisplayName("exec 映射：exitCode/合并 stdout+stderr（stdout 在前）/timedOut；命令经 Connect 信封下发")
    void execMapsOutputAndMerge() {
        provider.ensure(SID, List.of());
        fake.setExecFrames(CubeFakeServer.stdoutFrame("out-1"),
                CubeFakeServer.stderrFrame("err-1"),
                CubeFakeServer.endFrameExit(7));

        com.bidr.llm.agent.sandbox.SandboxExecResult result =
                provider.exec(SID, "python scripts/make.py", 60_000);

        assertEquals(7, result.exitCode());
        assertEquals("out-1\nerr-1", result.output(), "stdout 在前、stderr 换行接后");
        assertFalse(result.timedOut());
        assertTrue(fake.execCommands.contains("cd '/workspace' && python scripts/make.py"),
                "exec 必须前置 cd /workspace（Cube 容器默认 cwd=/root，相对路径找不到工作区）: " + fake.execCommands);
        assertEquals(1, fake.businessCreateBodies.size(), "ensure 建的会话，exec 不再 create");
    }

    @Test
    @DisplayName("exec 工作区口径：任何命令都前置 cd '/workspace'（含已带 cd 的命令，后者自会覆盖）")
    void execPrefixesWorkspaceDirectory() {
        provider.ensure(SID, List.of());
        provider.exec(SID, "pwd", 10_000);
        provider.exec(SID, "cd /tmp && pwd", 10_000);

        assertTrue(fake.execCommands.contains("cd '/workspace' && pwd"), fake.execCommands.toString());
        assertTrue(fake.execCommands.contains("cd '/workspace' && cd /tmp && pwd"), fake.execCommands.toString());
    }

    @Test
    @DisplayName("exec 对账寻址：新实例空 map ⇒ metadata 对账命中既有沙箱后直接 exec，零 create")
    void execResolvesByReconcileWithoutCreate() {
        fake.listedSessions.add(SID);
        com.bidr.llm.agent.sandbox.SandboxExecResult result = provider.exec(SID, "true", 10_000);
        assertEquals(0, result.exitCode(), "缺省帧 exit status 0");
        assertEquals(0, fake.businessCreateBodies.size(), "exec 不懒建业务沙箱");
        assertEquals(1, fake.listCalls, "miss ⇒ 一次对账");
    }

    @Test
    @DisplayName("exec 会话不存在（对账空列表）：ServiceException 明确报错，不静默新建")
    void execThrowsWhenSessionMissing() {
        ServiceException e = assertThrows(ServiceException.class,
                () -> provider.exec("ghost-session", "true", 10_000));
        assertTrue(e.getMessage().contains("不存在"), e.getMessage());
        assertEquals(0, fake.businessCreateBodies.size());
    }

    @Test
    @DisplayName("exec timeoutMs≤0 ⇒ 兜底取 properties.timeout（秒→毫秒；超时置 timedOut）")
    void execFallsBackToConfiguredTimeout() {
        fake.listedSessions.add(SID);
        fake.properties.setTimeout(1); // 兜底超时 1000ms
        fake.execDelayMs = 1500;       // fake 延迟 1500ms

        com.bidr.llm.agent.sandbox.SandboxExecResult timedOut =
                provider.exec(SID, "sleep 999", 0);
        assertTrue(timedOut.timedOut(), "兜底超时应生效");
        assertEquals(-1, timedOut.exitCode());
        assertEquals("", timedOut.output());

        fake.execDelayMs = 0;
        com.bidr.llm.agent.sandbox.SandboxExecResult ok =
                provider.exec(SID, "true", 10_000);
        assertFalse(ok.timedOut(), "显式超时覆盖兜底");
    }

    @Test
    @DisplayName("非正时长误配防护（M-5）：properties.timeout=0/-5 ⇒ exec/mkdir 不抛 Invalid duration，兜底 clamp 到 1000ms")
    void nonPositiveConfigTimeoutDoesNotProduceInvalidDuration() {
        fake.properties.setTimeout(0);
        fake.listedSessions.add(SID);
        // exec：timeoutMs=0 且配置 0 ⇒ 兜底 max(1000, 0*1000)=1000ms（0 透传 Duration 会抛 IAE）
        com.bidr.llm.agent.sandbox.SandboxExecResult result = provider.exec(SID, "true", 0);
        assertFalse(result.timedOut(), "clamp 后 1000ms 兜底超时，fake 零延迟 ⇒ 正常回");

        // mkdir 侧同口径（AdminSandboxManager.adminExecTimeoutMs clamp）：ensure 全链走通即证不抛
        fake.properties.setTimeout(-5);
        provider.ensure(SID, List.of());
        assertTrue(fake.execCommands.stream().anyMatch(cmd -> cmd.startsWith("mkdir -p ")),
                "mkdir 经 clamp 后正常下发");
    }

    @Test
    @DisplayName("exec 64KB 边界：多字节字符劈切退刀到字符边界，截断标记追加，UTF-8 回环合法")
    void execTruncatesMultibyteOutputAtUtf8Boundary() {
        provider.ensure(SID, List.of());
        // x*65535（65535B）＋中（3B）＝65538B > 64KB：65536 切点劈中字 ⇒ 退刀 65535
        String big = "x".repeat(64 * 1024 - 1) + "中";
        fake.setExecFrames(CubeFakeServer.stdoutFrame(big),
                CubeFakeServer.endFrameExit(0));

        com.bidr.llm.agent.sandbox.SandboxExecResult result = provider.exec(SID, "cat big", 60_000);

        assertTrue(result.output().endsWith(CubeSandboxProvider.TRUNCATION_NOTE));
        String head = result.output()
                .replace(CubeSandboxProvider.TRUNCATION_NOTE, "");
        assertEquals("x".repeat(64 * 1024 - 1), head, "退刀后恰剩全 x 前缀（中 字整体不出现在输出）");
        assertEquals(64 * 1024 - 1, head.getBytes(StandardCharsets.UTF_8).length,
                "截断后不超 64KB 且在预算内尽量保留");
        assertEquals(head.length(), new String(head.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8).length,
                "截断点为合法 UTF-8（回环等长）");
    }

    @Test
    @DisplayName("合并与截断纯函数：stdout/stderr 空缺不造多余换行；64KB 内原文直回；恰好 64KB 不截")
    void mergeAndTruncateEdgeCases() {
        assertEquals("err", CubeSandboxProvider.mergeOutput(null, "err"));
        assertEquals("out", CubeSandboxProvider.mergeOutput("out", ""));
        assertEquals("", CubeSandboxProvider.mergeOutput(null, null));
        assertEquals("out\nerr", CubeSandboxProvider.mergeOutput("out", "err"));
        String within = "x".repeat(64 * 1024 - 1);
        assertEquals(within, CubeSandboxProvider.truncateUtf8(within));
        String exact = "x".repeat(64 * 1024);
        assertEquals(exact, CubeSandboxProvider.truncateUtf8(exact), "恰好 64KB ⇒ 原文直回不截");
    }

    // ---------------------------------------------------------------- fetch

    @Test
    @DisplayName("fetch roundtrip：相对路径拼 /workspace 前缀回字节")
    void fetchRoundtrip() {
        provider.ensure(SID, seeds());
        byte[] bytes = provider.fetch(SID, "dir/b.txt");
        assertArrayEquals("B".getBytes(StandardCharsets.UTF_8), bytes);
        assertTrue(fake.ops.contains("read:/workspace/dir/b.txt"),
                "fetch 寻址＝/workspace/<relPath>（与种子写入同挂载点）");
    }

    @Test
    @DisplayName("fetch 路径四拒：空／绝对路径／.. 穿越段／. 或空段 ⇒ IAE「沙箱路径非法：」人话（不带拒绝前缀——工具层统一加）")
    void fetchRejectsIllegalPaths() {
        assertReject("ghost", "", "路径为空");
        assertReject("ghost", "/etc/passwd", "必须是相对路径：/etc/passwd");
        assertReject("ghost", "../escape.txt", "含非法路径段：../escape.txt");
        assertReject("ghost", "a/./b", "含非法路径段：a/./b");
        assertReject("ghost", "a//b", "含非法路径段：a//b");
        assertEquals(0, fake.listCalls, "路径校验先于寻址（非法路径零请求）");
    }

    private void assertReject(String session, String relPath, String reasonPart) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> provider.fetch(session, relPath), "应拒绝：" + relPath);
        assertEquals("沙箱路径非法：" + reasonPart, e.getMessage(),
                "人话固定话术（端口契约：工具层转「拒绝：…」给模型）");
    }

    @Test
    @DisplayName("fetch 文件不存在（readFile HTTP 404）：IAE 固定话术「文件不存在于沙箱（请先 sandbox_run ls 确认）」")
    void fetchMissingFileBecomesFixedPhraseIAE() {
        provider.ensure(SID, List.of());
        fake.readFileStatus = 404;
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> provider.fetch(SID, "out/nope.txt"));
        assertEquals("文件不存在于沙箱（请先 sandbox_run ls 确认）", e.getMessage(),
                "固定人话（自纠指引），上游响应体不进模型上下文");
    }

    @Test
    @DisplayName("fetch 上游 5xx：保持 ServiceException 原样上抛（取件失败≠路径非法，工具层走稍后重试）")
    void fetchServerErrorStaysServiceException() {
        provider.ensure(SID, List.of());
        fake.readFileStatus = 500;
        ServiceException e = assertThrows(ServiceException.class,
                () -> provider.fetch(SID, "out/x.txt"));
        assertTrue(e.getMessage().contains("500"), e.getMessage());
    }

    @Test
    @DisplayName("fetch 误判防护（M-1）：relPath 含「HTTP 404」字样＋真实失败 5xx ⇒ ServiceException 原样，不误判文件不存在")
    void fetchPathContaining404TextDoesNotMaskServerError() {
        provider.ensure(SID, List.of());
        fake.readFileStatus = 500;
        ServiceException e = assertThrows(ServiceException.class,
                () -> provider.fetch(SID, "out/HTTP 404.txt"));
        assertTrue(e.getMessage().startsWith("readFile 失败 HTTP 500:"),
                "固定前缀＋path 只在 detail 段（contains 判定会被路径字样击穿）: " + e.getMessage());
    }

    // ---------------------------------------------------------------- put / putIfAlive

    @Test
    @DisplayName("put 成功：ensure → exec test -e（不存在）→ write 原件字节，返回 input/a.txt")
    void putWritesWhenPathFree() {
        CubeHttpClient client = new CubeHttpClient(fake.properties);
        CubeSandboxProvider provider = new CubeSandboxProvider(client,
                mock(AdminSandboxManager.class), fake.properties);
        // 管理面 mkdir 已 mock；唯一 exec 是存在性检查，exit 1＝不存在
        fake.setExecFrames(CubeFakeServer.endFrameExit(1));
        byte[] bytes = "DOCX".getBytes(StandardCharsets.UTF_8);

        String actual = provider.put(SID, "input/a.txt", bytes);

        assertEquals("input/a.txt", actual);
        assertTrue(fake.ops.contains("exec:test -e '/workspace/input/a.txt'"), fake.ops.toString());
        assertTrue(fake.ops.contains("write:/workspace/input/a.txt"), fake.ops.toString());
        assertArrayEquals(bytes, fake.files.get("/workspace/input/a.txt"));
    }

    @Test
    @DisplayName("put 同名改名：exec 第一次 exit 0（占用）第二次 exit 1（可用）⇒ 写 input/a-2.txt 并返回")
    void putRenamesWhenPathOccupied() {
        CubeHttpClient client = mock(CubeHttpClient.class);
        AdminSandboxManager admin = mock(AdminSandboxManager.class);
        when(client.listSandboxIds("session-id", SID)).thenReturn(List.of(SID));
        when(client.exec(eq(SID), startsWith("test -e "), eq(10_000L)))
                .thenReturn(new CubeHttpClient.ExecResult(0, "", "", false),
                        new CubeHttpClient.ExecResult(1, "", "", false));
        CubeSandboxProvider provider = new CubeSandboxProvider(client, admin, new CubeSandboxProperties());
        byte[] bytes = "DOCX".getBytes(StandardCharsets.UTF_8);

        String actual = provider.put(SID, "input/a.txt", bytes);

        assertEquals("input/a-2.txt", actual);
        verify(client).exec(SID, "test -e '/workspace/input/a.txt'", 10_000L);
        verify(client).exec(SID, "test -e '/workspace/input/a-2.txt'", 10_000L);
        ArgumentCaptor<byte[]> content = ArgumentCaptor.forClass(byte[].class);
        verify(client).writeFile(eq(SID), eq("/workspace/input/a-2.txt"), content.capture());
        assertArrayEquals(bytes, content.getValue());
    }

    @Test
    @DisplayName("putIfAlive 无沙箱：返回 false 且零请求零 create（绝不 ensure/create）")
    void putIfAliveWithoutSandboxDoesNotCreate() {
        CubeHttpClient client = new CubeHttpClient(fake.properties);
        AdminSandboxManager admin = mock(AdminSandboxManager.class);
        CubeSandboxProvider provider = new CubeSandboxProvider(client, admin, fake.properties);

        assertFalse(provider.putIfAlive(SID, "input/a.txt", "X".getBytes(StandardCharsets.UTF_8)));

        assertTrue(fake.ops.isEmpty(), "零 HTTP 调用: " + fake.ops);
        assertEquals(0, fake.businessCreateBodies.size(), "沙箱不存在 ⇒ 绝不 create");
        assertEquals(0, fake.listCalls, "也不做对账");
        verifyNoInteractions(admin);
    }

    @Test
    @DisplayName("putIfAlive 有沙箱：直接写入返回 true（同名覆盖语义，不做存在性检查）")
    void putIfAliveWritesWhenPathFree() {
        CubeHttpClient client = new CubeHttpClient(fake.properties);
        CubeSandboxProvider provider = new CubeSandboxProvider(client,
                mock(AdminSandboxManager.class), fake.properties);
        provider.ensure(SID, List.of());
        int opsBefore = fake.ops.size();
        byte[] bytes = "X".getBytes(StandardCharsets.UTF_8);

        assertTrue(provider.putIfAlive(SID, "input/a.txt", bytes));

        assertArrayEquals(bytes, fake.files.get("/workspace/input/a.txt"));
        assertEquals(opsBefore + 1, fake.ops.size(), "只有一次 write（无 test -e 探测）: " + fake.ops);
        assertTrue(fake.ops.get(opsBefore).startsWith("write:/workspace/input/a.txt"), fake.ops.toString());
    }

    @Test
    @DisplayName("putIfAlive 路径已存在：同名覆盖为最新字节并返回 true（上传工作副本语义，不跳过）")
    void putIfAliveOverwritesWhenPathOccupied() {
        CubeHttpClient client = new CubeHttpClient(fake.properties);
        CubeSandboxProvider provider = new CubeSandboxProvider(client,
                mock(AdminSandboxManager.class), fake.properties);
        provider.ensure(SID, List.of());
        byte[] old = "OLD".getBytes(StandardCharsets.UTF_8);
        fake.files.put("/workspace/input/a.txt", old);
        int opsBefore = fake.ops.size();
        byte[] fresh = "NEW".getBytes(StandardCharsets.UTF_8);

        assertTrue(provider.putIfAlive(SID, "input/a.txt", fresh));

        assertEquals(opsBefore + 1, fake.ops.size(), "只多一次 write（无 exists 探测）: " + fake.ops);
        assertTrue(fake.ops.get(opsBefore).startsWith("write:/workspace/input/a.txt"), fake.ops.toString());
        assertArrayEquals(fresh, fake.files.get("/workspace/input/a.txt"), "同名必须覆盖为最新字节");
    }

    @Test
    @DisplayName("putIfAlive 路径非法：折算 false 且零 write（best-effort 通道不阻断上传）")
    void putIfAliveRejectsIllegalPathAsFalse() {
        CubeHttpClient client = mock(CubeHttpClient.class);
        when(client.listSandboxIds("session-id", SID)).thenReturn(List.of(SID));
        CubeSandboxProvider provider = new CubeSandboxProvider(client,
                mock(AdminSandboxManager.class), new CubeSandboxProperties());
        provider.ensure(SID, List.of());

        assertFalse(provider.putIfAlive(SID, "a//b", new byte[]{1}));

        verify(client, never()).writeFile(anyString(), anyString(), any(byte[].class));
    }

    @Test
    @DisplayName("put 路径含单引号：shellQuote 用 '\\'' 转义，exec 收到完整单参数")
    void putQuotesSingleQuoteInPath() {
        CubeHttpClient client = mock(CubeHttpClient.class);
        when(client.listSandboxIds("session-id", SID)).thenReturn(List.of(SID));
        when(client.exec(eq(SID), startsWith("test -e "), eq(10_000L)))
                .thenReturn(new CubeHttpClient.ExecResult(1, "", "", false));
        CubeSandboxProvider provider = new CubeSandboxProvider(client,
                mock(AdminSandboxManager.class), new CubeSandboxProperties());

        String actual = provider.put(SID, "input/O'Brien.docx", new byte[]{1});

        assertEquals("input/O'Brien.docx", actual);
        verify(client).exec(SID, "test -e '/workspace/input/O'\\''Brien.docx'", 10_000L);
        verify(client).writeFile(SID, "/workspace/input/O'Brien.docx", new byte[]{1});
    }

    @Test
    @DisplayName("put 改名边界：无扩展名 a→a-2；多点名 a.b.c→a.b-2.c")
    void putRenamesWithoutExtensionAndWithMultiDot() {
        assertRenamed("input/a", "input/a-2");
        assertRenamed("input/a.b.c", "input/a.b-2.c");
    }

    /** 目标被占用、候选可用（exec exit 0 → 1）⇒ 返回/写入改名后的同目录落点 */
    private void assertRenamed(String requested, String expected) {
        CubeHttpClient client = mock(CubeHttpClient.class);
        when(client.listSandboxIds("session-id", SID)).thenReturn(List.of(SID));
        when(client.exec(eq(SID), startsWith("test -e "), eq(10_000L)))
                .thenReturn(new CubeHttpClient.ExecResult(0, "", "", false),
                        new CubeHttpClient.ExecResult(1, "", "", false));
        CubeSandboxProvider provider = new CubeSandboxProvider(client,
                mock(AdminSandboxManager.class), new CubeSandboxProperties());
        byte[] bytes = new byte[]{1};

        assertEquals(expected, provider.put(SID, requested, bytes));

        verify(client).writeFile(SID, "/workspace/" + expected, bytes);
    }

    @Test
    @DisplayName("put 非法路径：在 ensure/任何 HTTP 之前抛 IAE（零副作用）")
    void putRejectsIllegalPathBeforeEnsure() {
        CubeHttpClient client = mock(CubeHttpClient.class);
        AdminSandboxManager admin = mock(AdminSandboxManager.class);
        CubeSandboxProvider provider = new CubeSandboxProvider(client, admin, new CubeSandboxProperties());

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> provider.put(SID, "../escape.txt", new byte[]{1}));

        assertTrue(e.getMessage().contains("含非法路径段"), e.getMessage());
        verifyNoInteractions(client, admin);
    }

    // ---------------------------------------------------------------- destroy

    @Test
    @DisplayName("destroy 幂等：首次 DELETE＋map 逐出；二连（map miss＋对账空）静默零二次 DELETE")
    void destroyTwiceSendsSingleDelete() {
        provider.ensure(SID, List.of());
        provider.destroy(SID);
        assertEquals(1, fake.businessDeletes, "首次销毁恰好一次 DELETE");

        provider.destroy(SID);
        assertEquals(1, fake.businessDeletes, "二连：对账空列表＝本就不存在 ⇒ 静默，无二次 DELETE");
    }

    @Test
    @DisplayName("destroy 重启后对账寻址：空 map＋GET /sandboxes 命中 ⇒ DELETE 到对账到的实例（宿主目录不删）")
    void destroyAfterRestartResolvesByReconcile() {
        fake.listedSessions.add(SID);
        provider.destroy(SID);
        assertTrue(fake.ops.contains("delete:" + SID), "对账到的实例照删（404 静默在 client 内）");
        assertTrue(fake.ops.stream().noneMatch(op -> op.startsWith("exec:rm")),
                "宿主目录不删（归档策略后续批次定）");
    }

    // ---------------------------------------------------------------- 装配形状（确保 Provider 实现端口）

    @Test
    @DisplayName("种子 relPath 同一把安全尺：穿越种子在 ensure 即拒（不落 write）")
    void ensureRejectsCrossingSeedPath() {
        List<SandboxSeedFile> bad = List.of(new SandboxSeedFile("../evil.txt", new byte[0]));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> provider.ensure(SID, bad));
        assertTrue(e.getMessage().contains("含非法路径段"), e.getMessage());
        assertEquals(0, fake.ops.stream().filter(op -> op.startsWith("write:")).count(),
            "非法种子未落盘");
    }
}
