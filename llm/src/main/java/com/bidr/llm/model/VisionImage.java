package com.bidr.llm.model;

import lombok.Getter;

import java.util.Base64;

/**
 * 视觉请求携带的图片（{@link RawVisionChatModel} 用）：文件名 + 字节。
 * <p>mime 按扩展名推断（png/gif/webp/bmp，默认 jpeg），编码 data URI 时使用。</p>
 *
 * @author Sharp
 */
@Getter
public class VisionImage {

    private final String name;

    private final byte[] data;

    public VisionImage(String name, byte[] data) {
        this.name = name;
        this.data = data == null ? new byte[0] : data;
    }

    /** 按扩展名推断 mime（默认 image/jpeg） */
    public String mime() {
        String lower = name == null ? "" : name.toLowerCase();
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".bmp")) {
            return "image/bmp";
        }
        return "image/jpeg";
    }

    /** base64（标准编码，data URI 用） */
    public String base64() {
        return Base64.getEncoder().encodeToString(data);
    }
}
