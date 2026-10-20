package com.bidr.task.dao.entity;

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
 * 通用异步任务（受理落库＋阶段推进＋重试记账；M16 棒 2 新表）
 *
 * <p>任务身份＝本行主键，一任务一行：阶段推进/重试/终态全是同行 UPDATE。
 * 业务侧不存本表 id、存 {@code biz_ref}（幂等键 {@code (task_type, biz_ref)} 即寻址正源，
 * 同键再 submit＝同一行）；不给业务表加 task_id 列。</p>
 *
 * @author sharp
 */
@ApiModel(description = "通用异步任务（受理落库＋阶段推进＋重试记账）")
@Data
@AccountContextFill
@TableName(value = "sa_async_task")
public class SaAsyncTask {

    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "id")
    private Long id;

    @TableField(value = "task_type")
    @ApiModelProperty(value = "任务类型（业务流程标识）")
    private String taskType;

    @TableField(value = "stage")
    @ApiModelProperty(value = "当前阶段标识（只许原地或前进）")
    private String stage;

    @TableField(value = "`status`")
    @ApiModelProperty(value = "状态 PENDING/RUNNING/SUCCESS/FAILED/DEAD")
    private String status;

    @TableField(value = "payload")
    @ApiModelProperty(value = "业务提交的入参 JSON（跨阶段载体一）")
    private String payload;

    @TableField(value = "biz_ref")
    @ApiModelProperty(value = "业务参照（幂等键后半，业务对象唯一定位串）")
    private String bizRef;

    @TableField(value = "external_id")
    @ApiModelProperty(value = "外部引擎任务id（跨阶段载体二）")
    private String externalId;

    @TableField(value = "external_status")
    @ApiModelProperty(value = "外部状态观测（对账用）")
    private String externalStatus;

    @TableField(value = "external_snapshot")
    @ApiModelProperty(value = "外部子状态快照JSON（对账用）")
    private String externalSnapshot;

    @TableField(value = "attempt")
    @ApiModelProperty(value = "已尝试次数")
    private Integer attempt;

    @TableField(value = "next_run_at")
    @ApiModelProperty(value = "下次可执行时间（退避用）")
    private Date nextRunAt;

    @TableField(value = "error")
    @ApiModelProperty(value = "最近错误")
    private String error;

    @TableField(value = "owner")
    @ApiModelProperty(value = "抢占者（实例标识）")
    private String owner;

    @TableField(value = "lease_expire")
    @ApiModelProperty(value = "租约到期")
    private Date leaseExpire;

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

    @TableField(value = "`valid`")
    @ApiModelProperty(value = "有效性")
    private String valid;

}
