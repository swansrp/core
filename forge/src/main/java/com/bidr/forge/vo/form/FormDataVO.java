package com.bidr.forge.vo.form;

import com.bidr.admin.config.PortalIdField;
import com.bidr.admin.vo.BaseVO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表单填写数据表 VO
 *
 * @author sharp
 */
@ApiModel(description = "表单填写数据表")
@Data
@EqualsAndHashCode(callSuper = true)
public class FormDataVO extends BaseVO {
    /**
     * 主键 ID
     */
    @PortalIdField
    @ApiModelProperty(value = "主键 ID")
    private String id;

    /**
     * 上传历史 ID
     */
    @ApiModelProperty(value = "上传历史 ID")
    private String historyId;

    /**
     * 区块实例 ID
     */
    @ApiModelProperty(value = "区块实例 ID")
    private String sectionInstanceId;

    /**
     * 组实例 ID
     */
    @ApiModelProperty(value = "组实例 ID")
    private String groupInstanceId;

    /**
     * 字段 ID
     */
    @ApiModelProperty(value = "字段 ID")
    private Long attributeId;

    /**
     * 企业填写的值
     */
    @ApiModelProperty(value = "企业填写的值")
    private String value;

    /**
     * 表单版本号
     */
    @ApiModelProperty(value = "表单版本号")
    private Integer version;

    /**
     * 状态：1=有效，0=无效
     */
    @ApiModelProperty(value = "状态：1=有效，0=无效")
    private Integer status;
}
