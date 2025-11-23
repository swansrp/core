package com.bidr.forge.vo.matrix;


import com.bidr.admin.config.PortalDictField;
import com.bidr.admin.config.PortalIdField;
import com.bidr.admin.config.PortalNameField;
import com.bidr.admin.config.PortalOrderField;
import com.bidr.admin.vo.BaseVO;
import com.bidr.forge.constant.dict.MatrixChangeTypeDict;
import com.bidr.kernel.constant.dict.common.BoolDict;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 矩阵表结构变更日志 VO
 *
 * @author sharp
 * @since 2025-11-21
 */
@ApiModel(description = "矩阵表结构变更日志VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class SysMatrixChangeLogVO extends BaseVO {

    /**
     * 主键ID
     */
    @PortalIdField
    @ApiModelProperty(value = "主键ID")
    private Long id;

    /**
     * 矩阵ID
     */
    @ApiModelProperty(value = "矩阵ID")
    private Long matrixId;

    /**
     * 版本号
     */
    @ApiModelProperty(value = "版本号")
    private Integer version;

    /**
     * 变更类型：1-创建表，2-添加字段，3-调整字段顺序，4-添加索引，5-删除索引
     */
    @PortalNameField
    @PortalDictField(MatrixChangeTypeDict.class)
    @ApiModelProperty(value = "变更类型：1-创建表，2-添加字段，3-调整字段顺序，4-添加索引，5-删除索引")
    private String changeType;

    /**
     * 变更类型名称
     */
    @ApiModelProperty(value = "变更类型名称")
    private String changeTypeName;

    /**
     * 变更描述
     */
    @ApiModelProperty(value = "变更描述")
    private String changeDesc;

    /**
     * 执行的DDL语句
     */
    @ApiModelProperty(value = "执行的DDL语句")
    private String ddlStatement;

    /**
     * 影响的字段名
     */
    @ApiModelProperty(value = "影响的字段名")
    private String affectedColumn;

    /**
     * 执行状态：0-失败，1-成功
     */
    @PortalDictField(BoolDict.class)
    @ApiModelProperty(value = "成功执行")
    private String executeStatus;

    /**
     * 执行状态名称
     */
    @ApiModelProperty(value = "执行状态名称")
    private String executeStatusName;

    /**
     * 错误信息
     */
    @ApiModelProperty(value = "错误信息")
    private String errorMsg;

    /**
     * 排序
     */
    @PortalOrderField
    @ApiModelProperty(value = "排序")
    private Integer sort;
}
