package com.bidr.socket.io.dao.po.msg;

import com.bidr.socket.io.bo.message.ChatMessage;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.data.mongodb.core.index.Indexed;

import java.util.Date;

/**
 * Title: TopicChatMessage
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/9/24 13:13
 * @description Project Name: customer-robot
 * @Package: com.bhfae.customer.robot.dto.chat
 */
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class TopicChatMessage extends ChatMessage {
    @ApiModelProperty("消息类型")
    private String topic;
    @Indexed
    @ApiModelProperty("已读")
    //@PortalDict(dict = BoolDict.class)
    private String delivered;
    @Indexed(expireAfterSeconds = 0)
    private Date expireTime;
}
