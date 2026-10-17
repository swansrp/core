package com.bidr.agent.console.skill;

import com.bidr.kernel.exception.NoticeException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Title: SkillPackageWriterTest
 * Description: 确定性打包器单测（10-02）：出厂包对账拿 {@code package_sha256} 当内容指纹，
 * 前提就是"同内容必得同字节"。这里钉住三条：
 * <ol>
 *   <li>同内容重打包字节全等（时间戳/压缩方式/条目序都不许污染指纹）；</li>
 *   <li>条目插入序不同也字节全等（内部按名排序）；</li>
 *   <li>产物照旧过 {@link SkillPackageParser} 契约（打包与解析同一把尺）。</li>
 * </ol>
 *
 * @author Sharp
 * @since 2026-10-13
 */
class SkillPackageWriterTest {

    private static final String SKILL_MD = "---\n"
            + "name: demo-skill\n"
            + "description: 演示技能\n"
            + "title: 演示\n"
            + "category: 通用\n"
            + "---\n"
            + "# 使用说明\n正文本体。\n";

    @Test
    void sameContentPacksToIdenticalBytes() {
        Map<String, byte[]> files = files();

        byte[] first = SkillPackageWriter.pack("demo-skill", files);
        byte[] second = SkillPackageWriter.pack("demo-skill", files());

        assertArrayEquals(first, second, "同内容必须同字节：sha256 才配当内容指纹、重启对账才不会假更新");
    }

    @Test
    void entryInsertionOrderDoesNotChangeBytes() {
        Map<String, byte[]> forward = new LinkedHashMap<>();
        forward.put("SKILL.md", utf8(SKILL_MD));
        forward.put("examples/a.md", utf8("范例"));
        forward.put("templates/b.md", utf8("模板"));

        Map<String, byte[]> backward = new LinkedHashMap<>();
        backward.put("templates/b.md", utf8("模板"));
        backward.put("examples/a.md", utf8("范例"));
        backward.put("SKILL.md", utf8(SKILL_MD));

        assertArrayEquals(SkillPackageWriter.pack("demo-skill", forward),
                SkillPackageWriter.pack("demo-skill", backward),
                "打包内部须按名排序：文件系统枚举序不定，不能漏进字节");
    }

    @Test
    void packedZipStillParsesAsSkillPackage() {
        SkillPackage pkg = SkillPackageParser.parse(SkillPackageWriter.pack("demo-skill", files()));

        assertThat(pkg.getName()).isEqualTo("demo-skill");
        assertThat(pkg.getTitle()).isEqualTo("演示");
        assertThat(pkg.getCategory()).isEqualTo("通用");
        assertThat(pkg.getFiles().keySet()).containsExactly("SKILL.md", "examples/a.md", "templates/b.md");
        assertThat(new String(pkg.file("templates/b.md"), StandardCharsets.UTF_8)).isEqualTo("模板");
    }

    @Test
    void blankRootOrEmptyContentRejected() {
        assertThrows(NoticeException.class, () -> SkillPackageWriter.pack("  ", files()));
        assertThrows(NoticeException.class, () -> SkillPackageWriter.pack("demo-skill", null));
        assertThrows(NoticeException.class, () -> SkillPackageWriter.pack("demo-skill", new LinkedHashMap<>()));
    }

    // ---------------------------------------------------------------- 夹具

    private static Map<String, byte[]> files() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("SKILL.md", utf8(SKILL_MD));
        files.put("examples/a.md", utf8("范例"));
        files.put("templates/b.md", utf8("模板"));
        return files;
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 条目时间必须取自常量、不能取自时钟：同秒内两次打包本就会同字节，掩盖"时间用了 now"的回归，
     * 所以直接读回 zip 条目时间断言它落在 1990 年之前（时钟值必是当前年份，一抓即中）。
     */
    @Test
    void entryTimeComesFromConstantNotClock() throws Exception {
        java.util.List<Long> times = new java.util.ArrayList<>();
        try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(
                new java.io.ByteArrayInputStream(SkillPackageWriter.pack("demo-skill", files())))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                times.add(entry.getTime());
            }
        }
        assertThat(times).isNotEmpty();
        java.util.Calendar t = java.util.Calendar.getInstance();
        for (Long ms : times) {
            t.setTimeInMillis(ms);
            assertThat(t.get(java.util.Calendar.YEAR))
                    .as("zip 条目时间必须固定（当前年份=%s）", java.time.Year.now())
                    .isLessThan(1990);
        }
    }
}