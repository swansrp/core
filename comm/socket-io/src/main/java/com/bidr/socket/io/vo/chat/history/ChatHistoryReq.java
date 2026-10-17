package com.bidr.socket.io.vo.chat.history;

import com.bidr.kernel.vo.query.QueryReqVO;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

/**
 * Title: ChatHistoryReq
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2021/2/4 17:07
 * @description Project Name: Seed
 * @Package: com.srct.service.socketio.vo.chat.history
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ChatHistoryReq extends QueryReqVO {
    @ApiModelProperty("房间id")
    private String roomId;
    @ApiModelProperty("目标id")
    private String targetId;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date beforeAt;
}
