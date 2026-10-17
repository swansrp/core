package com.bidr.socket.io.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * Title: ChatRoom
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2024/10/31 11:00
 */

/**
 * 聊天室
 */
@ApiModel(description = "聊天室")
@Data
@TableName(value = "chat_room")
public class ChatRoom {
    /**
     * 聊天室id
     */
    @TableId
    @TableField(value = "id")
    @ApiModelProperty(value = "聊天室id")
    private String id;

    /**
     * 聊天室名
     */
    @TableField(value = "title")
    @ApiModelProperty(value = "聊天室名")
    private String title;

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

    /**
     * 有效性
     */
    @TableField(value = "`valid`")
    @ApiModelProperty(value = "有效性")
    private String valid;
}