package com.bidr.parse;

import com.bidr.blob.BlobStore;
import com.bidr.document.DocumentProcessor;
import com.bidr.document.spi.AsyncTranscribeLeg;
import com.bidr.document.spi.ConvertStep;
import com.bidr.task.AsyncStageScope;
import com.bidr.task.AsyncTaskService;
import com.bidr.task.StageOutcome;
import com.bidr.task.dao.entity.SaAsyncTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 解析编排引擎离线单测（M17 棒 2/3 判据）：断点腿（键已存在→秒级 done）、D-11 裁剪键
 * （闭集减法/未知值快败/缺本体快败）、直通/内置/伪异步腿三态、等待超预算清单再抛（A8）、
 * ARTIFACTS 快照交付（观察者取键回写业务行的取口）。
 *
 * @author Sharp
 * @since 2026-10-21
 */
class ParseEngineFlowTest {

    private static final String SHA = "a1b2c3d4e5f6a7b8";

    /** 测试自有 scheme（中性值）：协议值业务所有，core 不内置字面量 */
    private static final String SCHEME = "test-image://";

    private static final String RAW_KEY = "raw/" + SHA + ".md";

    private final BlobStore storage = mock(BlobStore.class);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<DocumentProcessor> processor = mock(ObjectProvider.class);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<ConvertStep> convertStep = mock(ObjectProvider.class);

    private final DocumentProcessor documentProcessor = mock(DocumentProcessor.class);

    private final ParseArtifactWriter writer = new DefaultParseArtifactWriter(storage, SCHEME);

    private final PageImageRenderer pageImageRenderer = new PageImageRenderer(storage);

    private final AsyncTaskService asyncTaskService = mock(AsyncTaskService.class);

    private SaAsyncTask currentTask;

    private ParseEngineFlow flow;

    @BeforeEach
    void setUp() {
        when(processor.getIfAvailable()).thenReturn(documentProcessor);
        when(convertStep.getIfAvailable()).thenReturn(null);
        flow = new ParseEngineFlow(storage, processor, convertStep,
                Collections.<AsyncTranscribeLeg>emptyList(), "", 3_600_000L,
                writer, new TextPassthroughService(storage, "", SCHEME), pageImageRenderer,
                Collections.<ParseEnrichLeg>emptyList());
        stubScopeMirror();
    }

    /** scope 写穿用的假底盘（ParseFlowTest 同款）：writeExternal/clearExternal 镜像进内存行 */
    private void stubScopeMirror() {
        doAnswer(inv -> {
            SaAsyncTask t = inv.getArgument(0);
            String externalId = inv.getArgument(1);
            String externalStatus = inv.getArgument(2);
            String snapshotJson = inv.getArgument(3);
            if (externalId != null) {
                t.setExternalId(externalId);
            }
            if (externalStatus != null) {
                t.setExternalStatus(externalStatus);
            }
            if (snapshotJson != null) {
                t.setExternalSnapshot(snapshotJson);
            }
            return null;
        }).when(asyncTaskService).writeExternal(any(), any(), any(), any());
        doAnswer(inv -> {
            SaAsyncTask t = inv.getArgument(0);
            t.setExternalId(null);
            t.setExternalStatus(null);
            t.setExternalSnapshot(null);
            return null;
        }).when(asyncTaskService).clearExternal(any());
    }

    private ParseEngineFlow flowWithLeg(AsyncTranscribeLeg leg) {
        return new ParseEngineFlow(storage, processor, convertStep,
                Collections.singletonList(leg), "", 3_600_000L,
                writer, new TextPassthroughService(storage, "", SCHEME), pageImageRenderer,
                Collections.<ParseEnrichLeg>emptyList());
    }

    // ==================== D-11 裁剪键 ====================

    @Test
    void artifactsDefaultsToFullSet() {
        assertEquals(new java.util.LinkedHashSet<>(Arrays.asList(ParseEngineFlow.ARTIFACT_MARKDOWN,
                ParseEngineFlow.ARTIFACT_PAGE_MAP, ParseEngineFlow.ARTIFACT_PAGE_IMAGE)),
                ParseEngineFlow.requestedArtifacts(null, "b"));
    }

    @Test
    void artifactsTrimsPageImageOnly() {
        assertEquals(new java.util.LinkedHashSet<>(Arrays.asList(ParseEngineFlow.ARTIFACT_MARKDOWN,
                ParseEngineFlow.ARTIFACT_PAGE_MAP)),
                ParseEngineFlow.requestedArtifacts("[\"markdown\",\"pageMap\"]", "b"));
    }

    @Test
    void artifactsRejectsUnknownKeyAndMissingEssentials() {
        assertThrows(ParseContractException.class,
                () -> ParseEngineFlow.requestedArtifacts("[\"markdown\",\"thumb\"]", "b"));
        assertThrows(ParseContractException.class,
                () -> ParseEngineFlow.requestedArtifacts("[\"markdown\"]", "b"));
        assertThrows(ParseContractException.class,
                () -> ParseEngineFlow.requestedArtifacts("markdown", "b"));
    }

    // ==================== 断点腿（D-2） ====================

    @Test
    void textFileBreakpointReusesWhenMarkdownExists() {
        byte[] source = "# 笔记".getBytes(StandardCharsets.UTF_8);
        String sha = com.bidr.blob.Digests.sha256Hex(source);
        task("INTAKE", null, "{\"sourceKey\":\"" + RAW_KEY + "\",\"fileName\":\"note.md\",\"variant\":1}");
        when(storage.get(RAW_KEY)).thenReturn(source);
        when(storage.get(BlobStore.parsedMarkdownKey(sha, 1))).thenReturn(source);

        StageOutcome outcome = flow.intake(currentTask, scope());

        assertTrue(outcome.getKind() == StageOutcome.Kind.DONE, "md 已在＝秒级复用（非 office 件 INTAKE 即判）");
        Map<String, Object> snapshot = snapshotOfCurrent();
        assertEquals(sha, text(snapshot, "anchorSha"), "快照带锚 sha（观察者取键回写的坐标）");
        assertEquals("REUSED", currentTask.getExternalStatus());
        verify(storage, never()).put(anyString(), any(), anyString());
    }

    @Test
    void textFileWithoutBreakpointRunsPassthroughAndDeliversArtifacts() throws Exception {
        byte[] source = "# 标题\n\n正文段落。".getBytes(StandardCharsets.UTF_8);
        String sha = com.bidr.blob.Digests.sha256Hex(source);
        task("INTAKE", null, "{\"sourceKey\":\"" + RAW_KEY + "\",\"fileName\":\"note.md\",\"variant\":1}");
        when(storage.get(RAW_KEY)).thenReturn(source);
        when(storage.get(BlobStore.parsedMarkdownKey(sha, 1))).thenReturn(null);

        StageOutcome intake = flow.intake(currentTask, scope());
        assertEquals(StageOutcome.Kind.ADVANCE, intake.getKind());

        StageOutcome transcribe = flow.transcribe(currentTask, scope());
        assertEquals(StageOutcome.Kind.ADVANCE, transcribe.getKind());
        Map<String, Object> snapshot = snapshotOfCurrent();
        assertEquals(ParseEngineFlow.ROUTE_PASSTHROUGH, text(snapshot, "route"));
        // 直通件无 pagemap：快照该字段为 null（下游判空降级）
        assertNull(text(snapshot, "pageMapKey"));
        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(storage, atLeastOnce()).put(keys.capture(), any(), anyString());
        assertTrue(keys.getAllValues().contains("parsed/" + sha + "/v1.md"), "正文落标准键形");

        StageOutcome persist = flow.persist(currentTask, scope());
        assertEquals(StageOutcome.Kind.ADVANCE, persist.getKind());
        snapshot = snapshotOfCurrent();
        assertEquals("ARTIFACTS", currentTask.getExternalStatus(), "PERSIST 落 ARTIFACTS 交付快照");
        assertEquals("parsed/" + sha + "/v1.md", text(snapshot, "markdownKey"));
    }

    @Test
    void builtinLegUsedWhenNoAsyncLegConfigured() throws Exception {
        byte[] pdf = "%PDF-1.4 fake".getBytes(StandardCharsets.UTF_8);
        String sha = com.bidr.blob.Digests.sha256Hex(pdf);
        String pdfKey = "raw/" + sha + ".pdf";
        task("INTAKE", null, "{\"sourceKey\":\"" + pdfKey + "\",\"fileName\":\"doc.pdf\",\"variant\":2}");
        when(storage.get(pdfKey)).thenReturn(pdf);
        when(storage.get(BlobStore.parsedMarkdownKey(sha, 2))).thenReturn(null);
        com.bidr.document.RouteTrace trace = com.bidr.document.RouteTrace.builder()
                .transcribeLeg(com.bidr.document.RouteTrace.BUILTIN).build();
        when(documentProcessor.process(any(), anyString(), any(), anyBoolean()))
                .thenReturn(new com.bidr.document.DocumentArtifact("# 内置正文", trace));

        flow.intake(currentTask, scope());
        StageOutcome outcome = flow.transcribe(currentTask, scope());
        assertEquals(StageOutcome.Kind.ADVANCE, outcome.getKind());
        verify(storage, atLeastOnce()).put(eq("parsed/" + sha + "/v2.md"), any(), anyString());
        Map<String, Object> snapshot = snapshotOfCurrent();
        assertEquals(ParseEngineFlow.ROUTE_BUILTIN, text(snapshot, "route"));
        assertNull(text(snapshot, "pageMapKey"), "内置腿无页码映射（引用降级无页）");
    }

    // ==================== keyNamespace（temp/ 临时轨，2026-11-17） ====================

    /** 直通件带 keyNamespace：md 落前缀键；同内容断点回查也在前缀键上命中（不走主命名空间） */
    @Test
    void namespacedTextFileWritesPrefixedMarkdownAndBreakpointReusesThere() throws Exception {
        byte[] source = "# 临时材料".getBytes(StandardCharsets.UTF_8);
        String sha = com.bidr.blob.Digests.sha256Hex(source);
        task("INTAKE", null, "{\"sourceKey\":\"" + RAW_KEY + "\",\"fileName\":\"note.md\",\"variant\":0,"
                + "\"keyNamespace\":\"temp/material\"}");
        when(storage.get(RAW_KEY)).thenReturn(source);
        when(storage.get(BlobStore.namespaced("temp/material", BlobStore.parsedMarkdownKey(sha, 0))))
                .thenReturn(null);

        flow.intake(currentTask, scope());
        StageOutcome transcribe = flow.transcribe(currentTask, scope());
        assertEquals(StageOutcome.Kind.ADVANCE, transcribe.getKind());
        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(storage, atLeastOnce()).put(keys.capture(), any(), anyString());
        assertTrue(keys.getAllValues().contains("temp/material/parsed/" + sha + "/v0.md"),
                "直通 md 键须挂 namespace 前缀");
        boolean touchedMain = keys.getAllValues().stream().anyMatch(k -> k.startsWith("parsed/"));
        assertFalse(touchedMain, "主命名空间不得出现同名键（KB 键空间零污染）");

        // 断点腿：namespaced md 已在 ⇒ INTAKE 秒级复用（peek 在前缀键上命中）
        task("INTAKE", null, "{\"sourceKey\":\"" + RAW_KEY + "\",\"fileName\":\"note.md\",\"variant\":0,"
                + "\"keyNamespace\":\"temp/material\"}");
        when(storage.get(BlobStore.namespaced("temp/material", BlobStore.parsedMarkdownKey(sha, 0))))
                .thenReturn(source);
        StageOutcome reuse = flow.intake(currentTask, scope());
        assertTrue(reuse.getKind() == StageOutcome.Kind.DONE, "前缀键在＝秒级复用");
        assertEquals("REUSED", currentTask.getExternalStatus());
    }

    /** office 件带 keyNamespace：convert 缓存落前缀键（temp 轨随材料清理，不污染主命名空间缓存） */
    @Test
    void namespacedOfficeConvertCachesUnderPrefix() throws Exception {
        byte[] docx = "PK-fake-docx".getBytes(StandardCharsets.UTF_8);
        byte[] pdf = "%PDF-1.4 converted".getBytes(StandardCharsets.UTF_8);
        String sourceSha = com.bidr.blob.Digests.sha256Hex(docx);
        String pdfSha = com.bidr.blob.Digests.sha256Hex(pdf);
        String sourceKey = "temp/material/raw/" + sourceSha + ".docx";
        ConvertStep step = mock(ConvertStep.class);
        when(step.isConvertible("material.docx")).thenReturn(true);
        when(step.convertToPdf(eq("material.docx"), any(byte[].class))).thenReturn(pdf);
        when(convertStep.getIfAvailable()).thenReturn(step);
        task("INTAKE", null, "{\"sourceKey\":\"" + sourceKey + "\",\"fileName\":\"material.docx\","
                + "\"variant\":0,\"keyNamespace\":\"temp/material\"}");
        when(storage.get(sourceKey)).thenReturn(docx);
        when(storage.get(BlobStore.namespaced("temp/material", BlobStore.convertedPdfKey(sourceSha))))
                .thenReturn(null);

        flow.intake(currentTask, scope());
        StageOutcome convert = flow.convert(currentTask, scope());
        assertEquals(StageOutcome.Kind.ADVANCE, convert.getKind());
        verify(storage).put(eq("temp/material/convert/" + sourceSha + ".pdf"), eq(pdf), anyString());
        Map<String, Object> snapshot = snapshotOfCurrent();
        assertEquals("temp/material/convert/" + sourceSha + ".pdf", text(snapshot, "anchorKey"),
                "锚键须为前缀形（后续 anchorFileName 比较同形才对得上）");
        assertEquals(pdfSha, text(snapshot, "anchorSha"));
    }

    /** keyNamespace 非法段（路径注入尝试）在受理口快败，不进跑批 */
    @Test
    void keyNamespaceRejectsUnsafeSegment() {
        task("INTAKE", null, "{\"sourceKey\":\"raw/x.md\",\"fileName\":\"x.md\",\"variant\":0,"
                + "\"keyNamespace\":\"temp/../etc\"}");
        assertThrows(ParseContractException.class, () -> flow.intake(currentTask, scope()));
    }

    // ==================== 伪异步腿（submit/poll/fetch 三拍） ====================

    @Test
    void externalLegSubmitsThenPollsThenFetches() {
        FakeAsyncLeg leg = new FakeAsyncLeg();
        flow = flowWithLeg(leg);
        byte[] pdf = "%PDF-1.4 fake".getBytes(StandardCharsets.UTF_8);
        String sha = com.bidr.blob.Digests.sha256Hex(pdf);
        String pdfKey = "raw/" + sha + ".pdf";
        task("INTAKE", null, "{\"sourceKey\":\"" + pdfKey + "\",\"fileName\":\"doc.pdf\",\"variant\":1}");
        when(storage.get(pdfKey)).thenReturn(pdf);
        when(storage.get(BlobStore.parsedMarkdownKey(sha, 1))).thenReturn(null);
        when(storage.get(BlobStore.pageMapKey(sha, 1))).thenReturn(null);

        flow.intake(currentTask, scope());
        StageOutcome submitOutcome = flow.transcribe(currentTask, scope());
        assertEquals(StageOutcome.Kind.ADVANCE, submitOutcome.getKind(), "提交即返回（原地推进等下轮）");
        assertEquals("ext-1", currentTask.getExternalId(), "externalId 写任务行（跨阶段载体）");
        assertEquals("SUBMITTED", currentTask.getExternalStatus());
        Map<String, Object> snapshot = snapshotOfCurrent();
        assertEquals("cloud", text(snapshot, "subRoute"), "子路线进快照（轮询/取件按原串寻址）");

        // RUNNING → pending 退避（不占线程不占租约）
        leg.pollStatus = "RUNNING";
        StageOutcome waiting = flow.transcribe(currentTask, scope());
        assertEquals(StageOutcome.Kind.PENDING, waiting.getKind());
        assertTrue(waiting.getBackoffMs() > 0);

        // COMPLETED → fetch → 产物落库 → PERSIST
        leg.pollStatus = "COMPLETED";
        StageOutcome done = flow.transcribe(currentTask, scope());
        assertEquals(StageOutcome.Kind.ADVANCE, done.getKind());
        verify(storage).put(eq("parsed/" + sha + "/v1.md"), any(), anyString());
        verify(storage).put(eq("pagemap/" + sha + "/v1.json"), any(), anyString());
        snapshot = snapshotOfCurrent();
        assertEquals("parsed/" + sha + "/v1.md", text(snapshot, "markdownKey"));
        assertEquals("pagemap/" + sha + "/v1.json", text(snapshot, "pageMapKey"));

        // 等待超预算：清外部单再抛（A8）
        task("TRANSCRIBE", "ext-1", "{\"sourceKey\":\"" + pdfKey + "\",\"fileName\":\"doc.pdf\",\"variant\":1}");
        currentTask.setExternalSnapshot("{\"subRoute\":\"cloud\",\"since\":1,\"sourceSha\":\"" + sha
                + "\",\"anchorSha\":\"" + sha + "\",\"anchorKey\":\"" + pdfKey + "\"}");
        ParseEngineFlow tightFlow = new ParseEngineFlow(storage, processor, convertStep,
                Collections.<AsyncTranscribeLeg>singletonList(leg), "", 1L,
                writer, new TextPassthroughService(storage, "", SCHEME), pageImageRenderer,
                Collections.<ParseEnrichLeg>emptyList());
        try {
            setLong(tightFlow, "mineruMaxWaitMs", 1L);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        assertThrows(IllegalStateException.class, () -> tightFlow.transcribe(currentTask, scope()));
        assertNull(currentTask.getExternalId(), "超预算抛之前必须清外部单（A8：否则重试仍轮询那张单）");
    }

    /** 伪异步腿：提交记 cloud 路线，轮询返回可切换状态，产物给最小 md＋content_list */
    private static final class FakeAsyncLeg implements AsyncTranscribeLeg {

        private String pollStatus = "RUNNING";

        @Override
        public String legName() {
            return "fake";
        }

        @Override
        public boolean hasLeg() {
            return true;
        }

        @Override
        public Submission submit(byte[] content, String filename, boolean extractImages) {
            return new Submission("cloud", "ext-1", System.currentTimeMillis());
        }

        @Override
        public Progress poll(String route, String externalId) {
            return new Progress(pollStatus, null, null, "FAILED".equals(pollStatus) ? "boom" : null);
        }

        @Override
        public Artifact fetch(String route, String externalId) {
            String markdown = "# 正文\n\n第一段。";
            String contentList = "[{\"type\":\"text\",\"text\":\"第一段。\",\"page_idx\":0}]";
            return new Artifact(markdown, contentList, Collections.<Image>emptyList());
        }
    }

    // ==================== payload 契约 ====================

    @Test
    void payloadWithoutSourceKeyFailsFast() {
        task("INTAKE", null, "{\"fileName\":\"x.md\",\"variant\":1}");
        assertThrows(ParseContractException.class, () -> flow.intake(currentTask, scope()));
    }

    @Test
    void missingSourceObjectFailsFastNotRetryable() {
        task("INTAKE", null, "{\"sourceKey\":\"" + RAW_KEY + "\",\"fileName\":\"x.md\",\"variant\":1}");
        when(storage.get(RAW_KEY)).thenReturn(null);
        ParseContractException e = assertThrows(ParseContractException.class,
                () -> flow.intake(currentTask, scope()));
        assertTrue(!e.isRetryable(), "源件不存在＝重跑结果不变，直接 DEAD 不进重试预算");
    }

    // ==================== 辅助 ====================

    private void task(String stage, String externalId, String payload) {
        SaAsyncTask t = new SaAsyncTask();
        t.setId(77L);
        t.setTaskType(ParseEngineFlow.TYPE);
        t.setBizRef("parse:1:2");
        t.setPayload(payload);
        t.setStatus("RUNNING");
        t.setStage(stage);
        t.setExternalId(externalId);
        currentTask = t;
    }

    private AsyncStageScope scope() {
        try {
            Constructor<AsyncStageScope> constructor =
                    AsyncStageScope.class.getDeclaredConstructor(AsyncTaskService.class, SaAsyncTask.class);
            constructor.setAccessible(true);
            return constructor.newInstance(asyncTaskService, currentTask);
        } catch (Exception e) {
            throw new IllegalStateException("构造 AsyncStageScope 失败", e);
        }
    }

    private Map<String, Object> snapshotOfCurrent() {
        Map<String, Object> map = new LinkedHashMap<>();
        String snapshotJson = currentTask.getExternalSnapshot();
        if (snapshotJson != null) {
            try {
                com.fasterxml.jackson.databind.JsonNode node =
                        new com.fasterxml.jackson.databind.ObjectMapper().readTree(snapshotJson);
                node.fields().forEachRemaining(entry -> map.put(entry.getKey(), plainNode(entry.getValue())));
            } catch (Exception ignored) {
            }
        }
        return map;
    }

    private static String text(Map<String, Object> state, String field) {
        Object value = state.get(field);
        return value == null ? null : String.valueOf(value);
    }

    private static Object plainNode(com.fasterxml.jackson.databind.JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return null;
        }
        if (value.isTextual()) {
            return value.asText();
        }
        if (value.isNumber()) {
            return value.numberValue();
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        return value;
    }

    private static void setLong(Object target, String field, long value) throws Exception {
        java.lang.reflect.Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.setLong(target, value);
    }
}
