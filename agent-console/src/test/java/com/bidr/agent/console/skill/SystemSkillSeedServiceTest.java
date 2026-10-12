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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
 * 另钉指纹口径一条：确定性打包产物的 sha 稳定且为 64 位小写十六进制
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

        FixedSeed(String name, byte[] zip) {
            super(skillAssetService, store);
            this.packs = Collections.singletonMap(name, zip);
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
