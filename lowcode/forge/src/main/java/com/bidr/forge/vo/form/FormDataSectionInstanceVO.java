package com.bidr.forge.vo.form;

import com.bidr.admin.config.PortalIdField;
import com.bidr.admin.vo.BaseVO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表单区块实例 VO
 *
 * @author sharp
 */
@ApiModel(description = "表单区块实例")
@Data
@EqualsAndHashCode(callSuper = true)
public class FormDataSectionInstanceVO extends BaseVO {
    /**
     * 主键 ID
     */
    @PortalIdField
    @ApiModelProperty(value = "主键 ID")
    private String id;

    /**
     * 上报历史 id
     */
    @ApiModelProperty(value = "上报历史 id")
    private String historyId;

    /**
     * 区块 id
     */
    @ApiModelProperty(value = "区块 id")
    private Long sectionId;

    /**
     * 版本号
     */
    @ApiModelProperty(value = "版本号")
    private Integer versionNo;

    /**
     * 备注/说明
     */
    @ApiModelProperty("备注/说明")
    private String remark;

    /**
     * 上报内容 JSON
     */
    @ApiModelProperty(value = "上报内容 JSON")
    private String formContent;

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
     * 审核人
     */
    @ApiModelProperty(value = "审核人")
    private String confirmBy;

    /**
     * 审核时间
     */
    @ApiModelProperty(value = "审核时间")
    private java.util.Date confirmAt;

    /**
     * 状态：1=已提交，0=草稿，2=审核中，3=退回
     */
    @ApiModelProperty(value = "状态：1=已提交，0=草稿，2=审核中，3=退回")
    private String confirmStatus;

    /**
     * 审核意见
     */
    @ApiModelProperty("审核意见")
    private String confirmComment;
}
