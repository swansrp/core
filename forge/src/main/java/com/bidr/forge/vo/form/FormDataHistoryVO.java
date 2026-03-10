package com.bidr.forge.vo.form;

import com.bidr.admin.config.PortalIdField;
import com.bidr.admin.vo.BaseVO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表单填写历史 VO
 *
 * @author sharp
 */
@ApiModel(description = "表单填写历史")
@Data
@EqualsAndHashCode(callSuper = true)
public class FormDataHistoryVO extends BaseVO {
    /**
     * 主键 ID
     */
    @PortalIdField
    @ApiModelProperty(value = "主键 ID")
    private String id;

    /**
     * 表单 ID
     */
    @ApiModelProperty(value = "表单 ID")
    private String formId;

    /**
     * 批号
     */
    @ApiModelProperty(value = "批号")
    private String versionNo;

    /**
     * 总体状态：0 草稿 1 提交 2 审核中 3 通过 4 退回
     */
    @ApiModelProperty(value = "总体状态：0 草稿 1 提交 2 审核中 3 通过 4 退回")
    private String status;

    /**
     * 备注/说明
     */
    @ApiModelProperty("备注/说明")
    private String remark;

    /**
     * 提交人
     */
    @ApiModelProperty(value = "提交人")
    private String submittedBy;

    /**
     * 提交时间
     */
    @ApiModelProperty(value = "提交时间")
    private java.util.Date submittedAt;

    /**
     * 审批人
     */
    @ApiModelProperty(value = "审批人")
    private String confirmBy;

    /**
     * 审批时间
     */
    @ApiModelProperty(value = "审批时间")
    private java.util.Date confirmAt;

    /**
     * 审批理由
     */
    @ApiModelProperty(value = "审批理由")
    private String confirmReason;
}
