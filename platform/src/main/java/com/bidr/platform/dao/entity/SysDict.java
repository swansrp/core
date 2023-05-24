package com.bidr.platform.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.Date;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import lombok.Data;

 /**
 * Title: SysDict
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/24 13:33
 */

/**
 * 字典数据表
 */
@ApiModel(description = "字典数据表")
@Data
@TableName(value = "sys_dict")
public class SysDict {
    public static final String COL_TITLE = "title";
    /**
     * 字典编码
     */
    @TableId(value = "dict_id", type = IdType.AUTO)
    @ApiModelProperty(value = "字典编码")
    @Size(max = 50, message = "字典编码最大长度要小于 50")
    @NotBlank(message = "字典编码不能为空")
    private String dictId;

    /**
     * 字典父节点
     */
    @TableField(value = "dict_pid")
    @ApiModelProperty(value = "字典父节点")
    @Size(max = 50, message = "字典父节点最大长度要小于 50")
    private String dictPid;

    /**
     * 字典排序
     */
    @TableField(value = "dict_sort")
    @ApiModelProperty(value = "字典排序")
    private Integer dictSort;

    /**
     * 字典类型
     */
    @TableField(value = "dict_name")
    @ApiModelProperty(value = "字典类型")
    @Size(max = 100, message = "字典类型最大长度要小于 100")
    @NotBlank(message = "字典类型不能为空")
    private String dictName;

    /**
     * 字典显示名称
     */
    @TableField(value = "dict_title")
    @ApiModelProperty(value = "字典显示名称")
    @Size(max = 100, message = "字典显示名称最大长度要小于 100")
    @NotBlank(message = "字典显示名称不能为空")
    private String dictTitle;

    /**
     * 字典项名称
     */
    @TableField(value = "dict_item")
    @ApiModelProperty(value = "字典项名称")
    @Size(max = 100, message = "字典项名称最大长度要小于 100")
    private String dictItem;

    /**
     * 字典键值
     */
    @TableField(value = "dict_value")
    @ApiModelProperty(value = "字典键值")
    @Size(max = 100, message = "字典键值最大长度要小于 100")
    @NotBlank(message = "字典键值不能为空")
    private String dictValue;

    /**
     * 字典标签
     */
    @TableField(value = "dict_label")
    @ApiModelProperty(value = "字典标签")
    @Size(max = 100, message = "字典标签最大长度要小于 100")
    @NotBlank(message = "字典标签不能为空")
    private String dictLabel;

    /**
     * 是否默认（1是 0否）
     */
    @TableField(value = "is_default")
    @ApiModelProperty(value = "是否默认（1是 0否）")
    @Size(max = 1, message = "是否默认（1是 0否）最大长度要小于 1")
    private String isDefault;

    /**
     * 状态（0正常 1停用）
     */
    @TableField(value = "`status`")
    @ApiModelProperty(value = "状态（0正常 1停用）")
    @Size(max = 1, message = "状态（0正常 1停用）最大长度要小于 1")
    @NotBlank(message = "状态（0正常 1停用）不能为空")
    private String status;

    /**
     * 是否显示
     */
    @TableField(value = "`show`")
    @ApiModelProperty(value = "是否显示")
    @Size(max = 1, message = "是否显示最大长度要小于 1")
    @NotBlank(message = "是否显示不能为空")
    private String show;

    /**
     * 只读
     */
    @TableField(value = "read_only")
    @ApiModelProperty(value = "只读")
    @Size(max = 1, message = "只读最大长度要小于 1")
    @NotBlank(message = "只读不能为空")
    private String readOnly;

    /**
     * 创建者
     */
    @TableField(value = "create_by")
    @ApiModelProperty(value = "创建者")
    private Long createBy;

    /**
     * 创建时间
     */
    @TableField(value = "create_time")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /**
     * 更新者
     */
    @TableField(value = "update_by")
    @ApiModelProperty(value = "更新者")
    private Long updateBy;

    /**
     * 更新时间
     */
    @TableField(value = "update_time")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /**
     * 备注
     */
    @TableField(value = "remark")
    @ApiModelProperty(value = "备注")
    @Size(max = 500, message = "备注最大长度要小于 500")
    private String remark;

    public static final String COL_DICT_ID = "dict_id";

    public static final String COL_DICT_PID = "dict_pid";

    public static final String COL_DICT_SORT = "dict_sort";

    public static final String COL_DICT_NAME = "dict_name";

    public static final String COL_DICT_TITLE = "dict_title";

    public static final String COL_DICT_ITEM = "dict_item";

    public static final String COL_DICT_VALUE = "dict_value";

    public static final String COL_DICT_LABEL = "dict_label";

    public static final String COL_IS_DEFAULT = "is_default";

    public static final String COL_STATUS = "status";

    public static final String COL_SHOW = "show";

    public static final String COL_READ_ONLY = "read_only";

    public static final String COL_CREATE_BY = "create_by";

    public static final String COL_CREATE_TIME = "create_time";

    public static final String COL_UPDATE_BY = "update_by";

    public static final String COL_UPDATE_TIME = "update_time";

    public static final String COL_REMARK = "remark";
}
