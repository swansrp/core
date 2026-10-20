package com.bidr.blob;

import java.security.MessageDigest;

/**
 * 摘要工具（全系统 sha256 唯一口径，M4 Step3 归拢：上传/技能包/渲染/人写页共用；M16 棒 1 自 inkhub-common 下沉）。
 *
 * <p>内容寻址落 blob 都以本方法为指纹来源，勿再另写摘要实现——秒传判定、
 * blob 台账唯一键与产物 key 全部依赖同一算法口径。</p>
 *
 * @author sharp
 */
public final class Digests {

    private Digests() {
    }

    public static String sha256Hex(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
