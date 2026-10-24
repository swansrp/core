package com.bidr.notification.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bidr.authorization.mybatis.anno.AccountContextFill;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * 站内消息（M21；一收件人一行＝写时扇开，{@code target_user} 即作用域）。
 *
 * <p>租户列可空且<b>只作归属不作拦截器过滤</b>（null＝全局消息，任何租户可见；过滤条件恒为
 * {@code tenant_id IS NULL OR tenant_id IN (我的租户)}，由默认 store 显式拼——不靠
 * {@code TenantLineInnerInterceptor}，各家隔离列不同）。core 不含业务值域：msg_type/action_type
 * 是宿主自定义字符串，业务上下文进 {@code extra} JSON。</p>
 *
 * @author sharp (M21：站内信机制 2026-10-25)
 */
@ApiModel(description = "站内消息")
@Data
@AccountContextFill
@TableName(value = "sa_notification")
public class NotificationRecord {

    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "id")
    private Long id;

    @TableField(value = "msg_type")
    @ApiModelProperty(value = "消息类型（宿主自定义字符串）")
    private String msgType;

    @TableField(value = "level")
    @ApiModelProperty(value = "级别 INFO/WARN/ERROR")
    private String level;

    @TableField(value = "title")
    @ApiModelProperty(value = "标题")
    private String title;

    @TableField(value = "summary")
    @ApiModelProperty(value = "摘要（未读列表露出的人话原因）")
    private String summary;

    @TableField(value = "content")
    @ApiModelProperty(value = "正文（详情态）")
    private String content;

    @TableField(value = "action_type")
    @ApiModelProperty(value = "点击动作码（宿主自定义，空＝不可点）")
    private String actionType;

    @TableField(value = "action_params")
    @ApiModelProperty(value = "点击参数 JSON（只放 id/枚举）")
    private String actionParams;

    @TableField(value = "biz_type")
    @ApiModelProperty(value = "业务对象类型（治理/跳转辅助）")
    private String bizType;

    @TableField(value = "biz_id")
    @ApiModelProperty(value = "业务对象标识")
    private String bizId;

    @TableField(value = "dedupe_key")
    @ApiModelProperty(value = "去重键：同收件人+同键+未读＝只更新不新增")
    private String dedupeKey;

    @TableField(value = "target_user")
    @ApiModelProperty(value = "收件人（ac_user.customer_number；作用域即此人）")
    private String targetUser;

    @TableField(value = "tenant_id")
    @ApiModelProperty(value = "租户归属（可空＝全局消息；信息列，不进拦截器过滤）")
    private Long tenantId;

    @TableField(value = "read_flag")
    @ApiModelProperty(value = "已读标志（0 未读 1 已读）")
    private String readFlag;

    @TableField(value = "read_at")
    @ApiModelProperty(value = "已读时间")
    private Date readAt;

    @TableField(value = "pushed_at")
    @ApiModelProperty(value = "消息产生时间（列表排序）")
    private Date pushedAt;

    @TableField(value = "extra")
    @ApiModelProperty(value = "业务上下文 JSON（core 不认识这些键）")
    private String extra;

    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    @TableField(value = "create_at", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建时间")
    private Date createAt;

    @TableField(value = "update_by", fill = FieldFill.INSERT_UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    @TableField(value = "update_at", fill = FieldFill.INSERT_UPDATE)
    @ApiModelProperty(value = "更新时间")
    private Date updateAt;

    @TableField(value = "valid")
    @ApiModelProperty(value = "有效性")
    private String valid;
}
