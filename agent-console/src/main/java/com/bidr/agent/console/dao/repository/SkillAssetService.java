package com.bidr.agent.console.dao.repository;

import com.bidr.agent.console.dao.entity.SkillAsset;
import com.bidr.agent.console.dao.mapper.SkillAssetMapper;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Title: SkillAssetService
 * Description: 系统级技能资产台账数据仓库（仅读写方法，DDL 见 Schema）。
 *
 * <p>本表是框架平台表，<b>不参与空间行级隔离</b>：它要服务的是"这场会话根本没有空间上下文"的读场景，
 * 隔离器会把行筛掉，系统半就读空了。空间维度的可见性由业务侧那张表负责。</p>
 *
 * <p>本类只负责取行与落行：状态怎么流转（发布/下线/墓碑）、按名唯一怎么保证，都不在这里判定——
 * 那是上层资产服务的职责，仓库层判定就会出现两处口径。</p>
 *
 * @author Sharp
 * @since 2026-10-13
 */
@Service
public class SkillAssetService extends BaseSqlRepo<SkillAssetMapper, SkillAsset> {

    /** 某个来源档的全部有效行（对账器取 BUILTIN 行用；origin 空＝不限来源） */
    public List<SkillAsset> listByOrigin(String origin) {
        return lambdaQuery()
                .eq(origin != null && !origin.trim().isEmpty(), SkillAsset::getOrigin,
                        origin == null ? null : origin.trim())
                .eq(SkillAsset::getValid, CommonConst.YES)
                .orderByDesc(SkillAsset::getId)
                .list();
    }

    /**
     * 按名取一行（无则 null）——按名 upsert 与"系统半命中"两条腿共用。
     *
     * <p>同名多行时取 id 最大的一行：本表按软删表达"撤销后再加回"，且重递版本可以并存，
     * 故同名命中多行是设计内的事。<b>以最新（id 最大）为准</b>才符合"重递后读到的就是新那一版"；
     * 取旧行会把已加回的技能继续报成下线状态。</p>
     *
     * <p>这里<b>不过滤 valid</b>：upsert 要先看清"这个名字最近是什么状态（含墓碑）"才敢落新行，
     * 只看有效行会把软删行当成不存在、下次加回变成两条并存。</p>
     */
    public SkillAsset findByName(String skillName) {
        if (skillName == null || skillName.trim().isEmpty()) {
            return null;
        }
        return lambdaQuery()
                .eq(SkillAsset::getSkillName, skillName.trim())
                .orderByDesc(SkillAsset::getId)
                .last("limit 1")
                .one();
    }

    /** 系统半可读集：已发布且有效（下线与墓碑行不进这个集合） */
    public List<SkillAsset> listPublished() {
        return lambdaQuery()
                .eq(SkillAsset::getStatus, SkillAsset.STATUS_PUBLISHED)
                .eq(SkillAsset::getValid, CommonConst.YES)
                .orderByDesc(SkillAsset::getId)
                .list();
    }

    /**
     * 按名取<b>可读</b>的一行（无则 null）：读侧闸门用，与 {@link #listPublished()} 是同一把尺
     * （{@code status=PUBLISHED ∧ valid=1}），差别只在这里按名收口。
     *
     * <p><b>可读的单位是行、不是名字</b>：同名重传落的是 {@code UNPUBLISHED} 的新行，
     * 上一个已发布行仍然活着——"发布的是那一版"，新版本没发布就不生效。若这里改成
     * {@code findByName}（取最近一行、不过滤状态）再判状态，就会出现"清单里有它、读了报不存在"
     * 的分裂（{@code listPublished} 按行筛、清单里那个旧发布行在，闸门却看新行）。
     * 可见集与读闸门必须同一处判据，所以同名多行取 id 最大（最新那一版）。</p>
     */
    public SkillAsset findPublishedByName(String skillName) {
        if (skillName == null || skillName.trim().isEmpty()) {
            return null;
        }
        return lambdaQuery()
                .eq(SkillAsset::getSkillName, skillName.trim())
                .eq(SkillAsset::getStatus, SkillAsset.STATUS_PUBLISHED)
                .eq(SkillAsset::getValid, CommonConst.YES)
                .orderByDesc(SkillAsset::getId)
                .last("limit 1")
                .one();
    }

    /**
     * 落一行新档案。
     *
     * <p>方法名不叫 {@code save}：基类 {@code IService#save} 已占同名同参（返回 boolean），
     * 这里声明 void 版会撞返回类型。命名习惯随基类读写口（{@code insert}/{@code updateById}），
     * 加 Asset 后缀只为调用点上读得懂是谁的行。</p>
     */
    public void saveAsset(SkillAsset row) {
        insert(row);
    }

    /**
     * 按 id 更新一行档案，<b>空值不进 SET</b>：这一版没取到 sha256/文件数时，
     * 更新不能把上一版的指纹抹成 NULL——指纹一旦被抹，对账器下次会把同一内容当成"变了"。
     */
    public void updateAsset(SkillAsset row) {
        updateById(row);
    }
}
