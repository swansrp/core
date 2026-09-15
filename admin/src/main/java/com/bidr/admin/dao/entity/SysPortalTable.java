package com.bidr.admin.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 表格展示配置
 *
 * @author Sharp
 */
@ApiModel(description = "表格展示配置")
@Data
@TableName(value = "sys_portal_table")
public class SysPortalTable {
    /**
     * id
     */
    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "id")
    private Long id;

    /**
     * id
     */
    @TableField(value = "portal_name")
    @ApiModelProperty(value = "表格配置名称")
    private String portalName;

    /**
     * 表格 code
     */
    @TableField(value = "table_code")
    @ApiModelProperty(value = "表格 code")
    private String tableCode;

    /**
     * 左侧筛选栏的宽度
     */
    @TableField(value = "filter_width")
    @ApiModelProperty(value = "左侧筛选栏的宽度")
    private Integer filterWidth;

    /**
     * 标题间隔
     */
    @TableField(value = "padding_th")
    @ApiModelProperty(value = "标题间隔")
    private Integer paddingTh;

    /**
     * 筛选条目间隔
     */
    @TableField(value = "padding_td")
    @ApiModelProperty(value = "筛选条目间隔")
    private Integer paddingTd;

    /**
     * 要排除显示的列
     */
    @TableField(value = "filter_columns")
    @ApiModelProperty(value = "要排除显示的列")
    private String filterColumns;

    /**
     * 是否允许下载
     */
    @TableField(value = "download_able")
    @ApiModelProperty(value = "是否允许下载")
    private String downloadAble;

    /**
     * 是否透视报表模式
     */
    @TableField(value = "pivot_mode")
    @ApiModelProperty(value = "是否透视报表模式")
    private String pivotMode;

    /**
     * 透视行维度字段(逗号分隔)
     */
    @TableField(value = "group_by_fields")
    @ApiModelProperty(value = "透视行维度字段(逗号分隔)")
    private String groupByFields;

    /**
     * 透视度量列配置JSON
     */
    @TableField(value = "pivot_measures")
    @ApiModelProperty(value = "透视度量列配置JSON")
    private String pivotMeasures;

    /**
     * 透视度量布局: col=度量作为列(默认), row=度量作为行
     */
    @TableField(value = "pivot_measure_layout")
    @ApiModelProperty(value = "透视度量布局: col=列 row=行")
    private String pivotMeasureLayout;

    /**
     * 透视合计列位置: first=靠前 last=靠后(默认)
     */
    @TableField(value = "pivot_total_pos")
    @ApiModelProperty(value = "透视合计列位置: first=靠前 last=靠后")
    private String pivotTotalPos;

    /**
     * Tab成员JSON(仅宿主): [{tableId,label,order}]
     */
    @TableField(value = "tab_items")
    @ApiModelProperty(value = "Tab成员JSON(仅宿主): [{tableId,label,order}]")
    private String tabItems;

    /**
     * 默认排序JSON: [{property,type}] type 0=正序 1=倒序, 空数组=不排序
     */
    @TableField(value = "default_sort")
    @ApiModelProperty(value = "默认排序JSON: [{property,type}]")
    private String defaultSort;

    /**
     * 状态
     */
    @TableField(value = "status")
    @ApiModelProperty(value = "状态")
    private String status;
}
