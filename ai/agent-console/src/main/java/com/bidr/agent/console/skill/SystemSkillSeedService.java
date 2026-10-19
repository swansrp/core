package com.bidr.agent.console.skill;

import com.bidr.agent.console.dao.entity.SkillAsset;
import com.bidr.agent.console.dao.repository.SkillAssetService;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.exception.NoticeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Title: SystemSkillSeedService
 * Description: 系统级出厂技能包对账器：jar 内 {@code agent-skills/**} 随发版更新，
 * 启动时按<b>内容 sha</b> 把包账目对齐到 {@code sys_skill_asset}。
 *
 * <p><b>为什么是"对账"而不是"每次启动重传一遍"</b>：无条件重传会把管理员的决策抹平——
 * 技能被下线（{@code OFFLINE}）之后再重启，如果只是覆盖，等于重启把它偷偷发布回去。
 * 所以五个分支各自钉住一条裁定，顺序也不能换（先认所有权、再看墓碑、最后才比内容）：
 * <ol>
 *   <li>同名无行＝注册：{@code PUBLISHED}、{@code BUILTIN}、恒 v1；</li>
 *   <li>{@code origin != BUILTIN}＝<b>让位</b>：UPLOADED 行是运行期产生的数据，不是代码的附属物，
 *       发版内容永远不许覆盖用户资产；</li>
 *   <li>{@code status == TOMBSTONE}＝<b>不复活</b>：注销是人的决策，重启不得把包装回来
 *       （charter 验收项"卸载内置包后重启不复活"）；</li>
 *   <li>sha 相同且行有效＝未变，零写（不涨版本、不碰状态、不重传对象）；</li>
 *   <li>其余＝就地覆盖<b>同一个 key、同一个 v1</b>，且 status 一字不动。</li>
 * </ol></p>
 *
 * <p><b>所有权哨兵是 {@code origin}，不是 {@code create_by}</b>：业务侧出厂器拿
 * {@code create_by="system"} 当哨兵，那是它那张表的既有列；本表有专门的来源档，
 * 用 {@code create_by} 判所有权会把"系统管理员代传的技能"（人建的行）误认成出厂包。
 * 注册时也不写 {@code create_by}——启动线程没有登录上下文，
 * {@code FieldFill.INSERT} 留空是设计内的事（见实体与 Schema 的说明）。</p>
 *
 * <p>包源发现与确定性打包的尺与业务侧出厂器一致：隐藏条目不入包、目录项不入包
 * （否则 sha 会随"目录算不算条目"漂移）、技能名过 {@link SkillPackageParser#NAME_PATTERN}，
 * 入库前先拿上传闸门的同一把尺自检一遍契约，不合契约的包直接跳过——发版内容不该把启动搞挂，
 * 更不该把一个坏包写进台账。</p>
 *
 * <p>与业务侧出厂器的另一处刻意差异：这里<b>没有</b> {@code bindIntoSpace}。core 不认空间概念，
 * "哪些空间自动安装了这个包"是接入方自己的账，框架只出机制。</p>
 *
 * @author Sharp
 * @since 2026-10-13
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemSkillSeedService {

    /** 出厂包在 jar 内的根目录（框架构命名空间，与业务侧 {@code skills/} 互斥，见类注释） */
    public static final String BUILTIN_DIR = "agent-skills/";

    /** 出厂包版本：恒 1（内容变了就地覆盖，不涨版本——用户视角没有"版本变化"这回事） */
    public static final int BUILTIN_VERSION = 1;

    /**
     * 扫描模式用 {@code classpath*:}（遍历所有 jar），而目录名换成 {@code agent-skills/} 而非 {@code skills/}：
     * 业务侧出厂器扫的是 {@code classpath*:skills/**}，同样遍历每个 jar——框架 jar 若也 ship {@code skills/…}，
     * 业务侧就会把框架的包再往 {@code kb_skill} 种一遍。两个模式的路径解析标记都是
     * {@code "/" + dir}，故 {@code agent-skills/} 与 {@code skills/} 天然互斥，各扫各的。
     */
    private static final String SCAN_PATTERN = "classpath*:" + BUILTIN_DIR + "**";

    private final SkillAssetService skillAssetService;

    private final SystemSkillStore store;

    /** 对账结果（启动日志与单测都用它，别用返回值以外的副作用判断成败） */
    public static final class Outcome {

        public int added;

        public int refreshed;

        public int unchanged;

        public int skipped;

        public final List<String> notes = new ArrayList<>();

        public boolean changed() {
            return added > 0 || refreshed > 0;
        }

        @Override
        public String toString() {
            return "新增 " + added + "／内容更新 " + refreshed + "／未变 " + unchanged
                    + "／跳过 " + skipped + (notes.isEmpty() ? "" : "；" + String.join(" | ", notes));
        }
    }

    /**
     * 启动对账：jar 内每个出厂包逐一对账（幂等，失败向上抛给调用方记录，不在此吞掉）。
     */
    public Outcome seed() {
        Outcome outcome = new Outcome();
        for (Map.Entry<String, byte[]> pack : discoverPacks().entrySet()) {
            applyOne(pack.getKey(), pack.getValue(), outcome);
        }
        if (outcome.changed()) {
            log.info("系统出厂技能包对账完成: {}", outcome);
        }
        return outcome;
    }

    // ---------------------------------------------------------------- 包源发现

    /**
     * 扫 jar 内 {@code agent-skills/} 下所有出厂包，打确定性 zip（内容同上一次启动 ⇒ 字节同上一次）。
     *
     * <p>包源发现独立成一个可覆写方法：单测喂合成包校验对账分支，生产路径就是扫 jar，
     * 分支用例不受真实出厂包增减影响。</p>
     */
    Map<String, byte[]> discoverPacks() {
        PathMatchingResourcePatternResolver resolver =
                new PathMatchingResourcePatternResolver(getClass().getClassLoader());
        Resource[] resources;
        try {
            resources = resolver.getResources(SCAN_PATTERN);
        } catch (Exception e) {
            throw new NoticeException("系统出厂技能包扫描失败: " + e.getMessage());
        }
        Map<String, Map<String, byte[]>> packs = new TreeMap<>();
        for (Resource resource : resources) {
            String path = resourcePath(resource);
            if (path == null) {
                continue;
            }
            int slash = path.indexOf('/');
            if (slash <= 0) {
                continue;
            }
            String name = path.substring(0, slash);
            String relative = path.substring(slash + 1);
            // 目录条目（文件系统 classpath 下 ** 会返回目录，jar 内未必）不入包：
            // 它会让 sha 随"目录算不算条目"漂移，而 sha 是重启对账的内容指纹
            if (relative.isEmpty() || relative.endsWith("/") || hidden(relative)
                    || !SkillPackageParser.NAME_PATTERN.matcher(name).matches()) {
                continue;
            }
            packs.computeIfAbsent(name, k -> new TreeMap<>())
                    .put(relative, readBytes(resource, name, relative));
        }
        Map<String, byte[]> zips = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, byte[]>> pack : packs.entrySet()) {
            zips.put(pack.getKey(), SkillPackageWriter.pack(pack.getKey(), pack.getValue()));
        }
        return zips;
    }

    /** jar 内 URL 形如 {@code jar:file:/…/agent-console.jar!/agent-skills/demo/SKILL.md} */
    private static String resourcePath(Resource resource) {
        try {
            String url = resource.getURL().toString();
            String marker = "/" + BUILTIN_DIR;
            int idx = url.lastIndexOf(marker);
            if (idx < 0) {
                return null;
            }
            return url.substring(idx + marker.length());
        } catch (Exception e) {
            log.warn("系统出厂技能包路径无法解析，跳过: {}", e.getMessage());
            return null;
        }
    }

    /** 任一层以点开头的条目不入包（与业务侧出厂器同口径） */
    private static boolean hidden(String relative) {
        for (String segment : relative.split("/")) {
            if (segment.startsWith(".")) {
                return true;
            }
        }
        return false;
    }

    private static byte[] readBytes(Resource resource, String pack, String relative) {
        try (java.io.InputStream in = resource.getInputStream()) {
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, n);
            }
            return buffer.toByteArray();
        } catch (Exception e) {
            throw new NoticeException("系统出厂技能包文件读取失败: " + pack + "/" + relative
                    + " — " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- 对账

    private void applyOne(String packDir, byte[] zip, Outcome outcome) {
        SkillPackage pkg;
        try {
            // 与入库闸门同一把尺：出厂包也要过自己的契约（名字/根文件夹/必需字段）
            pkg = SkillPackageParser.parse(zip);
        } catch (Exception e) {
            outcome.skipped++;
            outcome.notes.add(packDir + " 契约自检失败：" + e.getMessage());
            log.warn("系统出厂技能包不合契约，跳过: {} — {}", packDir, e.getMessage());
            return;
        }
        String sha = SkillPackageWriter.sha256Hex(zip);
        SkillAsset existing = skillAssetService.findByName(pkg.getName());
        if (existing == null) {
            register(pkg, zip, sha);
            outcome.added++;
            return;
        }
        if (!SkillAsset.ORIGIN_BUILTIN.equals(existing.getOrigin())) {
            outcome.skipped++;
            outcome.notes.add(pkg.getName() + " 同名技能非出厂（origin=" + existing.getOrigin() + "），让位");
            log.warn("系统出厂技能包与既有同名技能冲突，跳过: name={} 既有 origin={} status={} v{}",
                    pkg.getName(), existing.getOrigin(), existing.getStatus(), existing.getVersion());
            return;
        }
        if (SkillAsset.STATUS_TOMBSTONE.equals(existing.getStatus())) {
            outcome.skipped++;
            outcome.notes.add(pkg.getName() + " 已注销（TOMBSTONE），不复活");
            log.info("系统出厂技能包已被注销，跳过: name={}（注销是人的决策，重启不得把包装回来）", pkg.getName());
            return;
        }
        if (sha.equals(existing.getPackageSha256()) && CommonConst.YES.equals(existing.getValid())) {
            outcome.unchanged++;
            log.info("系统出厂技能包内容未变: name={} sha={}", pkg.getName(), sha);
            return;
        }
        refresh(pkg, zip, sha, existing);
        outcome.refreshed++;
    }

    private void register(SkillPackage pkg, byte[] zip, String sha) {
        String key = SystemSkillStore.packageKey(pkg.getName(), BUILTIN_VERSION);
        store.put(key, zip);
        SkillAsset row = new SkillAsset();
        row.setSkillName(pkg.getName());
        row.setTitle(pkg.getTitle());
        row.setDescription(pkg.getDescription());
        row.setCategory(pkg.getCategory());
        row.setVersion(BUILTIN_VERSION);
        row.setStatus(SkillAsset.STATUS_PUBLISHED);
        // 所有权哨兵：来源档是唯一可信的所有权标记，create_by 不是（见类注释）
        row.setOrigin(SkillAsset.ORIGIN_BUILTIN);
        row.setPackageKey(key);
        row.setPackageSha256(sha);
        row.setFileCount(pkg.getFiles().size());
        row.setValid(CommonConst.YES);
        // 启动线程无登录上下文，create_by 不手写：FieldFill 填不上就是空，那是正常态不是缺陷
        skillAssetService.saveAsset(row);
        log.info("系统出厂技能包已注册: name={} v{} files={} sha={}",
                pkg.getName(), BUILTIN_VERSION, pkg.getFiles().size(), sha);
    }

    /** 内容变了：就地覆盖同一个 key、同一个版本；status 一字不动（管理员的"下线"必须活下来） */
    private void refresh(SkillPackage pkg, byte[] zip, String sha, SkillAsset existing) {
        String key = SystemSkillStore.packageKey(pkg.getName(), BUILTIN_VERSION);
        store.put(key, zip);
        SkillAsset patch = new SkillAsset();
        patch.setId(existing.getId());
        patch.setTitle(pkg.getTitle());
        patch.setDescription(pkg.getDescription());
        patch.setCategory(pkg.getCategory());
        patch.setVersion(BUILTIN_VERSION);
        patch.setPackageKey(key);
        patch.setPackageSha256(sha);
        patch.setFileCount(pkg.getFiles().size());
        patch.setValid(CommonConst.YES);
        // 这里不 set status/origin：空值不进 SET，patch 里没有这一列＝数据库里的原值保持不动
        skillAssetService.updateAsset(patch);
        log.info("系统出厂技能包内容更新: name={} v{} 就地覆盖（状态保持 {}，版本不涨）",
                pkg.getName(), BUILTIN_VERSION, existing.getStatus());
    }
}
