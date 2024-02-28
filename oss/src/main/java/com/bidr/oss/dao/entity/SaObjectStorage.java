package com.bidr.oss.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.Date;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Data;

 /**
 * Title: SaObjectStorage
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/02/26 16:08
 */

/**
 * 对象存储记录
 */
@ApiModel(description = "对象存储记录")
@Data
@TableName(value = "sa_object_storage")
public class SaObjectStorage {
    /**
     * id
     */
    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "id")
    @NotNull(message = "id不能为null")
    private Long id;

    @TableField(value = "`key`")
    @ApiModelProperty(value = "")
    @Size(max = 500, message = "最大长度要小于 500")
    @NotBlank(message = "不能为空")
    private String key;

    /**
     * 文件名
     */
    @TableField(value = "`name`")
    @ApiModelProperty(value = "文件名")
    @Size(max = 100, message = "文件名最大长度要小于 100")
    @NotBlank(message = "文件名不能为空")
    private String name;

    /**
     * 地址
     */
    @TableField(value = "uri")
    @ApiModelProperty(value = "地址")
    @Size(max = 500, message = "地址最大长度要小于 500")
    @NotBlank(message = "地址不能为空")
    private String uri;

    /**
     * 文件大小
     */
    @TableField(value = "`size`")
    @ApiModelProperty(value = "文件大小")
    @NotNull(message = "文件大小不能为null")
    private Long size;

    /**
     * 文件存储类型
     */
    @TableField(value = "`type`")
    @ApiModelProperty(value = "文件存储类型")
    @Size(max = 10, message = "文件存储类型最大长度要小于 10")
    @NotBlank(message = "文件存储类型不能为空")
    private String type;

    /**
     * 创建时间
     */
    @TableField(value = "create_at")
    @ApiModelProperty(value = "创建时间")
    @NotNull(message = "创建时间不能为null")
    private Date createAt;

    /**
     * 更新时间
     */
    @TableField(value = "update_at")
    @ApiModelProperty(value = "更新时间")
    @NotNull(message = "更新时间不能为null")
    private Date updateAt;

    /**
     * 有效性
     */
    @TableField(value = "`valid`")
    @ApiModelProperty(value = "有效性")
    @Size(max = 1, message = "有效性最大长度要小于 1")
    @NotBlank(message = "有效性不能为空")
    private String valid;
}