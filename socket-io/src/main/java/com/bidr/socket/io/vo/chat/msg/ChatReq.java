package com.bidr.socket.io.vo.chat.msg;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Title: ChatReq
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/9/25 10:29
 * @description Project Name: customer-robot
 * @Package: com.bhfae.customer.robot.vo.robot.chat
 */
@Data
public class ChatReq {
    @ApiModelProperty("消息id")
    private String msgId;
    @ApiModelProperty("消息类型")
    private String msgType;
    @ApiModelProperty("消息体")
    private String content;
    @ApiModelProperty("聊天室id")
    private String roomId;
    @ApiModelProperty("目标id")
    private String targetId;
    @ApiModelProperty("优先级")
    private String priority;
}
