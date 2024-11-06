package com.bidr.socket.io.bo.message;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Title: ChatMessage
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2024/10/31 10:44
 */

@Data
@Document("chat_message")
public class ChatMessage {
    @Id
    @Indexed
    @ApiModelProperty("id")
    private String msgId;
    @Indexed
    @ApiModelProperty("发送msgId")
    private String originalMsgId;
    @ApiModelProperty("sessionId")
    private String sessionId;
    @Indexed
    @ApiModelProperty("房间id")
    private String roomId;
    @Indexed
    @ApiModelProperty("目标id")
    private String targetId;
    @ApiModelProperty("用户id")
    private String userId;
    @ApiModelProperty("用户头像")
    private String avatar;
    //@PortalDict(dict = MessageTypeDict.class)
    @ApiModelProperty("消息类型 MessageTypeDict")
    private String msgType;
    @ApiModelProperty("消息内容")
    private String content;
    @ApiModelProperty("发送时间戳")
    private long timestamp;
    @ApiModelProperty("优先级")
    private String priority;
}
