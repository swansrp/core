package com.bidr.admin.vo;

import com.bidr.admin.config.PortalIdField;
import com.bidr.admin.config.PortalNameField;
import com.bidr.admin.config.PortalOrderField;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表格报表筛选项 VO - 前端交互
 *
 * @author Sharp
 */
@ApiModel(description = "表格报表筛选项")
@Data
@EqualsAndHashCode(callSuper = true)
public class PortalTableFilterVO extends BaseVO {
    /**
     * 主键 ID
     */
    @PortalIdField
    @ApiModelProperty(value = "主键 ID")
    private Long id;

    /**
     * table_id
     */
    @ApiModelProperty(value = "table_id")
    private Long tableId;

    /**
     * 筛选条目类型
     */
    @ApiModelProperty(value = "筛选条目类型")
    private String filterType;

    /**
     * 筛选条目编码
     */
    @PortalNameField
    @ApiModelProperty(value = "筛选条目编码")
    private String code;

    /**
     * 筛选条目标签
     */
    @PortalNameField
    @ApiModelProperty(value = "筛选条目标签")
    private String label;

    /**
     * 筛选条件
     */
    @ApiModelProperty(value = "筛选条件")
    private String condition;

    /**
     * 字典项
     */
    @ApiModelProperty(value = "字典项")
    private String dictCode;

    /**
     * 默认值
     */
    @ApiModelProperty(value = "默认值")
    private String defaultValue;

    /**
     * 占位文本
     */
    @ApiModelProperty(value = "占位文本")
    private String placeholder;

    /**
     * 是否允许清空
     */
    @ApiModelProperty(value = "是否允许清空")
    private String allowClear;

    /**
     * 是否多选
     */
    @ApiModelProperty(value = "是否多选")
    private String multiple;

    /**
     * 显示顺序
     */
    @PortalOrderField
    @ApiModelProperty(value = "显示顺序")
    private Integer displayOrder;

    /**
     * 状态
     */
    @ApiModelProperty(value = "状态")
    private String status;
}
