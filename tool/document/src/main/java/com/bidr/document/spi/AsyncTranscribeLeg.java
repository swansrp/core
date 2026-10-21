package com.bidr.document.spi;

import java.util.List;

/**
 * Title: AsyncTranscribeLeg
 * Description: 文件加工流水线的<b>异步</b>转录口（M17 棒 1，与同步 {@link TranscribeStep} 同族
 * 的"口在定议方"先例）：分钟级外部引擎的 submit→poll→fetch 三步，供编排层以
 * 「提交即返回、轮询走退避」的形状消费——等待不占跑批线程、不占租约。
 *
 * <p>契约要点：</p>
 * <ul>
 *   <li><b>腿内自管子路线</b>：实现可以在内部有多条物理路线（如 MinerU 的云/本地两腿），
 *       {@link Submission#getRoute()} 返回不透明路线串，{@link #poll}/{@link #fetch} 按原串
 *       寻址——编排层只存不解释（两腿任务 id 不在同一命名空间，串号只会 404）；</li>
 *   <li><b>配置可缺席</b>：{@link #hasLeg()} false＝本腿没配置（调用方走内置三态，缺席不抛）；
 *       装配缺席由实现模块的装配条件管（如 {@code mineru.enabled:false}＝Bean 不存在）；</li>
 *   <li><b>归一口径</b>：{@link Progress#getStatus()} 只出
 *       {@link #STATUS_PENDING}/{@link #STATUS_RUNNING}/{@link #STATUS_COMPLETED}/
 *       {@link #STATUS_FAILED} 四值——实现把自家状态机映射进来，编排层不认识任何引擎词；</li>
 *   <li><b>失败如实抛</b>：提交/轮询/取件的临时故障原样抛RuntimeException 交调用方重试预算，
 *       别吞错别兜底（与同步腿同一纪律）。</li>
 * </ul>
 * 依赖方向：实现模块（tool/mineru 等）→ 本口；本模块不反向依赖编排层（腿→口，无环）。
 *
 * @author Sharp
 * @since 2026-10-21
 */
public interface AsyncTranscribeLeg {

    String STATUS_PENDING = "PENDING";

    String STATUS_RUNNING = "RUNNING";

    String STATUS_COMPLETED = "COMPLETED";

    String STATUS_FAILED = "FAILED";

    /** 腿名（编排层选型键 {@code engine.parse.transcriber} 的取值对它点名，如 {@code mineru}） */
    String legName();

    /** 本腿有没有可用配置（两腿全空＝false，调用方走内置三态；不抛） */
    boolean hasLeg();

    /**
     * 提交异步解析单（即返；引擎在跑是常态不是失败）。
     *
     * @param content       原件字节
     * @param filename      原始文件名（引擎按扩展名分派解析器）
     * @param extractImages 是否要图片条目（引擎产物含图时必须 true）
     */
    Submission submit(byte[] content, String filename, boolean extractImages);

    /**
     * 轮询一次（归一四态）。目标路线不可用（配置被撤）＝可读 ISE，<b>不许回落另一条路线</b>——
     * 各路线的任务 id 不在同一命名空间，串号只会把错误指向无关的地方。
     */
    Progress poll(String route, String externalId);

    /** 取结果（同 {@link #poll}：路线不可用＝可读 ISE，不换路线） */
    Artifact fetch(String route, String externalId);

    /** 提交结果：不透明路线串＋引擎侧任务 id＋提交时刻（毫秒 epoch，等待预算锚点） */
    final class Submission {

        private final String route;

        private final String externalId;

        private final long since;

        public Submission(String route, String externalId, long since) {
            this.route = route;
            this.externalId = externalId;
            this.since = since;
        }

        public String getRoute() {
            return route;
        }

        public String getExternalId() {
            return externalId;
        }

        public long getSince() {
            return since;
        }
    }

    /** 归一进度：四态＋引擎侧阶段词＋排队位次（可空）＋失败原因 */
    final class Progress {

        private final String status;

        private final String phase;

        private final Integer queueAhead;

        private final String errorMessage;

        public Progress(String status, String phase, Integer queueAhead, String errorMessage) {
            this.status = status;
            this.phase = phase;
            this.queueAhead = queueAhead;
            this.errorMessage = errorMessage;
        }

        public String getStatus() {
            return status;
        }

        public String getPhase() {
            return phase;
        }

        public Integer getQueueAhead() {
            return queueAhead;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    /** 取件产物：markdown＋原文归一清单（如 content_list，归一化归编排层）＋图片字节 */
    final class Artifact {

        private final String markdown;

        private final String contentList;

        private final List<Image> images;

        public Artifact(String markdown, String contentList, List<Image> images) {
            this.markdown = markdown;
            this.contentList = contentList;
            this.images = images;
        }

        public String getMarkdown() {
            return markdown;
        }

        public String getContentList() {
            return contentList;
        }

        public List<Image> getImages() {
            return images;
        }
    }

    /** 产物内图片：产物内相对名＋字节 */
    final class Image {

        private final String name;

        private final byte[] data;

        public Image(String name, byte[] data) {
            this.name = name;
            this.data = data;
        }

        public String getName() {
            return name;
        }

        public byte[] getData() {
            return data;
        }
    }
}
