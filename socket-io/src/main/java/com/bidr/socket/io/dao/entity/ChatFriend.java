package com.bidr.socket.io.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * Title: ChatFriend
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2024/10/31 11:00
 */

/**
 * 聊天室好友
 */
@ApiModel(description = "聊天好友")
@Data
@TableName(value = "chat_friend")
public class ChatFriend {
    /**
     * 用户id
     */
    @TableField(value = "user_id")
    @ApiModelProperty(value = "用户id")
    private String userId;

    /**
     * 好友id
     */
    @TableField(value = "friend_id")
    @ApiModelProperty(value = "好友id")
    private String friendId;

    /**
     * 是否同意
     */
    @TableField(value = "confirm")
    @ApiModelProperty(value = "是否同意")
    private String confirm;

    /**
     * 处理时间
     */
    @TableField(value = "confirm_at")
    @ApiModelProperty(value = "处理时间")
    private Date confirmAt;

    /**
     * 创建时间
     */
    @TableField(value = "create_at")
    @ApiModelProperty(value = "创建时间")
    private Date createAt;

    /**
     * 更新时间
     */
    @TableField(value = "update_at")
    @ApiModelProperty(value = "更新时间")
    private Date updateAt;
}