package com.bidr.document.builtin;

/**
 * Title: BinaryProbe
 * Description: 内置未知扩展名探测腿＝历史 {@code FileMarkdownService.looksBinary} 逐字迁入：
 * 前 8KB 内出现 NUL 字节即视为二进制。认领面＝一切未知扩展名（内容级拒绝在加工时发生：
 * 二进制拒、非二进制当文本），受理收口归业务侧自己的闸。
 *
 * @author Sharp
 * @since 2026/10/20
 */
public final class BinaryProbe {

    private BinaryProbe() {
    }

    public static boolean looksBinary(byte[] bytes) {
        int limit = Math.min(bytes.length, 8192);
        for (int i = 0; i < limit; i++) {
            if (bytes[i] == 0) {
                return true;
            }
        }
        return false;
    }
}
