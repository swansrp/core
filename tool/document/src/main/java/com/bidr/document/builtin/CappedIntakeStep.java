package com.bidr.document.builtin;

import com.bidr.document.DocumentProperties;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Title: CappedIntakeStep
 * Description: Intake 槽内置实现＝历史 {@code FileMarkdownService.readAllCapped} 逐字迁入：
 * 读全字节并施加大小上限（{@code tool.document.max-file-size-bytes} 可配，缺省 100MB 不变），
 * 超限抛 {@link IllegalArgumentException}（语义与报错口径不变）。
 *
 * @author Sharp
 * @since 2026/10/20
 */
public class CappedIntakeStep implements com.bidr.document.spi.IntakeStep {

    private final DocumentProperties properties;

    public CappedIntakeStep(DocumentProperties properties) {
        this.properties = properties;
    }

    @Override
    public byte[] read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > properties.getMaxFileSizeBytes()) {
                throw new IllegalArgumentException(
                        "文件超过大小限制 " + (properties.getMaxFileSizeBytes() / 1024 / 1024) + "MB");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
