package com.bidr.agent.console.skill;

import com.bidr.kernel.exception.NoticeException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Title: SkillPackageParser
 * Description: skill 包解析：zip → 单根文件夹 → SKILL.md frontmatter 校验。
 *
 * <p><b>包契约</b>（与 Claude/Qoder 生态同格式）：zip 内只有一个根文件夹
 * （名 = 技能名，{@value #NAME_PATTERN} 约束 {@code ^[a-zA-Z0-9_-]+$}），
 * 根下必有 {@code SKILL.md}，frontmatter 至少含 {@code name}（须等于根文件夹名）
 * 与 {@code description}（非空）。消费方 profile 可约定 SKILL.md 不写 bash/python，
 * 只引用已注册的工具名——这是约定不是上传校验项。</p>
 *
 * <p><b>中文文件名编码坑</b>：Windows 资源管理器打包的 zip，文件名走本地编码（GBK），
 * 按 UTF-8 解码得到 U+FFFD 或乱码。{@link ZipInputStream} 本就接受 Charset，
 * 策略＝先 UTF-8，出现 U+FFFD 或包契约不满足时按 GBK 重读一次再验。
 * 已知盲区：GBK 串"碰巧"解成合法 UTF-8 乱码（无 U+FFFD）时不可检测——
 * 但那类名字同样过不了 name==根名校验，只会以报错失败，不会静默错包。</p>
 *
 * @author Sharp
 * @since 2026-10-13
 */
public final class SkillPackageParser {

    /** 技能名（= 根文件夹名）约束 */
    public static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");

    public static final String SKILL_MD = "SKILL.md";

    /** zip 解包上限（防炸弹）：总解压 50MB、条目 500 个（条目数同时是上层派发改写条目的上限，共用一把尺） */
    private static final int MAX_TOTAL_BYTES = 50 * 1024 * 1024;
    public static final int MAX_ENTRIES = 500;

    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private static final Charset GBK = Charset.forName("GBK");

    private static final Pattern FIELD = Pattern.compile("(\\w+)\\s*:\\s*(.*)");

    private SkillPackageParser() {
    }

    public static SkillPackage parse(byte[] zipBytes) {
        Map<String, byte[]> utf8 = tryRead(zipBytes, UTF8);
        Map<String, byte[]> entries = utf8;
        if (utf8 == null || containsReplacementChar(utf8) || !contractShapeOk(utf8)) {
            Map<String, byte[]> gbk = tryRead(zipBytes, GBK);
            if (gbk != null) {
                entries = gbk;
            } else if (utf8 == null) {
                throw new NoticeException("skill 包不是合法 zip（文件名按 UTF-8/GBK 均无法解码）");
            }
        }
        return validate(entries);
    }

    // ---------------------------------------------------------------- shape

    /**
     * 读包；仅<b>文件名解码失败</b>返回 null（触发另一编码重试）——JDK 的 ZipInputStream
     * 遇到非法 UTF-8 文件名是抛 {@code IllegalArgumentException(MALFORMED)} 而非替换符，
     * 必须捕获转译；包结构类错误（非 zip/超限/穿越）照常抛出。
     */
    private static Map<String, byte[]> tryRead(byte[] zipBytes, Charset charset) {
        try {
            return readZip(zipBytes, charset);
        } catch (UndecodableNameException e) {
            return null;
        }
    }

    /** 文件名按当前 Charset 解不了（zip 规范的 EFS 位之外还有本地编码打包的常见情形） */
    private static final class UndecodableNameException extends RuntimeException {
    }

    private static Map<String, byte[]> readZip(byte[] zipBytes, Charset charset) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        long total = 0;
        int count = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes), charset)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = normalize(entry.getName());
                if (entry.isDirectory() || skip(name)) {
                    continue;
                }
                if (++count > MAX_ENTRIES) {
                    throw new NoticeException("skill 包条目数超过上限 " + MAX_ENTRIES);
                }
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int n;
                while ((n = zip.read(chunk)) != -1) {
                    total += n;
                    if (total > MAX_TOTAL_BYTES) {
                        throw new NoticeException("skill 包解压总量超过上限 " + MAX_TOTAL_BYTES + " 字节");
                    }
                    buffer.write(chunk, 0, n);
                }
                entries.put(name, buffer.toByteArray());
            }
        } catch (UndecodableNameException e) {
            throw e;
        } catch (NoticeException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new UndecodableNameException();
        } catch (Exception e) {
            throw new NoticeException("skill 包不是合法 zip：" + e.getMessage());
        }
        return entries;
    }

    /** 反斜杠归一为斜杠；出现 ".." 段直接拒绝（zip slip） */
    private static String normalize(String name) {
        String value = name == null ? "" : name.replace('\\', '/');
        for (String segment : value.split("/")) {
            if ("..".equals(segment)) {
                throw new NoticeException("skill 包含路径穿越条目，已拒绝: " + name);
            }
        }
        return value;
    }

    /** macOS 打包噪声不入包 */
    private static boolean skip(String name) {
        return name.startsWith("__MACOSX/") || name.equals(".DS_Store") || name.endsWith("/.DS_Store");
    }

    /** 契约形态（不看 frontmatter 内容）：唯一根文件夹且根下有 SKILL.md */
    private static boolean contractShapeOk(Map<String, byte[]> entries) {
        String root = singleRoot(entries);
        return root != null && NAME_PATTERN.matcher(root).matches() && entries.containsKey(root + "/" + SKILL_MD);
    }

    private static boolean containsReplacementChar(Map<String, byte[]> entries) {
        for (String name : entries.keySet()) {
            if (name.indexOf('\uFFFD') >= 0) {
                return true;
            }
        }
        return false;
    }

    private static String singleRoot(Map<String, byte[]> entries) {
        String root = null;
        for (String name : entries.keySet()) {
            int slash = name.indexOf('/');
            if (slash <= 0) {
                return null;
            }
            String candidate = name.substring(0, slash);
            if (root == null) {
                root = candidate;
            } else if (!root.equals(candidate)) {
                return null;
            }
        }
        return root;
    }

    // ---------------------------------------------------------------- validate

    private static SkillPackage validate(Map<String, byte[]> entries) {
        if (entries.isEmpty()) {
            throw new NoticeException("skill 包为空");
        }
        String root = singleRoot(entries);
        if (root == null) {
            throw new NoticeException("skill 包必须是单根文件夹结构（根下直接是 SKILL.md 与资源文件）");
        }
        if (!NAME_PATTERN.matcher(root).matches()) {
            throw new NoticeException("根文件夹名须为 ^[a-zA-Z0-9_-]+$，实际: " + root
                    + "（若为中文乱码，请用 UTF-8 文件名重新打包）");
        }
        byte[] skillMd = entries.get(root + "/" + SKILL_MD);
        if (skillMd == null) {
            throw new NoticeException("根文件夹下缺少 SKILL.md");
        }
        String markdown = new String(skillMd, UTF8);
        String[] front = parseFrontmatter(markdown);
        String name = front[0];
        String description = front[1];
        if (!root.equals(name)) {
            throw new NoticeException("frontmatter.name（" + name + "）必须等于根文件夹名（" + root + "）");
        }
        if (description == null || description.trim().isEmpty()) {
            throw new NoticeException("frontmatter.description 不能为空（提示词渐进式披露只发 name+description）");
        }
        Map<String, byte[]> relative = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            relative.put(e.getKey().substring(root.length() + 1), e.getValue());
        }
        return new SkillPackage(name, front[2], description.trim(), front[3], markdown, new TreeMap<>(relative));
    }

    /** frontmatter：首行 --- 开、下一个 --- 闭；闭前逐行取 name/description/title/category（后两者可选，null=未提供） */
    private static String[] parseFrontmatter(String markdown) {
        String[] lines = markdown.split("\\r?\\n");
        if (lines.length == 0 || !"---".equals(lines[0].trim())) {
            throw new NoticeException("SKILL.md 缺少 frontmatter（首行须为 ---）");
        }
        String name = null;
        String description = null;
        String title = null;
        String category = null;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if ("---".equals(line.trim())) {
                break;
            }
            Matcher m = FIELD.matcher(line.trim());
            if (m.matches()) {
                if ("name".equals(m.group(1))) {
                    name = stripQuotes(m.group(2));
                } else if ("description".equals(m.group(1))) {
                    description = stripQuotes(m.group(2));
                } else if ("title".equals(m.group(1))) {
                    title = stripQuotes(m.group(2));
                } else if ("category".equals(m.group(1))) {
                    category = stripQuotes(m.group(2));
                }
            }
        }
        if (name == null || name.trim().isEmpty()) {
            throw new NoticeException("SKILL.md frontmatter 缺少 name");
        }
        return new String[]{name.trim(), description,
                title == null || title.trim().isEmpty() ? null : title.trim(),
                category == null || category.trim().isEmpty() ? null : category.trim()};
    }

    private static String stripQuotes(String value) {
        String v = value.trim();
        if (v.length() >= 2 && ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'")))) {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }
}
