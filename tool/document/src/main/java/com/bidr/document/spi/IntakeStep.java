package com.bidr.document.spi;

import java.io.IOException;
import java.io.InputStream;

/**
 * Title: IntakeStep
 * Description: 文件加工流水线的第 1 槽——<b>原件受理</b>：把进入的流读全为字节并施加防御性上限
 * （超限抛 {@link IllegalArgumentException}，语义同历史 {@code FileMarkdownService.readAllCapped}）。
 * <p>
 * 内置实现＝{@code builtin.CappedIntakeStep}（{@code tool.document.max-file-size-bytes} 可配，
 * 缺省 100MB 不变）。业务要换受理策略（如换大上限、边读边校验魔数）＝注册自己的
 * {@link IntakeStep} Bean 覆盖。本槽不抛「不支持」类错误——能力判定归路由。
 *
 * @author Sharp
 * @since 2026/10/20
 */
public interface IntakeStep {

    /**
     * 受理原件：读全字节并施加大小上限。
     *
     * @param in 原件流（调用方负责关闭）
     * @return 原件全量字节
     * @throws IOException 读流失败
     */
    byte[] read(InputStream in) throws IOException;
}
