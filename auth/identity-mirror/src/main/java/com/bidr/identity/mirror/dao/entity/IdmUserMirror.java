package com.bidr.identity.mirror.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * Title: IdmUserMirror
 * Description: 用户身份镜像表（T2.3d）——上游用户主表的最小身份投影
 * （id＋customer_number＋name），经 {@code /open-api/v1/identity/snapshot} 拉平落库。
 * <p>🔴 G9 边界：镜像只承载「这是谁」，<b>零角色/菜单/功能码字段</b>（IdentityMirrorConventionTest 钉）——
 * 授权判定一律回 provider（/authz 端点），子系统本地不留授权语义。</p>
 * <p>{@code id} 非自增：恒等于上游用户 id（跨系统 join 键稳定，mirror.id=上游 uid 裁定）；
 * {@code customer_number} 唯一——{@code @BindUser} 经 {@code BindRepoResolver} SPI 解析到本表后的
 * matchField 落点。valid/create_at/update_at 对齐全仓镜像惯例（BindRepoHandler valid='1' 条件追加语义）。</p>
 *
 * @author Sharp
 * @since 2026/11/03
 */
@ApiModel(description = "用户身份镜像表")
@Data
@TableName(value = "idm_user_mirror")
public class IdmUserMirror {

    /**
     * 用户 id（＝上游用户 id，非自增）
     */
    @TableId(value = "id", type = IdType.INPUT)
    @ApiModelProperty(value = "用户id（上游user_id）")
    private Long id;

    /**
     * 用户编码（上游唯一标识，镜像侧唯一键）
     */
    @TableField(value = "customer_number")
    @ApiModelProperty(value = "用户编码")
    private String customerNumber;

    /**
     * 用户姓名
     */
    @TableField(value = "name")
    @ApiModelProperty(value = "用户姓名")
    private String name;

    /**
     * 创建时间（快照首次落库时刻）
     */
    @TableField(value = "create_at")
    @ApiModelProperty(value = "创建时间")
    private Date createAt;

    /**
     * 更新时间（快照有差异时的落库时刻）
     */
    @TableField(value = "update_at")
    @ApiModelProperty(value = "更新时间")
    private Date updateAt;

    /**
     * 有效性（惯例软删位，镜像行随上游全量快照增删，恒 '1'）
     */
    @TableField(value = "`valid`")
    @ApiModelProperty(value = "有效性")
    private String valid;
}
