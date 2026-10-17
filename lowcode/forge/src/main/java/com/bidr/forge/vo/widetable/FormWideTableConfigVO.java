package com.bidr.forge.vo.widetable;

import com.bidr.admin.config.PortalIdField;
import com.bidr.admin.config.PortalTextAreaField;
import com.bidr.admin.vo.BaseVO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 宽表收集配置 VO
 *
 * @author sharp
 */
@ApiModel(description = "宽表收集配置")
@Data
@EqualsAndHashCode(callSuper = true)
public class FormWideTableConfigVO extends BaseVO {
    /**
     * 主键 ID
     */
    @PortalIdField
    @ApiModelProperty(value = "主键 ID")
    private Long id;

    /**
     * 关联表单 ID
     */
    @ApiModelProperty(value = "关联表单 ID")
    private String formId;

    /**
     * 物理宽表名
     */
    @ApiModelProperty(value = "物理宽表名")
    private String tableName;

    /**
     * 配置名称
     */
    @ApiModelProperty(value = "配置名称")
    private String title;

    /**
     * 描述
     */
    @PortalTextAreaField
    @ApiModelProperty(value = "描述")
    private String description;

    /**
     * 状态: draft/active/inactive
     */
    @ApiModelProperty(value = "状态: draft/active/inactive")
    private String status;

    /**
     * 关联 Portal 表 ID
     */
    @ApiModelProperty(value = "关联 Portal 表 ID")
    private Long portalId;

    /**
     * 创建时间
     */
    @ApiModelProperty(value = "创建时间")
    private Date createAt;

    /**
     * 更新时间
     */
    @ApiModelProperty(value = "更新时间")
    private Date updateAt;
}
