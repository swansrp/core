package com.bidr.document;

import com.bidr.document.builtin.BuiltInDispatcher;
import com.bidr.document.builtin.CappedIntakeStep;
import com.bidr.document.builtin.DefaultArtifactStep;
import com.bidr.document.builtin.VisionTranscriber;
import com.bidr.document.spi.ArtifactStep;
import com.bidr.document.spi.ConvertStep;
import com.bidr.document.spi.EnrichStep;
import com.bidr.document.spi.IntakeStep;
import com.bidr.document.spi.TranscribeStep;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

/**
 * Title: DocumentProcessor
 * Description: 文件→Markdown 加工<b>门面</b>（每步一槽、腿可选、内置件兜底）：
 * intake（受理读全＋大小闸）→ convert（外挂 office→PDF，<b>无内置实现</b>，转不动记
 * CONVERT_FALLBACK 原件直送）→ transcribe（选型后的增强腿，失败/空结果回落内置分派）→
 * enrich（可选后处理）→ artifact（正文＋路线留痕落形）。
 * <p>
 * 边界三条（§3.3，不许少）：
 * <ol>
 *   <li>降级腿不许伪装正常——出参 {@link DocumentArtifact} 带 {@link RouteTrace}：
 *       哪条 convert 腿×哪条 transcribe 腿／内置直读＋是否降级无页；</li>
 *   <li>{@link DocumentConvertException} 的 retryable 两半不许合并——转不动＝稳定可兜底，
 *       不可达/5xx＝<b>原样上抛</b>交调用方重试预算（不许静默兜底：会把「暂时没页」固化成
 *       「永远没页」）；</li>
 *   <li>core 侧「没配增强腿」绝不进抛错腿——三态：没配→内置不抛；配了包没引→WARN 走内置；
 *       配了且在→增强腿失败回落内置（门面回落、实现方别吞错）。</li>
 * </ol>
 * 硬参数全部 {@code tool.document.*} 可配、缺省值与历史 {@code FileMarkdownService} 相同。
 *
 * @author Sharp
 * @since 2026/10/20
 */
@Slf4j
public class DocumentProcessor {

    @Getter
    private final DocumentProperties properties;

    private final FileRouter router;

    private final List<ConvertStep> convertSteps;

    @Getter
    private final TranscribeStep transcribeStep;

    private final List<EnrichStep> enrichSteps;

    private final ArtifactStep artifactStep;

    private final IntakeStep intakeStep;

    private final BuiltInDispatcher builtIn;

    /**
     * 全参构造（null 列表/槽件归内置缺省档）。
     *
     * @param properties          硬参数（null＝{@link DocumentProperties#defaults()}）
     * @param convertSteps        convert 外挂腿（装配序；convert 槽无内置实现）
     * @param transcribeStep      选型后的增强转写腿（null＝走内置）
     * @param visionConfigProvider 视觉配置口（null＝多模态只能调用时显式传入）
     * @param enrichSteps         后处理增强（空＝直通）
     * @param artifactStep        产物落形（null＝默认直包装）
     * @param intakeStep          受理（null＝大小闸读全）
     */
    public DocumentProcessor(DocumentProperties properties, List<ConvertStep> convertSteps,
            TranscribeStep transcribeStep, VisionConfigProvider visionConfigProvider,
            List<EnrichStep> enrichSteps, ArtifactStep artifactStep, IntakeStep intakeStep) {
        this.properties = properties == null ? DocumentProperties.defaults() : properties;
        this.convertSteps = convertSteps == null ? Collections.<ConvertStep>emptyList() : convertSteps;
        this.transcribeStep = transcribeStep;
        this.enrichSteps = enrichSteps == null ? Collections.<EnrichStep>emptyList() : enrichSteps;
        this.artifactStep = artifactStep == null ? new DefaultArtifactStep() : artifactStep;
        this.intakeStep = intakeStep == null ? new CappedIntakeStep(this.properties) : intakeStep;
        VisionTranscriber visionTranscriber = new VisionTranscriber(visionConfigProvider);
        this.builtIn = new BuiltInDispatcher(this.properties, visionTranscriber);
        this.router = new FileRouter(this.convertSteps, transcribeStep, this.builtIn);
    }

    /**
     * 纯内置档（无 convert 腿、无增强转写腿）——历史 FileMarkdownService 无外部腿时的等价形态。
     */
    public static DocumentProcessor defaults(VisionConfigProvider visionConfigProvider) {
        return new DocumentProcessor(null, null, null, visionConfigProvider, null, null, null);
    }

    /**
     * 有没有腿认领该文件（路由分派副产物，受理收口归业务侧）。
     */
    public boolean isProcessable(String filename) {
        return router.isProcessable(filename);
    }

    /**
     * 受理读全（大小闸；{@code tool.document.max-file-size-bytes} 可配，缺省 100MB）。
     */
    public byte[] readCapped(InputStream in) throws IOException {
        return intakeStep.read(in);
    }

    /**
     * 加工：原件字节 → 产物（正文＋路线留痕）。
     *
     * @param content  原件字节
     * @param filename 原始文件名
     * @param vision   显式传入的多模态端点（可空＝走 {@link VisionConfigProvider}）
     */
    public DocumentArtifact process(byte[] content, String filename, VisionEndpoint vision) throws IOException {
        return process(content, filename, vision, false);
    }

    /**
     * @param skipEnhancedTranscribe true＝跳过增强转写腿直走内置分派（老口腿在外层已咨询失败后的
     *                               回落路径——同一条腿别打两遍；convert 槽不受影响）
     */
    public DocumentArtifact process(byte[] content, String filename, VisionEndpoint vision,
            boolean skipEnhancedTranscribe) throws IOException {
        RouteTrace.RouteTraceBuilder trace = RouteTrace.builder();
        byte[] working = content;
        String workingName = filename == null ? "" : filename;

        // ---- convert 槽（外挂，无内置）：认领 office 的腿先转 PDF；转不动(retryable=false)
        //      记 CONVERT_FALLBACK 原件直送；不可达/5xx(retryable=true) 原样上抛进重试预算 ----
        ConvertStep convertLeg = router.convertLegFor(workingName);
        boolean officeInput = convertLeg != null || builtIn.isOfficeExt(workingName);
        boolean converted = false;
        if (convertLeg != null) {
            try {
                working = convertLeg.convertToPdf(workingName, working);
                workingName = pdfNameOf(workingName);
                converted = true;
                trace.convertLeg(convertLeg.legName());
            } catch (DocumentConvertException e) {
                if (e.isRetryable()) {
                    throw e;
                }
                trace.convertFallback(true).convertFallbackReason(e.getMessage());
            }
        }
        if (officeInput && !converted && convertLeg == null) {
            // 现网快照形状（ParseTaskHandler 未装配档）：office 输入＋convert 腿未装配＝原件直送记 CONVERT_FALLBACK
            trace.convertFallback(true).convertFallbackReason("convert 腿未装配");
        }

        // ---- transcribe 槽（选型后的增强腿；失败/空结果 WARN 后回落内置分派） ----
        if (!skipEnhancedTranscribe && transcribeStep != null && transcribeStep.isAvailable(workingName)) {
            try {
                String markdown = transcribeStep.transcribe(working, workingName);
                if (hasText(markdown)) {
                    trace.transcribeLeg(transcribeStep.legName());
                    return finish(markdown, workingName, trace, officeInput && !converted);
                }
                log.warn("外部转录返回空结果，回落本地解析: {}", workingName);
                trace.transcribeFallback(true).transcribeFallbackReason("返回空结果");
            } catch (Exception e) {
                log.warn("外部转录失败，回落本地解析: {} 原因: {}", workingName, e.getMessage());
                trace.transcribeFallback(true).transcribeFallbackReason(e.getMessage());
            }
        }

        // ---- 内置分派（永驻 classpath、不参与选型、缺席不抛） ----
        String markdown = builtIn.dispatch(working, workingName, vision);
        trace.transcribeLeg(RouteTrace.BUILTIN);
        return finish(markdown, workingName, trace, officeInput && !converted);
    }

    private DocumentArtifact finish(String markdown, String workingName,
            RouteTrace.RouteTraceBuilder trace, boolean degradedNoPage) {
        for (EnrichStep step : enrichSteps) {
            String enriched = step.enrich(markdown, workingName);
            if (enriched != null) {
                markdown = enriched;
            }
        }
        return artifactStep.shape(markdown, trace.degradedNoPage(degradedNoPage).build());
    }

    private String pdfNameOf(String filename) {
        int dot = filename.lastIndexOf('.');
        String base = dot > 0 ? filename.substring(0, dot) : filename;
        return base + ".pdf";
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
