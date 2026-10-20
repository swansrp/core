package com.bidr.mineru;

import com.bidr.document.spi.TranscribeStep;

import java.io.IOException;

/**
 * Title: MineruTranscribeStep
 * Description: {@link TranscribeStep} 的 MinerU 适配腿（M16 棒 3：tool/mineru → tool/document
 * 的<b>腿→口</b>依赖方向，口不反向认识本模块）。薄委托 {@link MineruTranscriber}——
 * 腿选择口径（云优先本地兜底）、等待预算、认领面（pdf＋图片）全部沿用原实现，一字不动；
 * 契约「门面负责回落、实现方别吞错」同样沿用（失败如实抛给门面）。
 *
 * @author Sharp
 * @since 2026/10/20
 */
public class MineruTranscribeStep implements TranscribeStep {

    /** 腿名＝yml 选型键 {@code tool.document.transcriber} 的取值 */
    public static final String LEG_NAME = "mineru";

    private final MineruTranscriber delegate;

    public MineruTranscribeStep(MineruTranscriber delegate) {
        this.delegate = delegate;
    }

    @Override
    public String legName() {
        return LEG_NAME;
    }

    @Override
    public boolean isAvailable(String filename) {
        return delegate.isAvailable(filename);
    }

    @Override
    public String transcribe(byte[] content, String filename) throws IOException {
        return delegate.transcribe(content, filename);
    }
}
