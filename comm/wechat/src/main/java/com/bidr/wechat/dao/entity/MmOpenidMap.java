package com.bidr.wechat.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.github.jeffreyning.mybatisplus.anno.MppMultiId;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import lombok.Data;

/**
 * 微信公众号账号对应关系
 */
@ApiModel(description = "微信公众号账号对应关系")
@Data
@TableName(value = "mm_openid_map")
public class MmOpenidMap {
    /**
     * openId
     */
    @MppMultiId
    @TableField(value = "open_id")
    @ApiModelProperty(value = "openId")
    @Size(max = 50, message = "openId最大长度要小于 50")
    @NotBlank(message = "openId不能为空")
    private String openId;

    /**
     * 开放平台id
     */
    @MppMultiId
    @TableField(value = "union_id")
    @ApiModelProperty(value = "开放平台id")
    @Size(max = 50, message = "开放平台id最大长度要小于 50")
    @NotBlank(message = "开放平台id不能为空")
    private String unionId;

    /**
     * 手机号
     */
    @TableField(value = "phone")
    @ApiModelProperty(value = "手机号")
    @Size(max = 20, message = "手机号最大长度要小于 20")
    private String phone;

    /**
     * 昵称
     */
    @TableField(value = "nick_name")
    @ApiModelProperty(value = "昵称")
    @Size(max = 50, message = "昵称最大长度要小于 50")
    private String nickName;

    /**
     * 头像地址
     */
    @TableField(value = "avatar")
    @ApiModelProperty(value = "头像地址")
    @Size(max = 200, message = "头像地址最大长度要小于 200")
    private String avatar;
}