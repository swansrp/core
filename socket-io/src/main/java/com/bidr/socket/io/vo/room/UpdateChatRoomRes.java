package com.bidr.socket.io.vo.room;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: UpdateChatRoomRes
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2021/2/5 11:28
 * @description Project Name: Seed
 * @Package: com.srct.service.socketio.vo.chat
 */
@Data
public class UpdateChatRoomRes {
    @ApiModelProperty("房间id")
    private String roomId;
    @ApiModelProperty("房间名")
    private String title;
    @ApiModelProperty("成员id列表")
    private List<ChatMemberVO> memberList = new ArrayList<>();
}
