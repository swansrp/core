package com.bidr.document;

import com.bidr.document.spi.TranscribeStep;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Title: TranscribeLegSelector
 * Description: transcribe 增强腿选型（三态语义正源，§3.1）：
 * <ul>
 *   <li>键空＝不选（走内置，不抛）；classpath 上有自装腿时按老口径取第一条（容忍用户自注册）；</li>
 *   <li>键配了但无同名腿（包没引）＝WARN「选型指向不存在的腿，已走内置」后走内置；</li>
 *   <li>键配了且同名腿在＝用它（失败回落内置归门面，实现方别吞错）。</li>
 * </ul>
 * R-3 配套：本模块的增强腿实现应以 {@code @ConditionalOnProperty(name = "tool.document.transcriber",
 * havingValue = "<腿名>")} 自 gate——同一时刻至多一条在装，此处按名复核是第二道保险。
 *
 * @author Sharp
 * @since 2026/10/20
 */
@Slf4j
public final class TranscribeLegSelector {

    private TranscribeLegSelector() {
    }

    /**
     * 按选型键从在装腿里挑一条；挑不出＝null（走内置）。
     *
     * @param legs     classpath 上装配到的全部 transcribe 腿（可为空）
     * @param selected {@code tool.document.transcriber} 的值
     */
    public static TranscribeStep select(List<TranscribeStep> legs, String selected) {
        if (!StringUtils.hasText(selected)) {
            return legs.isEmpty() ? null : legs.get(0);
        }
        for (TranscribeStep leg : legs) {
            if (selected.equals(leg.legName())) {
                return leg;
            }
        }
        log.warn("选型指向不存在的腿，已走内置: tool.document.transcriber={}（在装腿: {}）",
                selected, legNames(legs));
        return null;
    }

    private static String legNames(List<TranscribeStep> legs) {
        return legs.stream().map(TranscribeStep::legName).collect(Collectors.joining(","));
    }
}
