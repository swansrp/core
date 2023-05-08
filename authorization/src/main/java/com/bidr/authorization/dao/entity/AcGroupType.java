package com.bidr.authorization.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import lombok.Data;

 /**
 * Title: AcGroupType
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/05/08 09:51
 */

/**
 * 组类型
 */
@ApiModel(description = "组类型")
@Data
@TableName(value = "ac_group_type")
public class AcGroupType {
    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "")
    @Size(max = 50, message = "最大长度要小于 50")
    @NotBlank(message = "不能为空")
    private String id;

    @TableField(value = "`name`")
    @ApiModelProperty(value = "")
    @Size(max = 50, message = "最大长度要小于 50")
    @NotBlank(message = "不能为空")
    private String name;

    public static final String COL_ID = "id";

    public static final String COL_NAME = "name";
}