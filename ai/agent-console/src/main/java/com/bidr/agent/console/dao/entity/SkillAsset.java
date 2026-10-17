package com.bidr.agent.console.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * Title: SkillAsset
 * Description: 系统级技能资产台账（一行一个技能包档）：记"这个技能在系统层面是什么状态、包在哪、第几版"。
 * 空间维度的使用关系不在本表（那半在 {@code kb_skill}），本表的存在意义是<b>没有空间上下文的会话也能读到</b>。
 *
 * <p>本表不带空间/用户/对象存储模板的任何语义：core 是被所有项目共用的框架，技能内容归接入方业务侧解释。</p>
 *
 * @author Sharp
 * @since 2026-10-13
 */
@ApiModel(description = "系统级技能资产台账（技能包档：状态/来源/包位置/版本）")
@Data
@TableName(value = "sys_skill_asset")
public class SkillAsset {

    /** 状态：已登记未发布，不注入提示词 */
    public static final String STATUS_UNPUBLISHED = "UNPUBLISHED";

    /** 状态：已发布，系统半可读集的消费对象 */
    public static final String STATUS_PUBLISHED = "PUBLISHED";

    /** 状态：主动下线（与 UNPUBLISHED 区别在于曾经发布过，下线是决策不是缺省） */
    public static final String STATUS_OFFLINE = "OFFLINE";

    /** 状态：注销墓碑（包本体已不可用，只留名以免同名再加回时误认成同一个东西） */
    public static final String STATUS_TOMBSTONE = "TOMBSTONE";

    /** 来源：随 jar 发版，由对账器按内容指纹维护（本行可能被对账器改写） */
    public static final String ORIGIN_BUILTIN = "BUILTIN";

    /** 来源：页面上传的数据资产。🔴 对账永不触碰——它是运行期产生的数据，不是代码的附属物 */
    public static final String ORIGIN_UPLOADED = "UPLOADED";

    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "主键")
    private Long id;

    @TableField(value = "skill_name")
    @ApiModelProperty(value = "技能包名（提示词与工具里出现的那个名字）")
    private String skillName;

    @TableField(value = "title")
    @ApiModelProperty(value = "展示名（NULL＝没提供，展示层回落 skillName）")
    private String title;

    @TableField(value = "description")
    @ApiModelProperty(value = "技能描述（提示词渐进式披露用）")
    private String description;

    @TableField(value = "category")
    @ApiModelProperty(value = "分类（NULL＝未归类，不是\"无分类\"这个分类）")
    private String category;

    /** NULL 与 0 不等价：0 会被读成一个真实的第一版，NULL 才是"没取到版本"的诚实表达 */
    @TableField(value = "version")
    @ApiModelProperty(value = "当前版本（重递++；NULL＝没取到，不填 0 冒充）")
    private Integer version;

    @TableField(value = "status")
    @ApiModelProperty(value = "状态：UNPUBLISHED/PUBLISHED/OFFLINE/TOMBSTONE")
    private String status;

    @TableField(value = "origin")
    @ApiModelProperty(value = "来源：BUILTIN 随 jar 发版/UPLOADED 页面上传（对账不碰）")
    private String origin;

    @TableField(value = "package_key")
    @ApiModelProperty(value = "当前版本包在对象存储里的 key（本表只存定位串，不碰存储客户端）")
    private String packageKey;

    @TableField(value = "package_sha256")
    @ApiModelProperty(value = "确定性重打包后的内容指纹（NULL＝还没算过，与\"算过且为空包\"可区分）")
    private String packageSha256;

    @TableField(value = "file_count")
    @ApiModelProperty(value = "包内文件数（NULL＝没数过）")
    private Integer fileCount;

    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建人（对账线程无登录上下文时为空，属正常）")
    private String createBy;

    @TableField(value = "create_at", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建时间")
    private Date createAt;

    @TableField(value = "update_at", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新时间（重递版本/改状态才动）")
    private Date updateAt;

    @TableField(value = "`valid`")
    @ApiModelProperty(value = "有效性（撤销走软删，故同名可以多行并存——见 Schema 里\"为什么没有唯一键\"）")
    private String valid;
}
