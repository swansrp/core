package com.bidr.socket.io.vo.room;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * Title: UpdateChatRoomMemberReq
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2021/2/7 14:11
 * @description Project Name: Seed
 * @Package: com.srct.service.socketio.vo.room
 */
@Data
public class UpdateChatRoomMemberReq {
    @ApiModelProperty("房间id")
    private String roomId;
    @ApiModelProperty("新增成员列表")
    private List<String> addMemberList;
    @ApiModelProperty("删除成员列表")
    private List<String> removeMemberList;
}
