package com.bidr.socket.io.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.github.jeffreyning.mybatisplus.anno.MppMultiId;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * Title: ChatRoomMember
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2024/10/31 11:00
 */

/**
 * 聊天室成员
 */
@ApiModel(description = "聊天室成员")
@Data
@TableName(value = "chat_room_member")
public class ChatRoomMember {
    /**
     * 聊天室id
     */
    @MppMultiId
    @TableField(value = "room_id")
    @ApiModelProperty(value = "聊天室id")
    private String roomId;

    /**
     * 成员id
     */
    @MppMultiId
    @TableField(value = "user_id")
    @ApiModelProperty(value = "成员id")
    private String userId;

    /**
     * 聊天室内角色
     */
    @TableField(value = "`role`")
    @ApiModelProperty(value = "聊天室内角色")
    private String role;

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