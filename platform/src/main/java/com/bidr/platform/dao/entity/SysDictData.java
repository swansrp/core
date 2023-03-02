package com.bidr.platform.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

 /**
 * Title: SysDictData
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2022/12/30 10:23
 */
/**
    * 字典数据表
    */
@ApiModel(value="字典数据表")
@Data
@TableName(value = "sys_dict_data")
public class SysDictData {
    /**
     * 字典编码
     */
    @TableId(value = "dict_code", type = IdType.AUTO)
    @ApiModelProperty(value="字典编码")
    private Long dictCode;

    /**
     * 字典排序
     */
    @TableField(value = "dict_sort")
    @ApiModelProperty(value="字典排序")
    private Integer dictSort;

    /**
     * 字典标签
     */
    @TableField(value = "dict_label")
    @ApiModelProperty(value="字典标签")
    private String dictLabel;

    /**
     * 字典键值
     */
    @TableField(value = "dict_value")
    @ApiModelProperty(value="字典键值")
    private String dictValue;

    /**
     * 字典类型
     */
    @TableField(value = "dict_type")
    @ApiModelProperty(value="字典类型")
    private String dictType;

    /**
     * 样式属性（其他样式扩展）
     */
    @TableField(value = "css_class")
    @ApiModelProperty(value="样式属性（其他样式扩展）")
    private String cssClass;

    /**
     * 表格回显样式
     */
    @TableField(value = "list_class")
    @ApiModelProperty(value="表格回显样式")
    private String listClass;

    /**
     * 是否默认（Y是 N否）
     */
    @TableField(value = "is_default")
    @ApiModelProperty(value="是否默认（Y是 N否）")
    private String isDefault;

    /**
     * 状态（0正常 1停用）
     */
    @TableField(value = "`status`")
    @ApiModelProperty(value="状态（0正常 1停用）")
    private String status;

    /**
     * 创建者
     */
    @TableField(value = "create_by")
    @ApiModelProperty(value="创建者")
    private String createBy;

    /**
     * 创建时间
     */
    @TableField(value = "create_time")
    @ApiModelProperty(value="创建时间")
    private Date createTime;

    /**
     * 更新者
     */
    @TableField(value = "update_by")
    @ApiModelProperty(value="更新者")
    private String updateBy;

    /**
     * 更新时间
     */
    @TableField(value = "update_time")
    @ApiModelProperty(value="更新时间")
    private Date updateTime;

    /**
     * 备注
     */
    @TableField(value = "remark")
    @ApiModelProperty(value="备注")
    private String remark;

    public static final String COL_DICT_CODE = "dict_code";

    public static final String COL_DICT_SORT = "dict_sort";

    public static final String COL_DICT_LABEL = "dict_label";

    public static final String COL_DICT_VALUE = "dict_value";

    public static final String COL_DICT_TYPE = "dict_type";

    public static final String COL_CSS_CLASS = "css_class";

    public static final String COL_LIST_CLASS = "list_class";

    public static final String COL_IS_DEFAULT = "is_default";

    public static final String COL_STATUS = "status";

    public static final String COL_CREATE_BY = "create_by";

    public static final String COL_CREATE_TIME = "create_time";

    public static final String COL_UPDATE_BY = "update_by";

    public static final String COL_UPDATE_TIME = "update_time";

    public static final String COL_REMARK = "remark";
}
