package com.bidr.document;

import lombok.Builder;
import lombok.Getter;

/**
 * Title: RouteTrace
 * Description: <b>路线留痕</b>——产物契约的显式字段（立项 §7.3 定形，判据⑤的判别对象）：
 * 同一份文件走了哪条路（哪条 convert 腿×哪条 transcribe 腿／内置直读）＋是否降级无页。
 * 现网业务侧自造的 {@code CONVERT_FALLBACK} 快照（布尔＋原因串）正是这个形状的实证——
 * 该由 core 的产物契约提供，而不是每个项目再写一遍。
 * <p>
 * 字段语义：
 * <ul>
 *   <li>{@code convertLeg}：转换成功的腿名；{@code null}＝未发生转换（腿未装配或该文件非 convert 认领类型）；</li>
 *   <li>{@code convertFallback}：{@code true}＝CONVERT_FALLBACK（腿转不动 retryable=false 后原件直送，
 *       或 office 输入但 convert 腿未装配）；{@code false}＝转换成功；{@code null}＝convert 槽与该文件无关；</li>
 *   <li>{@code transcribeLeg}：增强腿名；{@link #BUILTIN}＝内置直读；</li>
 *   <li>{@code transcribeFallback}：{@code true}＝增强腿抛错/空结果后回落内置（带原因）；</li>
 *   <li>{@code degradedNoPage}：office 输入未获得带版式转换＝引用降级无页（两条路线质量分档的显式化）。</li>
 * </ul>
 *
 * @author Sharp
 * @since 2026/10/20
 */
@Getter
@Builder
public class RouteTrace {

    /** transcribeLeg 的内置直读值 */
    public static final String BUILTIN = "builtin";

    /** 转换成功的 convert 腿名；null＝未发生转换 */
    private final String convertLeg;

    /** CONVERT_FALLBACK 标记：true＝转不动回落原件直送／腿未装配；false＝转换成功；null＝不适用 */
    private final Boolean convertFallback;

    /** fallback 原因（现网快照形状＝布尔＋原因串） */
    private final String convertFallbackReason;

    /** 增强转写腿名；"builtin"＝内置直读 */
    private final String transcribeLeg;

    /** 增强腿抛错/空结果后回落内置＝true */
    private final boolean transcribeFallback;

    /** 增强腿回落原因 */
    private final String transcribeFallbackReason;

    /** office 输入未获得带版式转换＝引用降级无页（降级不许伪装正常） */
    private final boolean degradedNoPage;

    /**
     * 人类可读走法（日志/审计一行）：convert 腿×transcribe 腿，降级时显式标注。
     */
    public String describe() {
        StringBuilder sb = new StringBuilder("convert=");
        sb.append(convertLeg != null ? convertLeg : (convertFallback != null && convertFallback ? "FALLBACK" : "none"));
        sb.append(" × transcribe=").append(transcribeLeg);
        if (transcribeFallback) {
            sb.append("(回落: ").append(transcribeFallbackReason == null ? "" : transcribeFallbackReason).append(')');
        }
        if (degradedNoPage) {
            sb.append(" [引用降级无页]");
        }
        return sb.toString();
    }
}
