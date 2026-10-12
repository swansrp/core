package com.bidr.agent.console.skill;

import com.bidr.kernel.exception.NoticeException;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Title: SkillPackageParserTest
 * Description: skill 包解析单测（离线）：包契约（单根文件夹 + SKILL.md frontmatter）与
 * Windows GBK 中文文件名回退逐条钉住——包格式是 agent 读内容的信任边界。
 *
 * @author Sharp
 * @since 2026-10-13
 */
class SkillPackageParserTest {

    private static final String SKILL_MD = "---\n"
            + "name: %s\n"
            + "description: %s\n"
            + "---\n"
            + "# 使用说明\n正文本体。\n";

    // ---------------------------------------------------------------- happy path

    @Test
    void validPackageParses() throws IOException {
        Map<String, String> contents = new LinkedHashMap<>();
        contents.put("demo-skill/SKILL.md", String.format(SKILL_MD, "demo-skill", "演示技能"));
        contents.put("demo-skill/examples/a.md", "范例内容");
        contents.put("demo-skill/参考.md", "中文文件名（UTF-8 zip）");

        SkillPackage pkg = SkillPackageParser.parse(zip(contents, StandardCharsets.UTF_8.name()));

        assertThat(pkg.getName()).isEqualTo("demo-skill");
        assertThat(pkg.getDescription()).isEqualTo("演示技能");
        assertThat(pkg.getSkillMarkdown()).contains("# 使用说明");
        assertThat(pkg.getFiles().keySet()).containsExactly("SKILL.md", "examples/a.md", "参考.md");
        assertThat(new String(pkg.file("examples/a.md"), StandardCharsets.UTF_8)).isEqualTo("范例内容");
    }

    /** Windows 资源管理器打包：中文文件名走 GBK，UTF-8 读出 U+FFFD 须自动回退 GBK */
    @Test
    void gbkChineseFileNamesFallBack() throws IOException {
        Map<String, String> contents = new LinkedHashMap<>();
        contents.put("demo-skill/SKILL.md", String.format(SKILL_MD, "demo-skill", "演示技能"));
        contents.put("demo-skill/参考资料/说明.md", "中文路径正文");

        SkillPackage pkg = SkillPackageParser.parse(zip(contents, "GBK"));

        assertThat(pkg.getName()).isEqualTo("demo-skill");
        assertThat(pkg.getFiles().keySet()).containsExactly("SKILL.md", "参考资料/说明.md");
        assertThat(new String(pkg.file("参考资料/说明.md"), StandardCharsets.UTF_8)).isEqualTo("中文路径正文");
    }

    /** macOS 打包噪声（__MACOSX/.DS_Store）不占文件清单 */
    @Test
    void macosNoiseIgnored() throws IOException {
        Map<String, String> contents = new LinkedHashMap<>();
        contents.put("demo-skill/SKILL.md", String.format(SKILL_MD, "demo-skill", "演示技能"));
        contents.put("__MACOSX/demo-skill/._SKILL.md", "junk");
        contents.put("demo-skill/.DS_Store", "junk");

        SkillPackage pkg = SkillPackageParser.parse(zip(contents, StandardCharsets.UTF_8.name()));

        assertThat(pkg.getFiles().keySet()).containsExactly("SKILL.md");
    }

    // ---------------------------------------------------------------- contract rejections

    @Test
    void multiRootRejected() throws IOException {
        Map<String, String> contents = new LinkedHashMap<>();
        contents.put("skill-a/SKILL.md", String.format(SKILL_MD, "skill-a", "A"));
        contents.put("skill-b/other.txt", "B");
        assertThrows(NoticeException.class, () -> SkillPackageParser.parse(zip(contents, StandardCharsets.UTF_8.name())));
    }

    /** 根下直接是 SKILL.md（无根文件夹包裹）不符合单根契约 */
    @Test
    void flatStructureRejected() throws IOException {
        Map<String, String> contents = new LinkedHashMap<>();
        contents.put("SKILL.md", String.format(SKILL_MD, "demo", "X"));
        assertThrows(NoticeException.class, () -> SkillPackageParser.parse(zip(contents, StandardCharsets.UTF_8.name())));
    }

    @Test
    void missingSkillMdRejected() throws IOException {
        Map<String, String> contents = new LinkedHashMap<>();
        contents.put("demo-skill/other.txt", "没有 SKILL.md");
        assertThrows(NoticeException.class, () -> SkillPackageParser.parse(zip(contents, StandardCharsets.UTF_8.name())));
    }

    @Test
    void frontmatterNameMustEqualRootFolder() throws IOException {
        Map<String, String> contents = new LinkedHashMap<>();
        contents.put("demo-skill/SKILL.md", String.format(SKILL_MD, "other-name", "X"));
        NoticeException e = assertThrows(NoticeException.class,
                () -> SkillPackageParser.parse(zip(contents, StandardCharsets.UTF_8.name())));
        assertThat(e.getMessage()).contains("根文件夹名");
    }

    @Test
    void missingDescriptionRejected() throws IOException {
        Map<String, String> contents = new LinkedHashMap<>();
        contents.put("demo-skill/SKILL.md", "---\nname: demo-skill\n---\n正文");
        assertThrows(NoticeException.class, () -> SkillPackageParser.parse(zip(contents, StandardCharsets.UTF_8.name())));
    }

    @Test
    void missingFrontmatterRejected() throws IOException {
        Map<String, String> contents = new LinkedHashMap<>();
        contents.put("demo-skill/SKILL.md", "# 直接正文，没有 frontmatter");
        assertThrows(NoticeException.class, () -> SkillPackageParser.parse(zip(contents, StandardCharsets.UTF_8.name())));
    }

    /** zip slip：条目名带 ".." 段直接拒绝，不进文件清单 */
    @Test
    void pathTraversalEntryRejected() throws IOException {
        Map<String, String> contents = new LinkedHashMap<>();
        contents.put("demo-skill/SKILL.md", String.format(SKILL_MD, "demo-skill", "X"));
        contents.put("demo-skill/../evil.txt", "boom");
        assertThrows(NoticeException.class, () -> SkillPackageParser.parse(zip(contents, StandardCharsets.UTF_8.name())));
    }

    // ---------------------------------------------------------------- fixture

    /** 按指定文件名编码打 zip：UTF-8 用 JDK（与生产读取同一套 API），GBK 用 commons-compress */
    private static byte[] zip(Map<String, String> contents, String nameEncoding) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (StandardCharsets.UTF_8.name().equals(nameEncoding)) {
            try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(out, StandardCharsets.UTF_8)) {
                for (Map.Entry<String, String> e : contents.entrySet()) {
                    zip.putNextEntry(new java.util.zip.ZipEntry(e.getKey()));
                    zip.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                }
            }
        } else {
            try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(out)) {
                zip.setEncoding(nameEncoding);
                for (Map.Entry<String, String> e : contents.entrySet()) {
                    zip.putArchiveEntry(new ZipArchiveEntry(e.getKey()));
                    zip.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                    zip.closeArchiveEntry();
                }
            }
        }
        return out.toByteArray();
    }
}
