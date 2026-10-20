package com.bidr.document;

import com.bidr.document.builtin.BuiltInDispatcher;
import com.bidr.document.spi.ConvertStep;
import com.bidr.document.spi.TranscribeStep;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * Title: FileRouter
 * Description: 路由＝「逐腿问认领」：convert 腿（装配序）→ 选型后的 transcribe 腿 → 内置分派面。
 * {@link #isProcessable(String)} 是路由分派的<b>副产物</b>——只答「有没有腿认领它」，
 * 不做扩展名清单判定、不抛「不支持」（能力面不削；受理收口＝业务侧自己的闸，按质量档挡）。
 *
 * @author Sharp
 * @since 2026/10/20
 */
@RequiredArgsConstructor
public class FileRouter {

    private final List<ConvertStep> convertSteps;

    private final TranscribeStep transcribeStep;

    private final BuiltInDispatcher builtIn;

    /**
     * 有没有腿认领该文件（convert 腿 / 增强转写腿 / 内置分派面任一认领即 true）。
     * 未知扩展名由内置 NUL 探测腿认领（内容级二进制拒绝在加工时发生）。
     */
    public boolean isProcessable(String filename) {
        for (ConvertStep step : convertSteps) {
            if (step.isConvertible(filename)) {
                return true;
            }
        }
        if (transcribeStep != null && transcribeStep.isAvailable(filename)) {
            return true;
        }
        return builtIn.claims(filename);
    }

    /**
     * 第一条认领该文件的 convert 腿（装配序；无认领＝null＝convert 槽缺席，原件往下送）。
     */
    public ConvertStep convertLegFor(String filename) {
        for (ConvertStep step : convertSteps) {
            if (step.isConvertible(filename)) {
                return step;
            }
        }
        return null;
    }
}
