package com.bidr.agent.console.dto;

import com.bidr.agent.console.dao.entity.SkillAsset;
import com.bidr.authorization.bind.annotation.BindUser;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * Title: SkillAssetView
 * Description: 系统级技能包台账的读视图（M14 棒 4 增量补）。
 *
 * <p>存在的理由只有一条：{@link SkillAsset} 是 {@code sys_skill_asset} 的 DDL 映射，
 * <b>显示用派生列不许挂进实体</b>。管理页要把"登记人"从工号翻成姓名，就多一列读侧派生字段，
 * 由端点在返回前经框架 {@code @BindUser} 批量翻译回填。</p>
 *
 * <p>与实体的两处刻意差异：① 不给 {@code packageKey}（存储对象键，页面不消费，没必要随台账下发）；
 * ② 不给 {@code valid}（{@code /assets} 的读腿本身只回 valid=1 的行，撤销的正确表达是
 * {@code status=TOMBSTONE}，回一个恒为 1 的列只会让人以为还有别的值）。</p>
 *
 * @author Sharp
 * @since 2026-10-13
 */
@ApiModel(description = "系统级技能包台账行（读视图）")
@Data
public class SkillAssetView {

    @ApiModelProperty(value = "台账行 id")
    private Long id;

    @ApiModelProperty(value = "包名（frontmatter.name＝根目录名，系统内唯一保留名）")
    private String skillName;

    @ApiModelProperty(value = "展示名（NULL＝包 frontmatter 没给，展示层回落 skillName）")
    private String title;

    @ApiModelProperty(value = "描述")
    private String description;

    @ApiModelProperty(value = "分类")
    private String category;

    @ApiModelProperty(value = "版本号（NULL＝没取到版本，不是第 0 版）")
    private Integer version;

    @ApiModelProperty(value = "状态：UNPUBLISHED / PUBLISHED / OFFLINE / TOMBSTONE")
    private String status;

    @ApiModelProperty(value = "来源：BUILTIN 随 jar 发版（重启按内容指纹对账）／UPLOADED 页面上传（对账永不触碰）")
    private String origin;

    @ApiModelProperty(value = "包内容 sha256（归一化重打包后的字节，与对账器同一把尺）")
    private String packageSha256;

    @ApiModelProperty(value = "包内文件数")
    private Integer fileCount;

    @ApiModelProperty(value = "登记人工号（对账线程写的行没有登录人，为空）")
    private String createBy;

    /**
     * 登记人姓名（读侧派生，非表列）：{@code create_by} 存工号且由 {@code FieldFill.INSERT} 写入，
     * 是审计列不能改形态，所以姓名另挂一列，由读端点回填
     */
    @BindUser("createBy")
    @ApiModelProperty(value = "登记人姓名（按工号查 ac_user.name，查不到为空，页面回落到工号）")
    private String createByName;

    @ApiModelProperty(value = "登记时刻")
    private Date createAt;

    @ApiModelProperty(value = "最后改动时刻（发布/下线/注销都算）")
    private Date updateAt;

    /** 台账行 → 读视图（null 入参返回 null）；姓名派生列不在此填，只由端点回填 */
    public static SkillAssetView of(SkillAsset row) {
        if (row == null) {
            return null;
        }
        SkillAssetView view = new SkillAssetView();
        view.setId(row.getId());
        view.setSkillName(row.getSkillName());
        view.setTitle(row.getTitle());
        view.setDescription(row.getDescription());
        view.setCategory(row.getCategory());
        view.setVersion(row.getVersion());
        view.setStatus(row.getStatus());
        view.setOrigin(row.getOrigin());
        view.setPackageSha256(row.getPackageSha256());
        view.setFileCount(row.getFileCount());
        view.setCreateBy(row.getCreateBy());
        view.setCreateAt(row.getCreateAt());
        view.setUpdateAt(row.getUpdateAt());
        return view;
    }
}
