package com.bidr.agent.console.skill;

import com.bidr.agent.console.dao.entity.SkillAsset;
import com.bidr.agent.console.dao.repository.SkillAssetService;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.exception.NoticeException;
import com.bidr.kernel.exception.ServiceException;
import com.bidr.kernel.utils.BeanUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Title: SystemSkillAdminService
 * Description: 系统级技能包的<b>写面</b>（上传/发布/下线/墓碑），平台管理员专属（M14 棒 4-E）。
 *
 * <p><b>上传 ≠ 可见</b>：读侧（棒 4-F）判"可读"的依据是 {@code status=PUBLISHED ∧ valid=1} 的行，
 * 上传只落 {@code UNPUBLISHED}——必须有显式的"发布"动作才进可读集。台账列表
 * {@link #listAssets()} 供管理页看全档（未发布/下线/墓碑）。</p>
 *
 * <p><b>存进去的字节必须是确定性重打包的产物</b>：上传动作先过 {@link SkillPackageParser} 契约尺，
 * 再用 {@link SkillPackageWriter#pack} 归一化（条目排序、固定时间、store 不压缩），sha 按归一化字节算。
 * 否则同一个包两次上传（一次带目录条目、一次不带）会算出不同 sha，
 * 读侧与对账器的内容指纹尺就对不上。入参 zip 与落库字节通常不相等——这是设计，不是缺陷。</p>
 *
 * <p><b>出厂包名是保留名</b>：对账器按名认账，允许上传同名 BUILTIN 会撞
 * {@code agent-skill/<name>/v1.zip} 这个 key、把 jar 的内容覆盖掉。上传遇到同名 BUILTIN 行直接拒绝；
 * 反方向（管理员把出厂包下线/注销）是本服务的合法动作——所有权（origin）不是状态，三种状态转换都不写它。</p>
 *
 * <p><b>墓碑是"撤销"的唯一正确表达，不是软删</b>：棒 4-D 已钉住对账器行为——
 * {@code origin=BUILTIN} 的行若只置 {@code valid=0} 会被下次启动就地复活，
 * 而 {@code status=TOMBSTONE} 的行对账器永远跳过。所以墓碑动作<b>只写 status 一列</b>
 * （只带 id+status 的 patch 走 {@code updateAsset}，ignoreNull 语义保证 valid/对象/sha 全不动）、
 * 不删对象（存储层本来也没有删的口，对象留档可回滚）。</p>
 *
 * <p><b>拒绝一律 {@link ServiceException}，不用 {@link NoticeException}</b>：框架的异常渲染是两条路——
 * notice 落 {@code status.code=0}（"提示语义"，前端看到的就是成功），ServiceException 才落非零码。
 * 本服务的六种"什么都没做成"（非管理员、坏包、撞保留名、状态不允许、行不存在、墓碑想发布）
 * 全属拒绝，若走 notice 就等于对着管理员说"传成功了"而台账里根本没那行。
 * {@code SkillPackageParser} 是业务侧上传共用的尺（它抛 notice，改动会漂移到业务端点上），
 * 所以翻译只在本服务的边界做一次。</p>
 *
 * <p><b>先存对象再落行</b>：行是账、对象是货；反过来会出现"账上有地址、货不在"的坏行。
 * 同名重传＝新版本新行（不 update 旧行），墓碑过的名字允许再传、视作新一档
 * （{@code findByName} 不过滤 valid，正是为了让版本从最近一行继续递增）。</p>
 *
 * <p><b>管理员闸门走反射，不加 authorization 依赖</b>：与棒 2 已定的
 * {@code AgentManifestController#isAdmin()} 同一口径——本模块不编译依赖 authorization，
 * bean 取不到＝按非管理员（fail closed，宁可拒绝也不放开）。</p>
 *
 * @author Sharp
 * @since 2026-10-13
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemSkillAdminService {

    private final SkillAssetService skillAssetService;

    private final SystemSkillStore store;

    /**
     * 上传系统级技能包：契约校验 → 确定性重打包 → 撞名检查 → 先存对象再落行。
     * 落库行一律 {@code origin=UPLOADED}、{@code status=UNPUBLISHED}（上传 ≠ 可见）。
     *
     * @param zipBytes 浏览器上传的 zip 原始字节
     * @return 新落的台账行
     */
    public SkillAsset upload(byte[] zipBytes) {
        requirePlatformAdmin();
        SkillPackage pkg = parseAsRejection(zipBytes);
        byte[] normalized = SkillPackageWriter.pack(pkg.getName(), pkg.getFiles());
        String sha = SkillPackageWriter.sha256Hex(normalized);
        SkillAsset existing = skillAssetService.findByName(pkg.getName());
        if (existing != null && SkillAsset.ORIGIN_BUILTIN.equals(existing.getOrigin())) {
            throw new ServiceException("出厂包名保留，请改名或走业务侧上传: " + pkg.getName());
        }
        int version = existing == null ? 1 : (existing.getVersion() == null ? 1 : existing.getVersion()) + 1;
        String key = SystemSkillStore.packageKey(pkg.getName(), version);
        store.put(key, normalized);
        SkillAsset row = new SkillAsset();
        row.setSkillName(pkg.getName());
        row.setTitle(pkg.getTitle());
        row.setDescription(pkg.getDescription());
        row.setCategory(pkg.getCategory());
        row.setVersion(version);
        row.setStatus(SkillAsset.STATUS_UNPUBLISHED);
        row.setOrigin(SkillAsset.ORIGIN_UPLOADED);
        row.setPackageKey(key);
        row.setPackageSha256(sha);
        row.setFileCount(pkg.getFiles().size());
        row.setValid(CommonConst.YES);
        // createBy 不手写：登录线程由 FieldFill.INSERT 填
        skillAssetService.saveAsset(row);
        log.info("系统技能包已上传: name={} v{} files={} sha={}（未发布，发布后方可读）",
                pkg.getName(), version, pkg.getFiles().size(), sha);
        return row;
    }

    /**
     * 发布：{@code UNPUBLISHED}/{@code OFFLINE} → {@code PUBLISHED}（进入读侧可读集）。
     * 已是 PUBLISHED＝幂等成功不写库；TOMBSTONE＝拒绝（注销不可逆，要恢复请重新上传）。
     */
    public void publish(Long id) {
        requirePlatformAdmin();
        SkillAsset row = requireById(id);
        if (SkillAsset.STATUS_PUBLISHED.equals(row.getStatus())) {
            return;
        }
        if (SkillAsset.STATUS_TOMBSTONE.equals(row.getStatus())) {
            throw new ServiceException("已注销的技能包不可发布（注销不可逆），请重新上传: id=" + id);
        }
        requireStatusIn(id, row, "发布", SkillAsset.STATUS_UNPUBLISHED, SkillAsset.STATUS_OFFLINE);
        writeStatus(id, SkillAsset.STATUS_PUBLISHED);
    }

    /**
     * 下线：{@code PUBLISHED} → {@code OFFLINE}（读侧立即不再下发；行与对象保留、可重新发布）。
     * 已是 OFFLINE＝幂等成功不写库；UNPUBLISHED＝拒绝（尚未发布，无需下线）；TOMBSTONE＝拒绝。
     */
    public void offline(Long id) {
        requirePlatformAdmin();
        SkillAsset row = requireById(id);
        if (SkillAsset.STATUS_OFFLINE.equals(row.getStatus())) {
            return;
        }
        if (SkillAsset.STATUS_UNPUBLISHED.equals(row.getStatus())) {
            throw new ServiceException("尚未发布，无需下线: id=" + id);
        }
        requireStatusIn(id, row, "下线", SkillAsset.STATUS_PUBLISHED);
        writeStatus(id, SkillAsset.STATUS_OFFLINE);
    }

    /**
     * 注销墓碑：任何状态 → {@code TOMBSTONE}（对账器永远跳过、不复活；撤销的正确表达）。
     * <b>只写 status 一列</b>：不碰 valid、不删对象（留档可回滚）。已是 TOMBSTONE＝幂等成功不写库。
     */
    public void tombstone(Long id) {
        requirePlatformAdmin();
        SkillAsset row = requireById(id);
        if (SkillAsset.STATUS_TOMBSTONE.equals(row.getStatus())) {
            return;
        }
        writeStatus(id, SkillAsset.STATUS_TOMBSTONE);
    }

    /**
     * 管理页台账列表：origin/status 都不筛（含未发布/下线/墓碑全档），同样过管理员闸。
     * 口径说明：复用仓库既有 {@code listByOrigin(null)}，故 valid=0 的软删历史行不进列表——
     * 墓碑行的 valid 恒为原值（写墓碑不碰 valid），全档语义由 status 表达。
     */
    public List<SkillAsset> listAssets() {
        requirePlatformAdmin();
        return skillAssetService.listByOrigin(null);
    }

    // ---------------------------------------------------------------- 内部

    /**
     * 包契约尺（{@link SkillPackageParser}）与业务侧上传共用，它按"提示语义"抛 notice；
     * 在本服务的边界上坏包就是拒绝，必须换成非零码——只翻译这一处，业务端点行为零漂移。
     */
    private SkillPackage parseAsRejection(byte[] zipBytes) {
        try {
            return SkillPackageParser.parse(zipBytes);
        } catch (NoticeException e) {
            throw new ServiceException(e.getMessage());
        }
    }

    private SkillAsset requireById(Long id) {
        SkillAsset row = id == null ? null : skillAssetService.getById(id);
        if (row == null) {
            throw new ServiceException("技能包不存在: id=" + id);
        }
        return row;
    }

    private void requireStatusIn(Long id, SkillAsset row, String action, String... allowed) {
        for (String status : allowed) {
            if (status.equals(row.getStatus())) {
                return;
            }
        }
        throw new ServiceException("当前状态不可" + action + ": id=" + id + " status=" + row.getStatus());
    }

    /** 状态转换只写 status 一列：patch 不带 origin（所有权不是状态），空值不进 SET ⇒ valid/sha/对象全不动 */
    private void writeStatus(Long id, String target) {
        SkillAsset patch = new SkillAsset();
        patch.setId(id);
        patch.setStatus(target);
        skillAssetService.updateAsset(patch);
        log.info("系统技能包状态已变更: id={} -> {}", id, target);
    }

    private void requirePlatformAdmin() {
        if (!isPlatformAdmin()) {
            throw new ServiceException("仅平台管理员可操作系统级技能包");
        }
    }

    /**
     * 管理员判定走 authorization 的 permitService（本模块不编译依赖它，
     * bean 取不到＝按非管理员处理，同 {@code AgentManifestController} 口径——
     * 宁可拒绝一个操作，不能让缺 bean 变成放开写面）。protected 是为单测放行留的缝。
     */
    protected boolean isPlatformAdmin() {
        try {
            return Boolean.TRUE.equals(BeanUtil.invoke("permitService", "isAdmin", Boolean.class));
        } catch (Exception e) {
            return false;
        }
    }
}
