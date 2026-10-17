package com.bidr.agent.console.skill;

import com.bidr.kernel.exception.NoticeException;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Title: SkillPackageWriter
 * Description: skill 包<b>确定性</b>打包（出厂包 seed 与规范重打包共用）。
 *
 * <p>同样三件事换来同一份字节：条目按名排序、时间固定、store 不压缩。这不是洁癖——
 * 启动期对账拿包内容指纹 {@code package_sha256} 判断要不要覆盖，
 * 时间戳取当前时刻的话每次启动都会算出一个新 sha，把"没改"误判成"改了"。</p>
 *
 * <p>固定时刻取 {@code 0}（1970）：JDK 写 zip 时间字段时对 1980 年之前的取值回落
 * 常量 {@code (1<<21)|(1<<16)}，因此与运行机器的本地时区无关。</p>
 *
 * @author Sharp
 * @since 2026-10-13
 */
public final class SkillPackageWriter {

    /** 1970 ⇒ JDK 写常量 DOS 时间（TZ 无关），见类注释 */
    private static final long FIXED_TIME = 0L;

    private SkillPackageWriter() {
    }

    /**
     * 打一个单根文件夹的 zip。条目名＝{@code rootName/相对路径}，相对路径用正斜杠。
     *
     * @param rootName 根文件夹名（＝技能名＝frontmatter.name）
     * @param files    包内文件（key＝相对包根的路径）
     */
    public static byte[] pack(String rootName, Map<String, byte[]> files) {
        if (rootName == null || rootName.trim().isEmpty()) {
            throw new NoticeException("skill 包根文件夹名为空");
        }
        if (files == null || files.isEmpty()) {
            throw new NoticeException("skill 包内容为空: " + rootName);
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bos, StandardCharsets.UTF_8)) {
            // TreeMap 保证同名输入恒得同一字节序（不走调用方的 Map 实现）
            for (Map.Entry<String, byte[]> file : new TreeMap<>(files).entrySet()) {
                byte[] data = file.getValue();
                CRC32 crc = new CRC32();
                crc.update(data);
                ZipEntry entry = new ZipEntry(rootName + "/" + file.getKey());
                entry.setMethod(ZipEntry.STORED);
                entry.setTime(FIXED_TIME);
                entry.setSize(data.length);
                entry.setCompressedSize(data.length);
                entry.setCrc(crc.getValue());
                zip.putNextEntry(entry);
                zip.write(data);
                zip.closeEntry();
            }
        } catch (Exception e) {
            throw new NoticeException("skill 包打包失败（" + rootName + "）: " + e.getMessage());
        }
        return bos.toByteArray();
    }

    /**
     * 确定性打包产物的指纹口径：对 {@link #pack} 的字节求 SHA-256，输出小写十六进制（64 字符）。
     *
     * <p>指纹只能跟着 {@code pack} 待在一起：{@code pack} 的全部意义就是"同内容必得同字节"，
     * 而指纹正是这句话的读数器——启动对账拿它判断要不要覆盖，两处实现各写一遍就等于
     * 允许两侧对同一内容报出不同的 sha。</p>
     *
     * <p>与业务文件域 {@code com.bidr.ai.inkhub.util.Digests.sha256Hex} 同算法、同编码、同输出格式，
     * 这个等价性是<b>存量迁移与两侧对账的前提</b>：出厂包从业务侧搬到框架侧后，
     * 库里已有的 {@code package_sha256} 必须还能对上重算出的值，否则每个存量包都会被认成"内容变了"
     * 而就地重写一遍。等价性由测试钉住，不靠约定。</p>
     *
     * <p>{@code Digests} 仍是文件域内容寻址的正源（秒传判定、blob 台账唯一键都吃它），本方法不取代它——
     * 只管 skill 包这一类确定性产物的指纹。</p>
     *
     * @param bytes 打包产物
     * @return 小写十六进制 sha256
     */
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
