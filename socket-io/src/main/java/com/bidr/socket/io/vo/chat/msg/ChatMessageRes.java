package com.bidr.socket.io.vo.chat.msg;

import com.bidr.socket.io.bo.message.ChatMessage;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Title: ChatMessageRes
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2022/7/6 23:24
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ChatMessageRes extends ChatMessage {
    @ApiModelProperty("消息类型")
    private String topic;
}
