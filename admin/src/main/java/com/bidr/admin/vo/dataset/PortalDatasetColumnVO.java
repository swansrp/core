package com.bidr.admin.vo.dataset;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * Title: PortalDatasetColumnVO
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/9/19 14:34
 */
@Data
public class PortalDatasetColumnVO {
    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value="")
    private Long id;

    @ApiModelProperty(value="关联的表格ID")
    private String tableId;

    @ApiModelProperty(value="表顺序")
    private Integer datasetOrder;

    @ApiModelProperty(value="关联表")
    private String datasetSql;

    @ApiModelProperty(value="表别名")
    private String datasetAlias;

    @ApiModelProperty(value="JOIN类型，主表可为空")
    private String joinType;

    @ApiModelProperty(value="ON 条件，主表可为空")
    private String joinCondition;

    @ApiModelProperty(value="备注")
    private String remark;
}
