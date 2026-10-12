package com.bidr.agent.console.skill;

import com.bidr.kernel.exception.NoticeException;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
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
}