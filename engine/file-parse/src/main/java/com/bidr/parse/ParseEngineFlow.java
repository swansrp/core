package com.bidr.parse;

import com.bidr.blob.BlobStore;
import com.bidr.blob.Digests;
import com.bidr.document.DocumentArtifact;
import com.bidr.document.DocumentConvertException;
import com.bidr.document.DocumentProcessor;
import com.bidr.document.RouteTrace;
import com.bidr.document.spi.AsyncTranscribeLeg;
import com.bidr.document.spi.ConvertStep;
import com.bidr.task.AsyncStage;
import com.bidr.task.AsyncStageScope;
import com.bidr.task.AsyncTaskType;
import com.bidr.task.StageOutcome;
import com.bidr.task.dao.entity.SaAsyncTask;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 解析编排引擎（M17，core 第一个 engine 族模块 {@code engine/file-parse} 的流程本体）：
 * 「文件进来 → office 转 PDF → 解析 → 产物落 blob」五阶段——
 * {@code INTAKE（受理/断点）→ CONVERT → TRANSCRIBE → PERSIST → ENRICH → done}。
 * 业务只剩「submit ＋ 终态观察 ＋ 读产物」三件事：submit（{@code AsyncTaskService.submit}），
 * 终态（{@code SUCCESS/DEAD} 必发，观察者自 ARTIFACTS 快照取键回写业务行），
 * 读产物（{@code BlobStore.get(markdownKey)}——快照里的键）。
 *
 * <p><b>输入</b>：payload＝{@code {"sourceKey":"raw/…","fileName":"x.docx","variant":3,
 * …业务透传键}}。biz_ref 与透传键引擎<b>不解释</b>（R-9：业务行坐标只透传）；{@code variant}
 * 是产物键代数段（文件轨＝业务版本号、材料轨＝0，D-1——引擎不当业务版本解释）。
 * 任务类型沿用 {@code "PARSE"}（D-8：与历史行同键，零迁移）。</p>
 *
 * <p><b>输出</b>：产物键形逐字不动（{@code BlobStore} 八前缀 golden 已钉，R-2）——
 * {@code parsed/{sha}/v{n}.md}、{@code pagemap/{sha}/v{n}.json}、{@code convert/{sha}.pdf}、
 * {@code image/{sha}/{name}}、{@code thumb/{sha}/p{n}.png}。交付通道＝任务行快照
 * {@code ARTIFACTS}（PERSIST 阶段落）：{@code {anchorSha, markdownKey, pageMapKey,
 * imageCount, pageImageCount, route, requested}}。快照列是跨阶段唯一载体（纪律：
 * 零内存/ThreadLocal 跨阶段载体），各阶段写快照都<b>先并后写</b>（保住前段字段）。</p>
 *
 * <p><b>断点（D-2，正向）</b>：目标产物键已存在即复用——直通件 md 命中即秒级 done；
 * 外部引擎件 md＋pagemap 双键命中才复用（md 落库与 pagemap 同 tick，拆开缺页映射＝
 * 静默降级不可接受）。增强腿注册时还要求快照带 {@code enriched} 标记——人工重排
 * （requeue 清快照）会重跑增强，正常复用才跳过。</p>
 *
 * <p><b>产物裁剪（D-11）</b>：payload 可带 engine-owned 闭集键 {@code artifacts}（JSON 数组，
 * 缺省＝全集 {@code [markdown,pageMap,pageImage]}）。子集-only 硬约束：未知值／缺
 * {@code markdown}/{@code pageMap} 本体一律 INTAKE 快败（{@link ParseContractException}
 * 不进重试）；本期唯一可减成员＝{@code pageImage}（PDFBox 逐页渲染是唯一有真实成本的
 * 派生产物）。裁剪先跑、全集后跑同 sha 时缺的产物补产，反之裁剪任务断点秒级命中——
 * 双向安全靠「键形不动＋只增不删」。</p>
 *
 * <p><b>三态转录</b>：直通件（md/txt/zip 连带包，D-5 引擎文件语义）→ 直通腿；
 * 异步腿在装且配置就绪 → 提交外部引擎、轮询走 {@code pending(退避)}（不占线程不占租约，
 * 等待预算 {@code engine.parse.mineru-max-wait-ms} 3600 档与同步门面 300 档分立）；
 * 都没有 → 内置直读（{@code DocumentProcessor}，{@code skipEnhancedTranscribe=true}——
 * 异步腿已先咨询过，同一条腿别在门面里同步再打一遍）。🔴 阶段不可裁（R-10）：
 * 空 config 不开关，空转是缺省态。</p>
 *
 * @author Sharp
 * @since 2026-10-21
 */
@Slf4j
@AsyncTaskType(value = ParseEngineFlow.TYPE, maxRetry = 3, stageEnum = ParseEngineStage.class)
public class ParseEngineFlow {

    /** 任务类型（D-8 沿用历史词，与业务行零迁移） */
    public static final String TYPE = "PARSE";

    /** 转录路线词表（快照 {@code route} 字段）：直通 */
    public static final String ROUTE_PASSTHROUGH = "passthrough";

    /** 转录路线：内置直读 */
    public static final String ROUTE_BUILTIN = "builtin";

    /** 转录路线：外部引擎（快照另带腿名） */
    public static final String ROUTE_EXTERNAL = "external";

    /** 外部引擎轮询退避（毫秒）：对齐跑批 5s tick 的等待节奏 */
    private static final long POLL_BACKOFF_MS = 5_000L;

    /** 「先转 PDF 再解析」的扩展名（office 词汇表；core convert 腿只答认领，不外泄清单） */
    private static final List<String> CONVERTIBLE_EXTS =
            Arrays.asList("doc", "docx", "xls", "xlsx", "ppt", "pptx");

    /** D-11 产物键闭集（裁剪词汇；{@code markdown}/{@code pageMap} 是本体恒产） */
    public static final String ARTIFACT_MARKDOWN = "markdown";

    public static final String ARTIFACT_PAGE_MAP = "pageMap";

    /** 唯一可减成员（PDFBox 逐页渲染＝唯一有真实成本的派生产物） */
    public static final String ARTIFACT_PAGE_IMAGE = "pageImage";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final BlobStore storage;

    private final ObjectProvider<DocumentProcessor> processor;

    private final ObjectProvider<ConvertStep> convertStep;

    private final List<AsyncTranscribeLeg> transcribeLegs;

    private final String transcriberSelection;

    private final long mineruMaxWaitMs;

    private final ParseArtifactWriter writer;

    private final TextPassthroughService passthrough;

    private final PageImageRenderer pageImageRenderer;

    private final List<ParseEnrichLeg> enrichLegs;

    public ParseEngineFlow(BlobStore storage, ObjectProvider<DocumentProcessor> processor,
            ObjectProvider<ConvertStep> convertStep, List<AsyncTranscribeLeg> transcribeLegs,
            String transcriberSelection, long mineruMaxWaitMs, ParseArtifactWriter writer,
            TextPassthroughService passthrough, PageImageRenderer pageImageRenderer,
            List<ParseEnrichLeg> enrichLegs) {
        this.storage = storage;
        this.processor = processor;
        this.convertStep = convertStep;
        this.transcribeLegs = transcribeLegs == null ? Collections.<AsyncTranscribeLeg>emptyList() : transcribeLegs;
        this.transcriberSelection = transcriberSelection;
        this.mineruMaxWaitMs = mineruMaxWaitMs;
        this.writer = writer;
        this.passthrough = passthrough;
        this.pageImageRenderer = pageImageRenderer;
        this.enrichLegs = enrichLegs == null ? Collections.<ParseEnrichLeg>emptyList() : enrichLegs;
    }

    // ==================== 阶段 1：受理（order=1） ====================

    /**
     * 解析 payload（sourceKey/fileName/variant/artifacts）、算源 sha、验 D-11 裁剪键、
     * 断点快路径（非 office 件 md＋pagemap 已在＝秒级 done）。office 件的断点在
     * TRANSCRIBE（锚 sha 要等 CONVERT 归一后才知道）。排队闸门不在阶段体——受理侧
     * {@code base.task.max-queue-ahead}（core submit 闸门）已管。
     */
    @AsyncStage(value = "INTAKE", order = 1)
    public StageOutcome intake(SaAsyncTask task, AsyncStageScope scope) {
        ParseRequest request = ParseRequest.of(task);
        Set<String> requested = requestedArtifacts(request.artifactsJson, task.getBizRef());
        byte[] source = requireBytes(request.sourceKey, task.getBizRef());
        String sourceSha = Digests.sha256Hex(source);
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("sourceSha", sourceSha);
        patch.put("fileName", request.fileName);
        patch.put("variant", request.variant);
        patch.put("requested", requested);
        if (!isConvertibleExt(request.fileName)) {
            // 非 office 件锚即原件：断点在这里就能判（D-2 正向：目标键已在＝秒级复用）
            if (productsComplete(sourceSha, request.variant, requested,
                    PageImageRenderer.looksLikePdf(source)) && enrichSatisfied(task)) {
                patch.put("anchorSha", sourceSha);
                patch.put("anchorKey", request.sourceKey);
                mergeSnapshot(task, scope, "REUSED", patch);
                log.info("解析断点命中（秒级复用）: task={} bizRef={} sha={} v={}",
                        task.getId(), task.getBizRef(), sourceSha, request.variant);
                return StageOutcome.done();
            }
            patch.put("anchorSha", sourceSha);
            patch.put("anchorKey", request.sourceKey);
            mergeSnapshot(task, scope, "INTAKE", patch);
            return StageOutcome.to(ParseEngineStage.TRANSCRIBE);
        }
        mergeSnapshot(task, scope, "INTAKE", patch);
        return StageOutcome.to(ParseEngineStage.CONVERT);
    }

    // ==================== 阶段 2：office 转 PDF（order=2） ====================

    /**
     * office 类先转 PDF 再解析（外部引擎对 docx/xlsx 不分页，先归一成 PDF，页码/页图/引用定位
     * 整条链才成立）。失败两半不许合并：转不动（retryable=false）＝稳定状态，记
     * CONVERT_FALLBACK 后按原件解析、引用降级无页；不可达/5xx（retryable=true）＝原样上抛走
     * 重试预算——静默兜底会把"暂时没页"固化成"永远没页"。convert 腿未装配同属稳定状态。
     * 非 office 文件本阶段直通（INTAKE 已分流）。转换产物按源 sha 寻址——已有即复用不重转。
     */
    @AsyncStage(value = "CONVERT", order = 2)
    public StageOutcome convert(SaAsyncTask task, AsyncStageScope scope) {
        ParseRequest request = ParseRequest.of(task);
        Map<String, Object> state = snapshotOf(task);
        String sourceSha = text(state, "sourceSha");
        if (!StringUtils.hasText(sourceSha)) {
            throw new ParseContractException("任务快照缺 sourceSha（INTAKE 未跑或快照被清）: " + task.getBizRef());
        }
        String convertedKey = BlobStore.convertedPdfKey(sourceSha);
        byte[] cached = peek(convertedKey);
        if (cached != null && cached.length > 0) {
            return converted(task, scope, request, sourceSha, convertedKey, Digests.sha256Hex(cached));
        }
        ConvertStep leg = convertStep.getIfAvailable();
        if (leg == null || !leg.isConvertible(request.fileName)) {
            if (leg == null && isConvertibleExt(request.fileName)) {
                log.warn("未装配 office 转换腿，按原件解析（引用降级无页）: task={} file={}",
                        task.getId(), request.fileName);
                mergeSnapshot(task, scope, "CONVERT_FALLBACK", convertSnapshot("office 转换引擎未装配"));
            }
            Map<String, Object> patch = new LinkedHashMap<>();
            patch.put("anchorSha", sourceSha);
            patch.put("anchorKey", request.sourceKey);
            mergeSnapshot(task, scope, "INTAKE", patch);
            return StageOutcome.to(ParseEngineStage.TRANSCRIBE);
        }
        byte[] pdf;
        try {
            pdf = leg.convertToPdf(request.fileName, requireBytes(request.sourceKey, task.getBizRef()));
        } catch (DocumentConvertException e) {
            if (e.isRetryable()) {
                throw e;
            }
            log.warn("office 转 PDF 失败，按原件解析（引用降级无页）: task={} file={} {}",
                    task.getId(), request.fileName, e.getMessage());
            mergeSnapshot(task, scope, "CONVERT_FALLBACK", convertSnapshot(e.getMessage()));
            Map<String, Object> patch = new LinkedHashMap<>();
            patch.put("anchorSha", sourceSha);
            patch.put("anchorKey", request.sourceKey);
            mergeSnapshot(task, scope, "INTAKE", patch);
            return StageOutcome.to(ParseEngineStage.TRANSCRIBE);
        }
        String parseSha = Digests.sha256Hex(pdf);
        storage.put(convertedKey, pdf, BlobStore.contentTypeOf("a.pdf"));
        return converted(task, scope, request, sourceSha, convertedKey, parseSha);
    }

    /** 转换收口（复用与新转共用）：锚/键落快照，进转录 */
    private StageOutcome converted(SaAsyncTask task, AsyncStageScope scope, ParseRequest request,
            String sourceSha, String convertedKey, String parseSha) {
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("anchorSha", parseSha);
        patch.put("anchorKey", convertedKey);
        mergeSnapshot(task, scope, "CONVERTED", patch);
        log.info("office 已转 PDF: task={} file={} anchorSha={} 键={}",
                task.getId(), request.fileName, parseSha, convertedKey);
        return StageOutcome.to(ParseEngineStage.TRANSCRIBE);
    }

    // ==================== 阶段 3：转录（order=3） ====================

    /**
     * 取字三态：直通件 → 直通腿（D-5 引擎文件语义）；异步腿在装且配置就绪 → 提交外部引擎、
     * 轮询走 pending 退避；都没有 → 内置直读。产物（md/pagemap/内联图）在本阶段落 blob——
     * 跨阶段零内存载体，产物键进快照就是 PERSIST 的输入。
     */
    @AsyncStage(value = "TRANSCRIBE", order = 3)
    public StageOutcome transcribe(SaAsyncTask task, AsyncStageScope scope) {
        ParseRequest request = ParseRequest.of(task);
        Map<String, Object> state = snapshotOf(task);
        String anchorSha = text(state, "anchorSha");
        String anchorKey = text(state, "anchorKey");
        if (!StringUtils.hasText(anchorSha) || !StringUtils.hasText(anchorKey)) {
            throw new ParseContractException("任务快照缺锚（anchorSha/anchorKey）: " + task.getBizRef());
        }
        Set<String> requested = requestedArtifacts(request.artifactsJson, task.getBizRef());
        String ext = extOf(request.fileName);
        // 断点（D-2）：锚已归一，键齐即复用（office 件在这里判；直通/内置件 md 即判，
        // 页图目标只在锚可渲染时成立——见 productsComplete）
        if (productsComplete(anchorSha, request.variant, requested,
                PageImageRenderer.looksLikePdf(peek(anchorKey))) && enrichSatisfied(task)) {
            mergeSnapshot(task, scope, "REUSED", Collections.<String, Object>singletonMap("route", routeOf(state)));
            log.info("解析断点命中（转录秒级复用）: task={} bizRef={} anchor={} v={}",
                    task.getId(), task.getBizRef(), anchorSha, request.variant);
            return StageOutcome.to(ParseEngineStage.PERSIST);
        }
        if (TextPassthroughService.isPassthroughExt(ext)) {
            // 直通判定要在"要引擎"之前：引擎按内容嗅探不收纯文本（实测提交那一刻就 HTTP 400），
            // 把没配解析引擎这种无关状态套到它们头上只会白烧重试预算
            TextPassthroughService.Result result = passthrough.passthrough(
                    anchorSha, request.variant, ext, readBytes(anchorKey));
            Map<String, Object> patch = new LinkedHashMap<>();
            patch.put("route", ROUTE_PASSTHROUGH);
            patch.put("markdownKey", result.getMarkdownKey());
            patch.put("pageMapKey", null);
            patch.put("imageCount", result.getStoredImages());
            patch.put("passthrough", result.getSource());
            patch.put("entry", result.getEntryName());
            patch.put("keptPlaceholders", result.getKeptPlaceholders());
            patch.put("unresolvedRefs", result.getUnresolvedRefs());
            mergeSnapshot(task, scope, "PASSED_THROUGH", patch);
            log.info("直通产物就绪: task={} 来源={} 字符={} 内联图={} 未解析引用={}",
                    task.getId(), result.getSource(), result.getMarkdown().length(),
                    result.getStoredImages(), result.getUnresolvedRefs());
            return StageOutcome.to(ParseEngineStage.PERSIST);
        }
        AsyncTranscribeLeg leg = selectTranscribeLeg();
        if (leg == null || !leg.hasLeg()) {
            return builtin(task, scope, request, state, anchorSha, anchorKey);
        }
        return external(task, scope, request, state, anchorSha, anchorKey, leg);
    }

    /** 内置直读腿（三态之一）：异步腿缺席＝内置不抛（"未配置解析引擎"的抛错腿不进引擎） */
    private StageOutcome builtin(SaAsyncTask task, AsyncStageScope scope, ParseRequest request,
            Map<String, Object> state, String anchorSha, String anchorKey) {
        DocumentProcessor documentProcessor = processor.getIfAvailable();
        if (documentProcessor == null) {
            throw new IllegalStateException("解析能力未装配（无异步转录腿且无内置加工门面）: " + task.getBizRef());
        }
        DocumentArtifact artifact;
        try {
            documentProcessor.getClass();
            artifact = documentProcessor.process(readBytes(anchorKey),
                    anchorFileName(request, state), null, true);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("内置解析失败: " + anchorFileName(request, state)
                    + " " + e.getMessage(), e);
        }
        ParseArtifactWriter.Written written = writer.builtin(anchorSha, request.variant, artifact.getMarkdown());
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("route", ROUTE_BUILTIN);
        patch.put("markdownKey", written.getMarkdownKey());
        patch.put("pageMapKey", null);
        patch.put("imageCount", 0);
        patch.put("routeTrace", artifact.getTrace().describe());
        patch.put("degradedNoPage", artifact.getTrace().isDegradedNoPage());
        mergeSnapshot(task, scope, "BUILTIN", patch);
        log.info("内置直读产物就绪: task={} 走法={} 字符={}", task.getId(),
                artifact.getTrace().describe(), artifact.getMarkdown().length());
        return StageOutcome.to(ParseEngineStage.PERSIST);
    }

    /**
     * 外部引擎异步腿：submit → poll → fetch。提交即返回（快照记腿/路线/提交时刻），
     * 轮询走 pending 退避；等待超预算／引擎报失败＝清外部单再抛（A8：不清就还在轮询
     * 那张已失效的单，「重提交新单」永远不会发生）。
     */
    private StageOutcome external(SaAsyncTask task, AsyncStageScope scope, ParseRequest request,
            Map<String, Object> state, String anchorSha, String anchorKey, AsyncTranscribeLeg leg) {
        String externalId = task.getExternalId();
        if (!StringUtils.hasText(externalId)) {
            AsyncTranscribeLeg.Submission submission = leg.submit(readBytes(anchorKey),
                    anchorFileName(request, state), true);
            scope.externalId(submission.getExternalId());
            Map<String, Object> patch = new LinkedHashMap<>();
            patch.put("route", ROUTE_EXTERNAL);
            patch.put("leg", leg.legName());
            patch.put("subRoute", submission.getRoute());
            patch.put("since", submission.getSince());
            mergeSnapshot(task, scope, "SUBMITTED", patch);
            log.info("已提交解析: task={} bizRef={} leg={} route={} externalId={}",
                    task.getId(), task.getBizRef(), leg.legName(), submission.getRoute(),
                    submission.getExternalId());
            return StageOutcome.to(ParseEngineStage.TRANSCRIBE);
        }
        String subRoute = StringUtils.hasText(text(state, "subRoute")) ? text(state, "subRoute")
                : text(state, "leg");
        assertWithinWaitBudget(task, scope, leg.legName(), subRoute, state);
        AsyncTranscribeLeg.Progress progress = leg.poll(subRoute, externalId);
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("status", progress.getStatus());
        if (progress.getPhase() != null) {
            patch.put("phase", progress.getPhase());
        }
        if (progress.getQueueAhead() != null) {
            patch.put("queueAhead", progress.getQueueAhead());
        }
        mergeSnapshot(task, scope, progress.getStatus(), patch);
        if (AsyncTranscribeLeg.STATUS_FAILED.equals(progress.getStatus())) {
            // 🔴 A8：重提交口先清外部单再抛——自动重试的下一轮若还留着 external_id，会去轮询
            // 那张已失效的单，「重提交新单」永远不发生
            scope.releaseExternal();
            throw new IllegalStateException("解析失败: " + progress.getErrorMessage());
        }
        if (!AsyncTranscribeLeg.STATUS_COMPLETED.equals(progress.getStatus())) {
            return StageOutcome.pending(POLL_BACKOFF_MS);
        }
        AsyncTranscribeLeg.Artifact artifact = leg.fetch(subRoute, externalId);
        ParseArtifactWriter.Written written = writer.transcribe(anchorSha, request.variant, artifact);
        Map<String, Object> done = new LinkedHashMap<>();
        done.put("route", ROUTE_EXTERNAL);
        done.put("leg", leg.legName());
        done.put("markdownKey", written.getMarkdownKey());
        done.put("pageMapKey", written.getPageMapKey());
        done.put("pageCount", written.getPageCount());
        done.put("blockCount", written.getBlockCount());
        done.put("imageCount", written.getImageCount());
        mergeSnapshot(task, scope, "TRANSCRIBED", done);
        log.info("解析与产物就绪: task={} bizRef={} 页={} 块={} 图={} 正文={}",
                task.getId(), task.getBizRef(), written.getPageCount(), written.getBlockCount(),
                written.getImageCount(), written.getMarkdownKey());
        return StageOutcome.to(ParseEngineStage.PERSIST);
    }

    // ==================== 阶段 4：产物收口（order=4） ====================

    /**
     * 页图渲染（D-11 可裁成员 {@code pageImage}；锚是 PDF 才有得渲染，非 PDF 腿内自跳过）
     * ＋ ARTIFACTS 交付快照（观察者回写业务行的取键口）。
     */
    @AsyncStage(value = "PERSIST", order = 4)
    public StageOutcome persist(SaAsyncTask task, AsyncStageScope scope) {
        ParseRequest request = ParseRequest.of(task);
        Map<String, Object> state = snapshotOf(task);
        Set<String> requested = requestedArtifacts(request.artifactsJson, task.getBizRef());
        Map<String, Object> patch = new LinkedHashMap<>();
        int pageImages = 0;
        if (requested.contains(ARTIFACT_PAGE_IMAGE) && StringUtils.hasText(text(state, "anchorKey"))) {
            pageImages = pageImageRenderer.render(text(state, "anchorSha"), readBytes(text(state, "anchorKey")));
        }
        patch.put("pageImageCount", pageImages);
        mergeSnapshot(task, scope, "ARTIFACTS", patch);
        log.info("产物收口: task={} bizRef={} route={} md={} pagemap={} 图={} 页图={}",
                task.getId(), task.getBizRef(), text(state, "route"), text(state, "markdownKey"),
                text(state, "pageMapKey"), state.get("imageCount"), pageImages);
        return StageOutcome.to(ParseEngineStage.ENRICH);
    }

    // ==================== 阶段 5：增强（order=5） ====================

    /**
     * 注册腿增强（业务注册 {@link ParseEnrichLeg}；不注册＝空转直通）。
     * 单腿失败如实抛（默认可重试；永久失败让异常实现 {@code RetryAware} 返回 false）。
     */
    @AsyncStage(value = "ENRICH", order = 5)
    public StageOutcome enrich(SaAsyncTask task, AsyncStageScope scope) {
        if (enrichLegs.isEmpty()) {
            mergeSnapshot(task, scope, "DONE", Collections.<String, Object>singletonMap("enrichLegs", ""));
            return StageOutcome.done();
        }
        ParseRequest request = ParseRequest.of(task);
        Map<String, Object> state = snapshotOf(task);
        ParseEnrichContext context = new ParseEnrichContext(text(state, "sourceSha"),
                text(state, "anchorSha"), request.variant, text(state, "markdownKey"),
                text(state, "pageMapKey"), text(state, "route"),
                intOf(state, "imageCount"), intOf(state, "pageImageCount"));
        Set<String> requested = requestedArtifacts(request.artifactsJson, task.getBizRef());
        StringBuilder names = new StringBuilder();
        for (ParseEnrichLeg leg : enrichLegs) {
            leg.enrich(context);
            if (names.length() > 0) {
                names.append(',');
            }
            names.append(leg.legName());
        }
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("enrichLegs", names.toString());
        mergeSnapshot(task, scope, "ENRICHED", patch);
        log.info("增强腿完成: task={} bizRef={} 腿={}", task.getId(), task.getBizRef(), names);
        return StageOutcome.done();
    }

    // ==================== 断点判据（D-2/D-11） ====================

    /**
     * 目标产物是否齐备（键形不动＋只增不删＝双向安全）：md 是断点主键；页图仅当
     * 「请求了 pageImage 且锚可渲染（PDF 魔数）」才算目标——直通/内置件永远无 thumb，
     * 拿它当断点条件会让文本件永远命中不了复用（每次重排都白跑一遍转录）。
     * pagemap 不进断点判据：直通/内置件本就没有映射（判空降级的正常形态），
     * 其落库顺序先于 md（写完映射才写正文），md 在场即映射已在。
     */
    private boolean productsComplete(String anchorSha, int variant, Set<String> requested, boolean pdfAnchor) {
        if (peek(BlobStore.parsedMarkdownKey(anchorSha, variant)) == null) {
            return false;
        }
        if (pdfAnchor && requested.contains(ARTIFACT_PAGE_IMAGE) && !thumbsExist(anchorSha)) {
            return false;
        }
        return true;
    }

    /** thumb 目录有没有产出（页图第一页在＝渲染过；仅「请求了 pageImage 且锚可渲染」时查） */
    private boolean thumbsExist(String anchorSha) {
        return peek(BlobStore.thumbKey(anchorSha, 1)) != null;
    }

    /** 增强是否已随上一轮完成：注册了增强腿而快照缺 {@code enriched} 标记＝要重跑增强 */
    private boolean enrichSatisfied(SaAsyncTask task) {
        if (enrichLegs.isEmpty()) {
            return true;
        }
        Map<String, Object> state = snapshotOf(task);
        return StringUtils.hasText(text(state, "enrichLegs"));
    }

    // ==================== D-11 产物键闭集 ====================

    /**
     * 解析 D-11 裁剪键：payload {@code artifacts} 缺省＝全集；给了＝闭集减法——
     * 未知值或缺本体（markdown/pageMap）一律快败（{@link ParseContractException}，
     * 子集错位＝下游读空，最坏失败模式必须提交时炸）。
     */
    static Set<String> requestedArtifacts(String artifactsJson, String bizRef) {
        Set<String> full = new LinkedHashSet<>(Arrays.asList(ARTIFACT_MARKDOWN, ARTIFACT_PAGE_MAP,
                ARTIFACT_PAGE_IMAGE));
        if (!StringUtils.hasText(artifactsJson)) {
            return full;
        }
        Set<String> requested = new LinkedHashSet<>();
        try {
            JsonNode node = MAPPER.readTree(artifactsJson);
            if (!node.isArray() || node.size() == 0) {
                throw new ParseContractException("artifacts 裁剪键须为非空 JSON 数组: bizRef=" + bizRef
                        + " artifacts=" + artifactsJson);
            }
            for (JsonNode item : node) {
                requested.add(item.asText());
            }
        } catch (ParseContractException e) {
            throw e;
        } catch (Exception e) {
            throw new ParseContractException("artifacts 裁剪键解析失败: bizRef=" + bizRef
                    + " artifacts=" + artifactsJson);
        }
        for (String name : requested) {
            if (!full.contains(name)) {
                throw new ParseContractException("artifacts 未知产物键（闭集＝" + full + "）: bizRef="
                        + bizRef + " artifacts=" + artifactsJson);
            }
        }
        if (!requested.contains(ARTIFACT_MARKDOWN) || !requested.contains(ARTIFACT_PAGE_MAP)) {
            throw new ParseContractException("artifacts 裁剪缺产物本体（markdown/pageMap 恒产）: bizRef="
                    + bizRef + " artifacts=" + artifactsJson);
        }
        return requested;
    }

    // ==================== 转录腿选型（三态语义，沿 TranscribeLegSelector） ====================

    /**
     * 异步腿选型：键空＝取第一条在装腿（无＝null 走内置）；配 {@code builtin}/{@code none}
     * ＝强制内置；配了但腿不在 classpath＝WARN「选型指向不存在的腿」后走内置。
     */
    private AsyncTranscribeLeg selectTranscribeLeg() {
        if ("builtin".equalsIgnoreCase(transcriberSelection) || "none".equalsIgnoreCase(transcriberSelection)) {
            return null;
        }
        if (!StringUtils.hasText(transcriberSelection)) {
            return transcribeLegs.isEmpty() ? null : transcribeLegs.get(0);
        }
        for (AsyncTranscribeLeg leg : transcribeLegs) {
            if (transcriberSelection.equals(leg.legName())) {
                return leg;
            }
        }
        log.warn("engine.parse.transcriber 选型指向不存在的腿，已走内置: {}（在装腿: {}）",
                transcriberSelection, legNames());
        return null;
    }

    private String legNames() {
        StringBuilder names = new StringBuilder();
        for (AsyncTranscribeLeg leg : transcribeLegs) {
            if (names.length() > 0) {
                names.append(',');
            }
            names.append(leg.legName());
        }
        return names.toString();
    }

    // ==================== 等待预算（判据⑧：3600 档） ====================

    /**
     * 超预算＝可重试失败，重提交新单。🔴 A8：抛之前先 {@code releaseExternal()}——
     * external_id／快照不清，重试的下一轮还在轮询那张已等超时的单
     * （同一个 externalId 再等 3600s＝预算形同虚设）。
     */
    private void assertWithinWaitBudget(SaAsyncTask task, AsyncStageScope scope, String legName,
            String subRoute, Map<String, Object> state) {
        Long since = longOf(state, "since");
        if (since == null) {
            return;
        }
        long elapsed = System.currentTimeMillis() - since;
        if (elapsed > mineruMaxWaitMs) {
            scope.releaseExternal();
            throw new IllegalStateException("解析引擎等待超预算（" + (mineruMaxWaitMs / 1000) + "s 档）: leg="
                    + legName + " route=" + subRoute + " externalId=" + task.getExternalId()
                    + " 已等 " + (elapsed / 1000) + "s");
        }
    }

    // ==================== payload 与快照读写 ====================

    /** 快照并写（保住前段字段）：快照列是跨阶段唯一载体，覆盖写会丢前段坐标 */
    private void mergeSnapshot(SaAsyncTask task, AsyncStageScope scope, String status,
            Map<String, Object> patch) {
        Map<String, Object> merged = snapshotOf(task);
        merged.putAll(patch);
        scope.snapshot(status, json(merged));
    }

    private static Map<String, Object> snapshotOf(SaAsyncTask task) {
        Map<String, Object> map = new LinkedHashMap<>();
        String snapshotJson = task == null ? null : task.getExternalSnapshot();
        if (StringUtils.hasText(snapshotJson)) {
            try {
                JsonNode node = MAPPER.readTree(snapshotJson);
                node.fields().forEachRemaining(entry -> map.put(entry.getKey(), plain(entry.getValue())));
            } catch (Exception e) {
                log.debug("既有快照解析失败（按空快照并写）: {}", e.getMessage());
            }
        }
        return map;
    }

    /** 快照值解包成原生 Java 型（JSON 回读的是 JsonNode，String.valueOf 会带引号污染键拼装） */
    private static Object plain(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return null;
        }
        if (value.isTextual()) {
            return value.asText();
        }
        if (value.isInt() || value.isLong()) {
            return value.asLong();
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isNumber()) {
            return value.numberValue();
        }
        return value;
    }

    private static String text(Map<String, Object> state, String field) {
        Object value = state.get(field);
        return value == null || value instanceof com.fasterxml.jackson.databind.node.NullNode
                ? null : String.valueOf(value);
    }

    private static int intOf(Map<String, Object> state, String field) {
        Object value = state.get(field);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static Long longOf(Map<String, Object> state, String field) {
        Object value = state.get(field);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(String.valueOf(value));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static String json(Map<String, Object> map) {
        try {
            return MAPPER.writeValueAsString(map);
        } catch (Exception e) {
            log.debug("快照序列化失败（忽略）: {}", e.getMessage());
            return null;
        }
    }

    /** 转录路线（快照缺席＝未转录过，置空串占位） */
    private static String routeOf(Map<String, Object> state) {
        String route = text(state, "route");
        return StringUtils.hasText(route) ? route : "";
    }

    private byte[] requireBytes(String key, String bizRef) {
        byte[] bytes = readBytes(key);
        if (bytes == null || bytes.length == 0) {
            throw new ParseContractException("源文件不存在或为空: bizRef=" + bizRef + " key=" + key);
        }
        return bytes;
    }

    /**
     * 对象存储读口的受检异常卫生（M17 真机实测：MinIO SDK 的受检 ErrorResponseException
     * 穿 CGLIB 代理成 {@code UndeclaredThrowableException}，message=null 不可归因）——
     * 统一收编为可读运行时异常（缺省可重试：临时故障走退避，NoSuchKey 重试耗尽自然 DEAD）。
     */
    private byte[] readBytes(String key) {
        try {
            return storage.get(key);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("读取对象失败: key=" + key + " " + e.getMessage(), e);
        }
    }

    /**
     * 存在性探测（断点判据专用）：对象存储对缺失 key 是<b>抛异常</b>不是回 null
     * （M17 真机实测：首轮解析的断点检查读产物键必吃 NoSuchKey）——任何读取失败按
     * 「缺席」处理。误判缺席的代价＝多跑一遍幂等产线（内容寻址、键形不动，安全侧）。
     */
    private byte[] peek(String key) {
        try {
            return storage.get(key);
        } catch (Exception e) {
            return null;
        }
    }

    /** 喂给解析引擎的文件名：锚是转换 PDF 时必须带 .pdf 扩展名（引擎按扩展名分派解析器） */
    private static String anchorFileName(ParseRequest request, Map<String, Object> state) {
        if (BlobStore.convertedPdfKey(text(state, "sourceSha")).equals(text(state, "anchorKey"))) {
            return stripExt(request.fileName) + ".pdf";
        }
        return request.fileName;
    }

    private static String extOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String stripExt(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        return name == null || dot <= 0 ? name : name.substring(0, dot);
    }

    private static boolean isConvertibleExt(String fileName) {
        String ext = extOf(fileName);
        return CONVERTIBLE_EXTS.contains(ext);
    }

    /** 转换观测快照体（fallback 档） */
    private static Map<String, Object> convertSnapshot(String detail) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("convert", "fallback");
        map.put("detail", detail);
        return map;
    }

    /** 阶段体入参（payload 解析；透传键引擎不解释） */
    private static final class ParseRequest {

        private final String sourceKey;

        private final String fileName;

        private final int variant;

        private final String artifactsJson;

        private ParseRequest(String sourceKey, String fileName, int variant, String artifactsJson) {
            this.sourceKey = sourceKey;
            this.fileName = fileName;
            this.variant = variant;
            this.artifactsJson = artifactsJson;
        }

        static ParseRequest of(SaAsyncTask task) {
            String payload = task == null ? null : task.getPayload();
            if (!StringUtils.hasText(payload)) {
                throw new ParseContractException("任务 payload 为空，缺 sourceKey/fileName/variant: bizRef="
                        + (task == null ? "-" : task.getBizRef()));
            }
            try {
                JsonNode node = MAPPER.readTree(payload);
                String sourceKey = node.path("sourceKey").asText(null);
                String fileName = node.path("fileName").asText(null);
                if (!StringUtils.hasText(sourceKey) || !StringUtils.hasText(fileName)) {
                    throw new ParseContractException("任务 payload 缺 sourceKey/fileName: bizRef="
                            + task.getBizRef() + " payload=" + payload);
                }
                JsonNode variantNode = node.path("variant");
                int variant = variantNode.isMissingNode() || variantNode.isNull()
                        ? 0 : variantNode.asInt(0);
                JsonNode artifacts = node.path("artifacts");
                return new ParseRequest(sourceKey, fileName, variant,
                        artifacts.isMissingNode() || artifacts.isNull() ? null : artifacts.toString());
            } catch (ParseContractException e) {
                throw e;
            } catch (Exception e) {
                throw new ParseContractException("任务 payload 解析失败: bizRef="
                        + task.getBizRef() + " " + e.getMessage());
            }
        }
    }
}
