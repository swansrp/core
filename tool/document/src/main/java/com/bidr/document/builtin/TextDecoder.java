package com.bidr.document.builtin;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Title: TextDecoder
 * Description: 内置文本腿＝历史 {@code FileMarkdownService.decodeText} 逐字迁入：
 * UTF-8 严格解码，失败回落 GBK；剥离 UTF-8 BOM。
 *
 * @author Sharp
 * @since 2026/10/20
 */
public final class TextDecoder {

    /**
     * UTF-8 BOM（U+FEFF）。M16 修复轮 B13：原先这里直接粘了一个不可见字符——肉眼看不出、
     * diff 也看不出，谁再格式化一次就可能被编辑器悄悄吃掉；改用转义字面量。
     */
    private static final char BOM = '\uFEFF';

    private TextDecoder() {
    }

    public static String decode(byte[] bytes) {
        String text;
        try {
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            text = decoder.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            text = new String(bytes, Charset.forName("GBK"));
        }
        if (!text.isEmpty() && text.charAt(0) == BOM) {
            text = text.substring(1);
        }
        return text;
    }
}
