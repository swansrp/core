package com.bidr.forge.vo.form;

import com.bidr.admin.config.PortalIdField;
import com.bidr.admin.vo.BaseVO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 属性分组实例表 VO
 *
 * @author sharp
 */
@ApiModel(description = "属性分组实例表")
@Data
@EqualsAndHashCode(callSuper = true)
public class FormDataGroupInstanceVO extends BaseVO {
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
     * 分组配置 ID
     */
    @ApiModelProperty(value = "分组配置 ID")
    private Long groupId;

    /**
     * 行索引（多组子表场景）
     */
    @ApiModelProperty(value = "行索引（多组子表场景）")
    private Integer rowIndex;
}
