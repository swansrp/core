package com.bidr.agent.console.skill;

import com.bidr.agent.console.dao.entity.SkillAsset;
import com.bidr.agent.console.dao.repository.SkillAssetService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Title: SystemSkillSeedServiceTest
 * Description: 系统级出厂包启动对账单测（M14 棒 4-C）。对账分支喂<b>合成包</b>
 * （{@link FixedSeed} 覆写包源发现），与真实出厂包增减解耦；另有一条用真实 classpath
 * 锁"jar 内确实发现了发版内容"。逐条钉住五条裁定：
 * <ul>
 *   <li>jar 内 {@code agent-skills/} 的出厂包被发现并登记（真实 resources）；</li>
 *   <li>首次入库＝建 PUBLISHED 行、origin=BUILTIN、v1、key {@code agent-skill/<name>/v1.zip}；</li>
 *   <li>二次启动 sha 相同＝零写（不涨版本、不碰状态、不重传对象）；</li>
 *   <li>同名但非出厂（origin=UPLOADED）＝让位跳过，运行期数据不是代码的附属物；</li>
 *   <li>TOMBSTONE＝不复活（注销是人的决策）；</li>
 *   <li>sha 变＝就地覆盖同 key、同 v1，status 一字不动（管理员的"下线"活下来）；</li>
 *   <li>不合契约的包＝跳过且不碰存储。</li>
 * </ul>
 * 另补棒 4 验收项「上传包在重启对账后仍在（不被当野包清理）」的负向腿：
 * 对账器只按包名点查、从不反扫表（结构上就无从"清理"）；origin 为 NULL 的历史行同样让位；
 * 一轮里一个包让位不阻断其余包；墓碑行即便 sha 恰好相同也不记成"未变"；
 * jar 内没有包＝整轮零写。指纹口径一条：确定性打包产物的 sha 稳定且为 64 位小写十六进制
 * （业务侧 {@code Digests} 与本实现的等价性由业务侧测试守，见 BuiltinSkillSeedServiceTest）。
 *
 * @author Sharp
 * @since 2026-10-13
 */
class SystemSkillSeedServiceTest {

    private static final String SYNTHETIC = "demo-skill";
    private static final String SYNTHETIC_KEY = "agent-skill/" + SYNTHETIC + "/v1.zip";
    private static final String FACTORY_PACK = "skill-authoring";
    private static final String FACTORY_KEY = "agent-skill/" + FACTORY_PACK + "/v1.zip";
    private static final String OTHER_SHA = "0000000000000000000000000000000000000000000000000000000000000000";

    private final SkillAssetService skillAssetService = mock(SkillAssetService.class);
    private final SystemSkillStore store = mock(SystemSkillStore.class);
    /** 真实包源（扫 classpath）——只给"jar 内容被发现"那条用 */
    private final SystemSkillSeedService service = new SystemSkillSeedService(skillAssetService, store);

    // ---------------------------------------------------------------- 真实包源

    @Test
    void realJarPackIsDiscoveredAndRegistered() {
        stubLookup(null);

        Map<String, byte[]> packs = service.discoverPacks();
        SystemSkillSeedService.Outcome outcome = service.seed();

        // 新增/删除出厂包＝往 agent-console 的 resources/agent-skills 丢/删目录，本断言随之更新
        assertThat(packs).as("jar 内出厂包目录应被扫到: %s", packs.keySet()).containsKey(FACTORY_PACK);
        assertThat(outcome.added).as("真实出厂包全部登记: %s", outcome).isEqualTo(1);
        assertThat(outcome.skipped).as("发版内容必须自带合契约的包: %s", outcome).isZero();
        SkillAsset row = capturedSaved().get(0);
        assertThat(row.getSkillName()).isEqualTo(FACTORY_PACK);
        assertThat(row.getOrigin()).isEqualTo(SkillAsset.ORIGIN_BUILTIN);
        assertThat(row.getStatus()).isEqualTo(SkillAsset.STATUS_PUBLISHED);
        assertThat(row.getVersion()).isEqualTo(SystemSkillSeedService.BUILTIN_VERSION);
        assertThat(row.getPackageKey()).isEqualTo(FACTORY_KEY);
        assertThat(row.getFileCount()).as("文件数取自包内真实条目数，不能是 0 或 null").isGreaterThan(0);
        assertThat(row.getPackageSha256()).isEqualTo(SkillPackageWriter.sha256Hex(packs.get(FACTORY_PACK)));
        verify(store).put(eq(FACTORY_KEY), any());
    }

    // ---------------------------------------------------------------- seed：首建

    @Test
    void firstRunRegistersPackAsPublishedBuiltinVersionOne() {
        stubLookup(null);

        SystemSkillSeedService.Outcome outcome = fixedSeed().seed();

        assertThat(outcome.added).as("%s", outcome).isEqualTo(1);
        SkillAsset registered = onlySaved();
        assertThat(registered.getSkillName()).isEqualTo(SYNTHETIC);
        assertThat(registered.getTitle()).isEqualTo("演示");
        assertThat(registered.getDescription()).isEqualTo("合成测试包");
        assertThat(registered.getCategory()).isEqualTo("通用");
        assertThat(registered.getVersion()).isEqualTo(SystemSkillSeedService.BUILTIN_VERSION);
        assertThat(registered.getStatus()).isEqualTo(SkillAsset.STATUS_PUBLISHED);
        assertThat(registered.getOrigin()).isEqualTo(SkillAsset.ORIGIN_BUILTIN);
        assertThat(registered.getPackageKey()).isEqualTo(SYNTHETIC_KEY);
        assertThat(registered.getValid()).isEqualTo("1");
        assertThat(registered.getFileCount()).isEqualTo(2);
        assertThat(registered.getPackageSha256()).isEqualTo(SkillPackageWriter.sha256Hex(syntheticZip()));
        verify(store).put(eq(SYNTHETIC_KEY), any());
    }

    // ---------------------------------------------------------------- seed：幂等

    @Test
    void secondRunWithSameShaWritesNothing() {
        stubLookup(null);
        fixedSeed().seed();
        SkillAsset persisted = onlySaved();

        reset(skillAssetService, store); // 清验数，只盯第二轮
        stubLookup(persisted);

        SystemSkillSeedService.Outcome outcome = fixedSeed().seed();

        assertThat(outcome.unchanged).as("sha 未变＝零写: %s", outcome).isEqualTo(1);
        assertThat(outcome.added).isZero();
        assertThat(outcome.refreshed).isZero();
        verify(skillAssetService, never()).saveAsset(any(SkillAsset.class));
        verify(skillAssetService, never()).updateAsset(any(SkillAsset.class));
        verify(store, never()).put(anyString(), any());
    }

    // ---------------------------------------------------------------- seed：让位（UPLOADED 是运行期数据）

    @Test
    void sameNameOwnedByUploadIsSkippedNeverClobbered() {
        SkillAsset uploaded = asset(SkillAsset.ORIGIN_UPLOADED, SkillAsset.STATUS_UNPUBLISHED);
        uploaded.setPackageSha256(OTHER_SHA);
        stubLookup(uploaded);

        SystemSkillSeedService.Outcome outcome = fixedSeed().seed();

        assertThat(outcome.skipped).as("非出厂同名让位: %s", outcome).isEqualTo(1);
        assertThat(outcome.refreshed).isZero();
        assertThat(outcome.notes).anyMatch(n -> n.contains(SYNTHETIC) && n.contains("让位"));
        verify(skillAssetService, never()).saveAsset(any(SkillAsset.class));
        verify(skillAssetService, never()).updateAsset(any(SkillAsset.class));
        verify(store, never()).put(anyString(), any());
    }

    // ---------------------------------------------------------------- seed：让位（origin 为 NULL 的历史行同样让位）

    @Test
    void sameNameWithNullOriginIsSkippedNeverClobbered() {
        SkillAsset legacy = asset(null, SkillAsset.STATUS_PUBLISHED);
        legacy.setPackageSha256(OTHER_SHA);
        stubLookup(legacy);

        SystemSkillSeedService.Outcome outcome = fixedSeed().seed();

        // 钉住的风险：判据写成 existing.getOrigin().equals(...) 方向反了会 NPE，
        // 加了 isNotBlank 之类前置又会把"来源不明的行"当成出厂包覆盖掉——NULL 同样不是 BUILTIN，必须让位
        assertThat(outcome.skipped).as("来源不明的同名行让位: %s", outcome).isEqualTo(1);
        assertThat(outcome.refreshed).as("NULL origin 不得走就地覆盖").isZero();
        assertThat(outcome.notes).anyMatch(n -> n.contains(SYNTHETIC) && n.contains("让位"));
        verify(skillAssetService, never()).saveAsset(any(SkillAsset.class));
        verify(skillAssetService, never()).updateAsset(any(SkillAsset.class));
        verify(store, never()).put(anyString(), any());
    }

    // ---------------------------------------------------------------- seed：不复活

    @Test
    void tombstonedBuiltinIsNeverReinstalled() {
        SkillAsset tombstone = asset(SkillAsset.ORIGIN_BUILTIN, SkillAsset.STATUS_TOMBSTONE);
        tombstone.setPackageSha256(OTHER_SHA);
        stubLookup(tombstone);

        SystemSkillSeedService.Outcome outcome = fixedSeed().seed();

        assertThat(outcome.skipped).as("注销是人的决策: %s", outcome).isEqualTo(1);
        assertThat(outcome.refreshed).as("墓碑行不得走就地覆盖").isZero();
        assertThat(outcome.notes).anyMatch(n -> n.contains(SYNTHETIC) && n.contains("不复活"));
        verify(skillAssetService, never()).saveAsset(any(SkillAsset.class));
        verify(skillAssetService, never()).updateAsset(any(SkillAsset.class));
        verify(store, never()).put(anyString(), any());
    }

    @Test
    void tombstonedRowWithMatchingShaIsSkippedNotCountedUnchanged() {
        // 墓碑行的 sha 恰好等于合成包当前指纹——"内容也没变"最容易把分支顺序错误暴露成"未变"
        SkillAsset tombstone = asset(SkillAsset.ORIGIN_BUILTIN, SkillAsset.STATUS_TOMBSTONE);
        tombstone.setPackageSha256(SkillPackageWriter.sha256Hex(syntheticZip()));
        stubLookup(tombstone);

        SystemSkillSeedService.Outcome outcome = fixedSeed().seed();

        // 钉住的风险：分支顺序若被换成"先比 sha"，墓碑行会被记成未变，
        // 将来发布动作查状态时会误判这个包还活着
        assertThat(outcome.skipped).as("墓碑优先于 sha 比对: %s", outcome).isEqualTo(1);
        assertThat(outcome.unchanged).as("TOMBSTONE∧sha 相同不得走未变分支").isZero();
        assertThat(outcome.notes).anyMatch(n -> n.contains(SYNTHETIC) && n.contains("不复活"));
        verify(skillAssetService, never()).saveAsset(any(SkillAsset.class));
        verify(skillAssetService, never()).updateAsset(any(SkillAsset.class));
        verify(store, never()).put(anyString(), any());
    }

    // ---------------------------------------------------------------- seed：内容更新

    @Test
    void changedShaOverwritesInPlaceWithoutBumpingVersionOrTouchingStatus() {
        SkillAsset offline = asset(SkillAsset.ORIGIN_BUILTIN, SkillAsset.STATUS_OFFLINE);
        offline.setId(9L);
        offline.setPackageSha256(OTHER_SHA);
        stubLookup(offline);

        SystemSkillSeedService.Outcome outcome = fixedSeed().seed();

        assertThat(outcome.refreshed).as("sha 变＝就地覆盖: %s", outcome).isEqualTo(1);
        ArgumentCaptor<SkillAsset> patch = ArgumentCaptor.forClass(SkillAsset.class);
        verify(skillAssetService).updateAsset(patch.capture());
        SkillAsset written = patch.getValue();
        assertThat(written.getId()).isEqualTo(9L);
        assertThat(written.getVersion()).as("出厂包恒 v1，重启不涨版本")
                .isEqualTo(SystemSkillSeedService.BUILTIN_VERSION);
        assertThat(written.getStatus())
                .as("status 不在这张补丁里＝一字没动（管理员的下线决策必须活下来）").isNull();
        assertThat(offline.getStatus()).as("离线不会被重启改成 PUBLISHED")
                .isEqualTo(SkillAsset.STATUS_OFFLINE);
        assertThat(written.getValid()).isEqualTo("1");
        assertThat(written.getPackageKey()).isEqualTo(SYNTHETIC_KEY);
        assertThat(written.getPackageSha256()).isEqualTo(SkillPackageWriter.sha256Hex(syntheticZip()));
        verify(store).put(eq(SYNTHETIC_KEY), any());
        verify(skillAssetService, never()).saveAsset(any(SkillAsset.class));
    }

    @Test
    void softDeletedBuiltinRowIsRestoredInPlaceByDesign() {
        SkillAsset softDeleted = asset(SkillAsset.ORIGIN_BUILTIN, SkillAsset.STATUS_PUBLISHED);
        softDeleted.setId(7L);
        softDeleted.setValid("0");
        softDeleted.setPackageSha256(SkillPackageWriter.sha256Hex(syntheticZip()));
        stubLookup(softDeleted);

        SystemSkillSeedService.Outcome outcome = fixedSeed().seed();

        // 这是当前裁定（棒 4-D 复核项）：jar 是出厂包存在性的权威，
        // 软删不进出厂包的"未变"分支（valid≠1），而是就地覆盖并把 valid 置回 1。
        // 撤销出厂包的正确表达是 TOMBSTONE 而不是软删——若将来改判"软删也保留"，改这条用例。
        assertThat(outcome.refreshed).as("软删的出厂行走就地覆盖: %s", outcome).isEqualTo(1);
        assertThat(outcome.unchanged).as("valid=0 不得被记成未变").isZero();
        ArgumentCaptor<SkillAsset> patch = ArgumentCaptor.forClass(SkillAsset.class);
        verify(skillAssetService).updateAsset(patch.capture());
        SkillAsset written = patch.getValue();
        assertThat(written.getId()).isEqualTo(7L);
        assertThat(written.getValid()).as("当前设计：refresh 把 valid 复活回 1").isEqualTo("1");
        assertThat(written.getStatus()).as("patch 不带 status＝库里原状态不动").isNull();
        verify(store).put(eq(SYNTHETIC_KEY), any());
        verify(skillAssetService, never()).saveAsset(any(SkillAsset.class));
    }

    // ---------------------------------------------------------------- seed：契约自检

    @Test
    void packViolatingContractIsSkippedAndStorageUntouched() {
        stubLookup(null);

        SystemSkillSeedService.Outcome outcome = new FixedSeed(badFrontmatterZip()).seed();

        assertThat(outcome.skipped).as("坏包不入库: %s", outcome).isEqualTo(1);
        assertThat(outcome.added).isZero();
        assertThat(outcome.notes).anyMatch(n -> n.contains("契约自检失败"));
        verify(store, never()).put(anyString(), any());
        verify(skillAssetService, never()).saveAsset(any(SkillAsset.class));
        verify(skillAssetService, never()).updateAsset(any(SkillAsset.class));
    }

    // ---------------------------------------------------------------- seed：从不反扫表（"上传包不被当野包清理"的结构性论证）

    @Test
    void seederOnlyLooksUpByPackNameAndNeverSweepsTheTable() {
        stubLookup(null);

        SystemSkillSeedService.Outcome outcome = fixedSeed().seed();

        assertThat(outcome.added).as("%s", outcome).isEqualTo(1);
        // 全部交互只有 findByName（且只被以包名调过一次）＋ saveAsset 两种
        verify(skillAssetService).findByName(SYNTHETIC);
        verify(skillAssetService).saveAsset(any(SkillAsset.class));
        verifyNoMoreInteractions(skillAssetService);
        // 显式钉住：反扫与清理的口一个都没开（verifyNoMoreInteractions 已兜底，这里点名是防将来"合法化"某个新调用）
        verify(skillAssetService, never()).listPublished();
        verify(skillAssetService, never()).listByOrigin(anyString());
        verify(skillAssetService, never()).removeById(anyLong());
        verify(skillAssetService, never()).removeByIds(any());
        verify(skillAssetService, never()).remove(any());
        // 存储侧除 put 外没有别的动作。注：SystemSkillStore 本身只有 put/get/readUrl，
        // 没有删除对象的口——"清理"这件事对账器根本无从下手
        verify(store).put(eq(SYNTHETIC_KEY), any());
        verifyNoMoreInteractions(store);
        verify(store, never()).get(anyString());
        verify(store, never()).readUrl(anyString(), anyString());
    }

    // ---------------------------------------------------------------- seed：一包让位不阻断同轮其余包

    @Test
    void onePackYieldingDoesNotBlockSiblingsInSameRound() {
        String yieldName = "seed-yield";
        String siblingName = "seed-sibling";
        SkillAsset uploaded = asset(yieldName, SkillAsset.ORIGIN_UPLOADED, SkillAsset.STATUS_UNPUBLISHED);
        uploaded.setPackageSha256(OTHER_SHA);
        when(skillAssetService.findByName(yieldName)).thenReturn(uploaded);
        when(skillAssetService.findByName(siblingName)).thenReturn(null);
        Map<String, byte[]> packs = new LinkedHashMap<>();
        packs.put(yieldName, syntheticZipNamed(yieldName));
        packs.put(siblingName, syntheticZipNamed(siblingName));

        SystemSkillSeedService.Outcome outcome = new FixedSeed(packs).seed();

        assertThat(outcome.skipped).as("A 让位: %s", outcome).isEqualTo(1);
        assertThat(outcome.added).as("B 照常注册，不被 A 阻断: %s", outcome).isEqualTo(1);
        SkillAsset registered = onlySaved();
        assertThat(registered.getSkillName()).isEqualTo(siblingName);
        assertThat(registered.getOrigin()).isEqualTo(SkillAsset.ORIGIN_BUILTIN);
        // A 的行零写：不 save、不 update、存储里也不落 A 的 key
        verify(skillAssetService, never()).updateAsset(any(SkillAsset.class));
        verify(store, never()).put(eq("agent-skill/" + yieldName + "/v1.zip"), any());
    }

    // ---------------------------------------------------------------- seed：jar 内没有包＝整轮零写

    @Test
    void emptyJarMeansWhollyZeroSideEffects() {
        // 钉住的风险：专有化部署把 agent-skills/ 资源摘掉后，启动对账不得对表有任何副作用
        SystemSkillSeedService.Outcome outcome = new FixedSeed(Collections.emptyMap()).seed();

        assertThat(outcome.added).as("%s", outcome).isZero();
        assertThat(outcome.refreshed).isZero();
        assertThat(outcome.unchanged).isZero();
        assertThat(outcome.skipped).isZero();
        assertThat(outcome.notes).isEmpty();
        verifyNoInteractions(skillAssetService, store);
    }

    // ---------------------------------------------------------------- 指纹口径

    @Test
    void packFingerprintIsStableLowercaseHex64() {
        String first = SkillPackageWriter.sha256Hex(syntheticZip());
        String second = SkillPackageWriter.sha256Hex(syntheticZip());

        assertThat(first).as("同内容必得同指纹，否则重启对账会把没改的认成改了").isEqualTo(second);
        assertThat(first).as("sha256 输出口径：64 位小写十六进制").matches("^[0-9a-f]{64}$");
        assertThat(SkillPackageWriter.sha256Hex("x".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("2d711642b726b04401627ca9fbac32f5c8530fb1903cc4db02258717921a4881");
    }

    // ---------------------------------------------------------------- 夹具

    /** 合成包源：分支用例只喂一个包，桩行与包名一一对应，不受真实出厂包增减影响 */
    private final class FixedSeed extends SystemSkillSeedService {

        private final Map<String, byte[]> packs;

        FixedSeed(Map<String, byte[]> packs) {
            super(skillAssetService, store);
            this.packs = packs;
        }

        FixedSeed(String name, byte[] zip) {
            this(Collections.singletonMap(name, zip));
        }

        FixedSeed(byte[] zip) {
            this(SYNTHETIC, zip);
        }

        @Override
        Map<String, byte[]> discoverPacks() {
            return packs;
        }
    }

    private FixedSeed fixedSeed() {
        return new FixedSeed(SYNTHETIC, syntheticZip());
    }

    private static Map<String, byte[]> syntheticFiles(String name) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put(SkillPackageParser.SKILL_MD, ("---\nname: " + name
                + "\ndescription: 合成测试包\ntitle: 演示\ncategory: 通用\n---\n# 正文\n")
                .getBytes(StandardCharsets.UTF_8));
        files.put("templates/t.md", "模板".getBytes(StandardCharsets.UTF_8));
        return files;
    }

    private static byte[] syntheticZip() {
        return SkillPackageWriter.pack(SYNTHETIC, syntheticFiles(SYNTHETIC));
    }

    /** 换名的合成包（多包用例要包名与 frontmatter name 一致，契约才过） */
    private static byte[] syntheticZipNamed(String name) {
        return SkillPackageWriter.pack(name, syntheticFiles(name));
    }

    /** 缺 frontmatter 的包：过不了上传闸门那把尺，也就该被对账器一样拒掉 */
    private static byte[] badFrontmatterZip() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put(SkillPackageParser.SKILL_MD, "# 没有 frontmatter\n".getBytes(StandardCharsets.UTF_8));
        return SkillPackageWriter.pack(SYNTHETIC, files);
    }

    private static SkillAsset asset(String origin, String status) {
        SkillAsset row = new SkillAsset();
        row.setSkillName(SYNTHETIC);
        row.setVersion(1);
        row.setOrigin(origin);
        row.setStatus(status);
        row.setValid("1");
        return row;
    }

    /** 多包用例：桩行的包名要与喂进去的合成包名一一对应 */
    private static SkillAsset asset(String name, String origin, String status) {
        SkillAsset row = asset(origin, status);
        row.setSkillName(name);
        return row;
    }

    private List<SkillAsset> capturedSaved() {
        ArgumentCaptor<SkillAsset> saved = ArgumentCaptor.forClass(SkillAsset.class);
        verify(skillAssetService, atLeastOnce()).saveAsset(saved.capture());
        return saved.getAllValues();
    }

    private SkillAsset onlySaved() {
        List<SkillAsset> all = capturedSaved();
        assertThat(all).hasSize(1);
        return all.get(0);
    }

    private void stubLookup(SkillAsset row) {
        when(skillAssetService.findByName(anyString())).thenReturn(row);
    }
}
