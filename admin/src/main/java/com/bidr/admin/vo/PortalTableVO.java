package com.bidr.admin.vo;

import com.bidr.admin.config.PortalIdField;
import com.bidr.admin.config.PortalNameField;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表格展示配置 VO - 前端交互
 *
 * @author Sharp
 */
@ApiModel(description = "表格展示配置")
@Data
@EqualsAndHashCode(callSuper = true)
public class PortalTableVO extends BaseVO {
    /**
     * 主键 ID
     */
    @PortalIdField
    @ApiModelProperty(value = "主键 ID")
    private Long id;

    /**
     * 表格配置名称
     */
    @ApiModelProperty(value = "表格配置名称")
    private String portalName;

    /**
     * 表格 code
     */
    @PortalNameField
    @ApiModelProperty(value = "表格 code")
    private String tableCode;

    /**
     * 左侧筛选栏的宽度
     */
    @ApiModelProperty(value = "左侧筛选栏的宽度")
    private Integer filterWidth;

    /**
     * 标题间隔
     */
    @ApiModelProperty(value = "标题间隔")
    private Integer paddingTh;

    /**
     * 筛选条目间隔
     */
    @ApiModelProperty(value = "筛选条目间隔")
    private Integer paddingTd;

    /**
     * 要排除显示的列
     */
    @ApiModelProperty(value = "要排除显示的列")
    private String filterColumns;

    /**
     * 是否可下载
     */
    @ApiModelProperty(value = "是否可下载")
    private String downloadAble;

    /**
     * 是否透视报表模式
     */
    @ApiModelProperty(value = "是否透视报表模式")
    private String pivotMode;

    /**
     * 透视行维度字段(逗号分隔)
     */
    @ApiModelProperty(value = "透视行维度字段(逗号分隔)")
    private String groupByFields;

    /**
     * 透视度量列配置JSON
     */
    @ApiModelProperty(value = "透视度量列配置JSON")
    private String pivotMeasures;

    /**
     * 状态
     */
    @ApiModelProperty(value = "状态")
    private String status;
}
