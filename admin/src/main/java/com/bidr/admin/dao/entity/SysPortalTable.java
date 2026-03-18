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
     * 状态
     */
    @TableField(value = "status")
    @ApiModelProperty(value = "状态")
    private String status;
}
