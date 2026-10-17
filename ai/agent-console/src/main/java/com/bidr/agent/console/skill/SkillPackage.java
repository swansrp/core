package com.bidr.agent.console.skill;

import lombok.Getter;

import java.util.Collections;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Title: SkillPackage
 * Description: 解析后的 skill 包：包结构契约的内存形态——单根文件夹 + SKILL.md frontmatter。
 *
 * <p>{@link #getFiles()} 的 key 相对根文件夹（{@code SKILL.md}、{@code examples/a.md}），
 * 由 {@link SkillPackageParser} 保证不含路径穿越段。</p>
 *
 * @author Sharp
 * @since 2026-10-13
 */
@Getter
public class SkillPackage {

    /** 技能名（= 根文件夹名 = frontmatter.name） */
    private final String name;

    /** frontmatter.title（中文显示名，可选；null=未提供） */
    private final String title;

    /** frontmatter.description（提示词渐进式披露的正文来源） */
    private final String description;

    /** frontmatter.category（消费方分组展示用，可选；null=未提供） */
    private final String category;

    /** SKILL.md 全文（UTF-8 解码） */
    private final String skillMarkdown;

    /** 相对根文件夹的文件路径 -> 内容（含 SKILL.md；只含文件，目录项不占位） */
    private final SortedMap<String, byte[]> files;

    public SkillPackage(String name, String title, String description, String category,
                        String skillMarkdown, SortedMap<String, byte[]> files) {
        this.name = name;
        this.title = title;
        this.description = description;
        this.category = category;
        this.skillMarkdown = skillMarkdown;
        this.files = Collections.unmodifiableSortedMap(new TreeMap<>(files));
    }

    public byte[] file(String path) {
        return files.get(path);
    }
}
