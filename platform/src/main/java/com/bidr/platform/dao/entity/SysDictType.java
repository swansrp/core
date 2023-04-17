package com.bidr.platform.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

 /**
 * Title: SysDictType
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/04/14 09:03
 */

/**
 * 字典类型表
 */
@ApiModel(value = "字典类型表")
@Data
@TableName(value = "sys_dict_type")
public class SysDictType {
    /**
     * 字典类型
     */
    @TableId(value = "dict_name", type = IdType.AUTO)
    @ApiModelProperty(value = "字典类型")
    private String dictName;

    /**
     * 字典显示名称
     */
    @TableField(value = "dict_title")
    @ApiModelProperty(value = "字典显示名称")
    private String dictTitle;

    /**
     * 只读
     */
    @TableField(value = "read_only")
    @ApiModelProperty(value = "只读")
    private String readOnly;

    public static final String COL_DICT_NAME = "dict_name";

    public static final String COL_DICT_TITLE = "dict_title";

    public static final String COL_READ_ONLY = "read_only";
}