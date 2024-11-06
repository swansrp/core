package com.bidr.socket.io.vo.chat.msg;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Title: ChatRes
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/9/25 10:35
 * @description Project Name: customer-robot
 * @Package: com.bhfae.customer.robot.vo.robot.chat
 */
@Data
public class ChatRes {
    @ApiModelProperty(value = "问题id")
    private String msgId;
    @ApiModelProperty(value = "消息种类: 0:自身, 1:机器人, 2:人工客服")
    private String type;
    @ApiModelProperty(value = "优先级")
    private String priority;
    @ApiModelProperty(value = "用户id")
    private String userId;
    @ApiModelProperty(value = "头像")
    private String avatar;
    @ApiModelProperty(value = "消息类型")
    private String msgType;
    @ApiModelProperty(value = "消息内容")
    private String content;
    @ApiModelProperty(value = "时间戳")
    private long timestamp;
}
