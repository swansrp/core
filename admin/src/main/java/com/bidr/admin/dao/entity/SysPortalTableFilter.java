package com.bidr.admin.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 表格报表筛选项
 *
 * @author Sharp
 */
@ApiModel(description = "表格报表筛选项")
@Data
@TableName(value = "sys_portal_table_filter")
public class SysPortalTableFilter {
    /**
     * id
     */
    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "id")
    private Long id;

    /**
     * table_id
     */
    @TableField(value = "table_id")
    @ApiModelProperty(value = "table_id")
    private Long tableId;

    /**
     * 筛选条目类型
     */
    @TableField(value = "filter_type")
    @ApiModelProperty(value = "筛选条目类型")
    private String filterType;

    /**
     * 筛选条目标签
     */
    @TableField(value = "label")
    @ApiModelProperty(value = "筛选条目标签")
    private String label;

    /**
     * 筛选条件
     */
    @TableField(value = "`condition`")
    @ApiModelProperty(value = "筛选条件")
    private String condition;

    /**
     * 字典项
     */
    @TableField(value = "dict_code")
    @ApiModelProperty(value = "字典项")
    private String dictCode;

    /**
     * 默认值
     */
    @TableField(value = "default_value")
    @ApiModelProperty(value = "默认值")
    private String defaultValue;

    /**
     * 占位文本
     */
    @TableField(value = "placeholder")
    @ApiModelProperty(value = "占位文本")
    private String placeholder;

    /**
     * 是否允许清空
     */
    @TableField(value = "allow_clear")
    @ApiModelProperty(value = "是否允许清空")
    private String allowClear;

    /**
     * 是否多选
     */
    @TableField(value = "multiple")
    @ApiModelProperty(value = "是否多选")
    private String multiple;

    /**
     * 显示顺序
     */
    @TableField(value = "display_order")
    @ApiModelProperty(value = "显示顺序")
    private Integer displayOrder;

    /**
     * 状态
     */
    @TableField(value = "status")
    @ApiModelProperty(value = "状态")
    private String status;
}
