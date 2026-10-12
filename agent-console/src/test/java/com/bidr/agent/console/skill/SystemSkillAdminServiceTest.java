package com.bidr.agent.console.skill;

import com.bidr.agent.console.dao.entity.SkillAsset;
import com.bidr.agent.console.dao.repository.SkillAssetService;
import com.bidr.kernel.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Title: SystemSkillAdminServiceTest
 * Description: 系统级技能包写面单测（M14 棒 4-E：上传/发布/下线/墓碑 + 管理员闸门）。
 * 分支用例喂<b>合成包</b>（{@link #syntheticZip}，与 SystemSkillSeedServiceTest 同法自建），
 * 闸门用 {@link OpenGate} 覆写 protected {@code isPlatformAdmin()} 放行。逐条钉住裁定：
 * <ul>
 *   <li>首传＝v1、origin=UPLOADED、status=UNPUBLISHED（上传 ≠ 可见）、key {@code agent-skill/<名>/v1.zip}、
 *       fileCount 取包内真实条目数、valid=1、createBy 不手写（留 null 给 FieldFill.INSERT），
 *       且<b>先存对象再落行</b>（反过来会留下"账上有地址、货不在"的坏行）；</li>
 *   <li>落库字节必须是确定性重打包产物：非归一入参 zip（带目录条目+乱序）与 store 收到的字节不相等，
 *       sha 按归一化字节算——这是设计不是缺陷；</li>
 *   <li>同名再传＝新版本新行（save 不 update、key 递增到 v2.zip）；</li>
 *   <li>同名撞 BUILTIN＝拒绝（出厂包名是保留名），且<b>先查后写</b>：存储与表零写；</li>
 *   <li>坏包（缺 frontmatter / 非 zip 字节）＝契约尺拦下，存储与表零写；</li>
 *   <li><b>六种拒绝一律 {@link ServiceException}</b>：框架把 NoticeException 渲染成 {@code code=0}＋
 *       "SUCCESS"，拒绝若走 notice 就等于对调用方报成功（真机探针 10-13 就是这么抓出第一版写法的）；
 *       契约尺抛的 notice 在 {@code parseAsRejection} 边界翻译一次，业务侧上传端点零漂移。</li>
 *   <li>墓碑 patch 只带 id+status：valid/origin/sha 全为 null（ignoreNull 语义＝不进 SET 不动列），存储零动作；</li>
 *   <li>状态机：UNPUBLISHED/OFFLINE→publish 放行、PUBLISHED→offline 放行、TOMBSTONE→publish 拒绝、
 *       UNPUBLISHED→offline 拒绝；</li>
 *   <li>目标态已达成＝幂等成功不写库（publish/offline/tombstone 三态各钉一条）；</li>
 *   <li>行不存在＝三动作各抛一次且文案带 id；</li>
 *   <li>台账列表复用 {@code listByOrigin(null)}（不筛 origin）；</li>
 *   <li>闸门 fail closed：不覆写的真实实例 {@code isPlatformAdmin()} 返回 false，
 *       调 upload 拒绝且 {@code verifyNoInteractions} 存储与表。</li>
 * </ul>
 *
 * @author Sharp
 * @since 2026-10-13
 */
class SystemSkillAdminServiceTest {

    private static final String SYNTHETIC = "demo-skill";
    private static final String SYNTHETIC_KEY = "agent-skill/" + SYNTHETIC + "/v1.zip";

    private final SkillAssetService skillAssetService = mock(SkillAssetService.class);
    private final SystemSkillStore store = mock(SystemSkillStore.class);

    // ---------------------------------------------------------------- 上传：首传

    @Test
    void firstUploadLandsVersionOneAsUnpublishedUploadedRow() {
        stubLookup(null);

        SkillAsset row = openGate().upload(syntheticZip());

        assertThat(row.getSkillName()).isEqualTo(SYNTHETIC);
        assertThat(row.getTitle()).isEqualTo("演示");
        assertThat(row.getDescription()).isEqualTo("合成测试包");
        assertThat(row.getCategory()).isEqualTo("通用");
        assertThat(row.getVersion()).as("首传＝v1").isEqualTo(1);
        assertThat(row.getOrigin()).as("上传的行来源一律 UPLOADED").isEqualTo(SkillAsset.ORIGIN_UPLOADED);
        assertThat(row.getStatus()).as("上传 ≠ 可见：必须等显式发布").isEqualTo(SkillAsset.STATUS_UNPUBLISHED);
        assertThat(row.getPackageKey()).isEqualTo(SYNTHETIC_KEY);
        assertThat(row.getFileCount()).as("文件数取自包内真实条目数，不能是 0 或 null")
                .isEqualTo(syntheticFiles(SYNTHETIC).size());
        assertThat(row.getValid()).isEqualTo("1");
        assertThat(row.getPackageSha256()).isEqualTo(SkillPackageWriter.sha256Hex(syntheticZip()));
        assertThat(row.getCreateBy()).as("createBy 不手写：登录线程由 FieldFill.INSERT 填").isNull();
        verify(store).put(eq(SYNTHETIC_KEY), any());
        verify(skillAssetService).saveAsset(row);
        InOrder order = inOrder(store, skillAssetService);
        order.verify(store).put(eq(SYNTHETIC_KEY), any());
        order.verify(skillAssetService).saveAsset(row);
    }

    // ---------------------------------------------------------------- 上传：确定性重打包

    @Test
    void storedBytesAreDeterministicRepackNotTheUploadedBytes() {
        stubLookup(null);
        byte[] nonNormalizedZip = nonNormalizedZip(); // 多带目录条目 + 条目顺序倒过来
        assertThat(nonNormalizedZip).as("夹具自检：入参 zip 本身就不等于归一化产物")
                .isNotEqualTo(syntheticZip());

        SkillAsset row = openGate().upload(nonNormalizedZip);

        ArgumentCaptor<byte[]> stored = ArgumentCaptor.forClass(byte[].class);
        verify(store).put(eq(SYNTHETIC_KEY), stored.capture());
        assertThat(stored.getValue()).as("落库字节 ≠ 入参字节（归一化是设计不是缺陷）").isNotEqualTo(nonNormalizedZip);
        assertThat(stored.getValue()).as("落库字节＝确定性重打包产物").isEqualTo(syntheticZip());
        assertThat(row.getPackageSha256()).as("sha 必须按归一化字节算，否则带不带目录条目会算出两个指纹")
                .isEqualTo(SkillPackageWriter.sha256Hex(stored.getValue()));
    }

    // ---------------------------------------------------------------- 上传：同名再传＝新版本新行

    @Test
    void sameNameReuploadCreatesVersionTwoRowWithoutUpdatingOldOne() {
        SkillAsset previous = asset(SkillAsset.ORIGIN_UPLOADED, SkillAsset.STATUS_PUBLISHED);
        previous.setVersion(1);
        stubLookup(previous);

        SkillAsset row = openGate().upload(syntheticZip());

        assertThat(row.getVersion()).as("同名再传＝v2").isEqualTo(2);
        assertThat(row.getPackageKey()).as("历史版本同规则递增留档").isEqualTo("agent-skill/" + SYNTHETIC + "/v2.zip");
        verify(store).put(eq("agent-skill/" + SYNTHETIC + "/v2.zip"), any());
        verify(skillAssetService).saveAsset(any(SkillAsset.class));
        verify(skillAssetService, never()).updateAsset(any(SkillAsset.class));
    }

    // ---------------------------------------------------------------- 上传：撞出厂名

    @Test
    void uploadClobberingBuiltinNameIsRejectedBeforeAnyWrite() {
        stubLookup(asset(SkillAsset.ORIGIN_BUILTIN, SkillAsset.STATUS_PUBLISHED));

        assertThatThrownBy(() -> openGate().upload(syntheticZip()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(SYNTHETIC);

        verify(store, never()).put(anyString(), any());
        verify(skillAssetService, never()).saveAsset(any(SkillAsset.class));
    }

    // ---------------------------------------------------------------- 上传：契约尺

    @Test
    void badPackagesAreRejectedWithZeroSideEffects() {
        assertThatThrownBy(() -> openGate().upload(badFrontmatterZip()))
                .as("缺 frontmatter 的包过不了契约尺")
                .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> openGate().upload("这不是 zip 的字节".getBytes(StandardCharsets.UTF_8)))
                .as("非 zip 字节同样抛出")
                .isInstanceOf(ServiceException.class);

        verifyNoInteractions(store, skillAssetService);
    }

    // ---------------------------------------------------------------- 墓碑：只写 status 一列

    @Test
    void tombstonePatchCarriesOnlyIdAndStatusAndLeavesStorageAlone() {
        SkillAsset builtin = asset(SkillAsset.ORIGIN_BUILTIN, SkillAsset.STATUS_PUBLISHED);
        builtin.setId(5L);
        builtin.setPackageSha256(SkillPackageWriter.sha256Hex(syntheticZip()));
        stubGetById(5L, builtin);

        openGate().tombstone(5L);

        ArgumentCaptor<SkillAsset> patch = ArgumentCaptor.forClass(SkillAsset.class);
        verify(skillAssetService).updateAsset(patch.capture());
        SkillAsset written = patch.getValue();
        assertThat(written.getId()).isEqualTo(5L);
        assertThat(written.getStatus()).isEqualTo(SkillAsset.STATUS_TOMBSTONE);
        assertThat(written.getValid()).as("patch 不带 valid＝不进 SET（墓碑不是软删）").isNull();
        assertThat(written.getOrigin()).as("所有权不是状态，三种转换都不写它").isNull();
        assertThat(written.getPackageSha256()).as("指纹留在原列供回滚对账").isNull();
        verifyNoInteractions(store);
    }

    // ---------------------------------------------------------------- 状态机

    @Test
    void publishAcceptsUnpublishedAndOfflineButRejectsTombstone() {
        SkillAsset unpublished = asset(SkillAsset.ORIGIN_UPLOADED, SkillAsset.STATUS_UNPUBLISHED);
        stubGetById(1L, unpublished);
        openGate().publish(1L);
        assertThat(statusPatch(1L).getStatus()).as("UNPUBLISHED→publish 放行").isEqualTo(SkillAsset.STATUS_PUBLISHED);

        SkillAsset offline = asset(SkillAsset.ORIGIN_UPLOADED, SkillAsset.STATUS_OFFLINE);
        stubGetById(2L, offline);
        openGate().publish(2L);
        assertThat(statusPatch(2L).getStatus()).as("OFFLINE→publish 重新发布放行").isEqualTo(SkillAsset.STATUS_PUBLISHED);

        SkillAsset tombstoned = asset(SkillAsset.ORIGIN_UPLOADED, SkillAsset.STATUS_TOMBSTONE);
        stubGetById(3L, tombstoned);
        assertThatThrownBy(() -> openGate().publish(3L))
                .as("注销不可逆")
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("3");
    }

    @Test
    void offlineAcceptsPublishedButRejectsUnpublished() {
        SkillAsset published = asset(SkillAsset.ORIGIN_UPLOADED, SkillAsset.STATUS_PUBLISHED);
        stubGetById(1L, published);
        openGate().offline(1L);
        assertThat(statusPatch(1L).getStatus()).as("PUBLISHED→offline 放行").isEqualTo(SkillAsset.STATUS_OFFLINE);

        SkillAsset unpublished = asset(SkillAsset.ORIGIN_UPLOADED, SkillAsset.STATUS_UNPUBLISHED);
        stubGetById(2L, unpublished);
        assertThatThrownBy(() -> openGate().offline(2L))
                .as("尚未发布，无需下线")
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("2");
    }

    // ---------------------------------------------------------------- 幂等

    @Test
    void alreadyInTargetStateSucceedsWithoutWriting() {
        stubGetById(1L, asset(SkillAsset.ORIGIN_UPLOADED, SkillAsset.STATUS_PUBLISHED));
        openGate().publish(1L);

        stubGetById(2L, asset(SkillAsset.ORIGIN_UPLOADED, SkillAsset.STATUS_OFFLINE));
        openGate().offline(2L);

        stubGetById(3L, asset(SkillAsset.ORIGIN_UPLOADED, SkillAsset.STATUS_TOMBSTONE));
        openGate().tombstone(3L);

        verify(skillAssetService, never()).updateAsset(any(SkillAsset.class));
        verifyNoInteractions(store);
    }

    // ---------------------------------------------------------------- 行不存在

    @Test
    void missingRowIsReportedWithItsIdByEveryAction() {
        // getById 未桩＝返回 null，正是"行不存在"
        assertThatThrownBy(() -> openGate().publish(42L))
                .isInstanceOf(ServiceException.class).hasMessageContaining("42");
        assertThatThrownBy(() -> openGate().offline(42L))
                .isInstanceOf(ServiceException.class).hasMessageContaining("42");
        assertThatThrownBy(() -> openGate().tombstone(42L))
                .isInstanceOf(ServiceException.class).hasMessageContaining("42");

        verify(skillAssetService, never()).updateAsset(any(SkillAsset.class));
    }

    // ---------------------------------------------------------------- 台账列表

    @Test
    void listAssetsQueriesWholeLedgerWithoutOriginFilter() {
        List<SkillAsset> ledger = Collections.singletonList(asset(SkillAsset.ORIGIN_BUILTIN, SkillAsset.STATUS_PUBLISHED));
        when(skillAssetService.listByOrigin(null)).thenReturn(ledger);

        List<SkillAsset> result = openGate().listAssets();

        assertThat(result).as("管理页看全档：含未发布/下线/墓碑").isEqualTo(ledger);
        verify(skillAssetService).listByOrigin(null);
    }

    // ---------------------------------------------------------------- 闸门 fail closed

    @Test
    void realGateIsFalseAndBlocksUploadWithoutTouchingAnything() {
        SystemSkillAdminService gated = new SystemSkillAdminService(skillAssetService, store);
        assertThat(gated.isPlatformAdmin()).as("无 Spring 上下文取不到 permitService＝按非管理员").isFalse();

        assertThatThrownBy(() -> gated.upload(syntheticZip()))
                .isInstanceOf(ServiceException.class);

        verifyNoInteractions(store, skillAssetService);
    }

    // ---------------------------------------------------------------- 夹具

    /** 放行闸门：只覆写 isPlatformAdmin，其余逻辑走真实实现（同 FixedSeed 桩法） */
    private final class OpenGate extends SystemSkillAdminService {

        OpenGate() {
            super(skillAssetService, store);
        }

        @Override
        protected boolean isPlatformAdmin() {
            return true;
        }
    }

    private OpenGate openGate() {
        return new OpenGate();
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

    /** 缺 frontmatter 的包：过不了上传的契约尺 */
    private static byte[] badFrontmatterZip() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put(SkillPackageParser.SKILL_MD, "# 没有 frontmatter\n".getBytes(StandardCharsets.UTF_8));
        return SkillPackageWriter.pack(SYNTHETIC, files);
    }

    /** 非归一 zip：多带目录条目 + 条目顺序倒过来（SKILL.md 在后），解析契约能过、字节与归一化产物不等 */
    private static byte[] nonNormalizedZip() {
        Map<String, byte[]> files = syntheticFiles(SYNTHETIC);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bos, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry(SYNTHETIC + "/templates/"));
            zip.closeEntry();
            putDeflated(zip, SYNTHETIC + "/templates/t.md", files.get("templates/t.md"));
            zip.putNextEntry(new ZipEntry(SYNTHETIC + "/"));
            zip.closeEntry();
            putDeflated(zip, SYNTHETIC + "/" + SkillPackageParser.SKILL_MD,
                    files.get(SkillPackageParser.SKILL_MD));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bos.toByteArray();
    }

    private static void putDeflated(ZipOutputStream zip, String name, byte[] data) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setMethod(ZipEntry.DEFLATED);
        zip.putNextEntry(entry);
        zip.write(data);
        zip.closeEntry();
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

    private void stubLookup(SkillAsset row) {
        when(skillAssetService.findByName(anyString())).thenReturn(row);
    }

    private void stubGetById(Long id, SkillAsset row) {
        when(skillAssetService.getById(id)).thenReturn(row);
    }

    /** 取该 id 最近一次 updateAsset 写出的 patch（幂等/负向腿由各自用例的 never 断言守） */
    private SkillAsset statusPatch(Long id) {
        ArgumentCaptor<SkillAsset> patches = ArgumentCaptor.forClass(SkillAsset.class);
        verify(skillAssetService, atLeastOnce()).updateAsset(patches.capture());
        List<SkillAsset> all = patches.getAllValues();
        return all.get(all.size() - 1);
    }
}
