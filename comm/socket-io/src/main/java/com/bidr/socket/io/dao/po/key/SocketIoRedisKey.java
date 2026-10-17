package com.bidr.socket.io.dao.po.key;

/**
 * Title: WebsocketConst
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/6/29 14:04
 * @description Project Name: Seed
 * @Package: com.srct.service.websocket.constant
 */
public class SocketIoRedisKey {
    private static final String SOCKET_IO_PREFIX = "ROBOT-CHAT:";

    /**
     * HASH
     */
    public static final String SOCKET_IO_ROOM_INFO = SOCKET_IO_PREFIX + "ROOM_INFO:";
    public static final String SOCKET_IO_MSG_INFO = SOCKET_IO_PREFIX + "MSG_INFO:";
    public static final String SOCKET_IO_USER_INFO = SOCKET_IO_PREFIX + "USER_INFO:";

    /**
     * SET
     */
    public static final String SOCKET_IO_SESSION_USER_LIST = SOCKET_IO_PREFIX + "SESSION:";
    public static final String SOCKET_IO_ROOM_USER_LIST = SOCKET_IO_PREFIX + "USER_IN_ROOM:";
    public static final String SOCKET_IO_USER_ROOM_LIST = SOCKET_IO_PREFIX + "ROOM_OF_USER:";

    /**
     * SORTED SET
     */
    public static final String SOCKET_IO_USER_MSG_LIST = SOCKET_IO_PREFIX + "MSG_OF_USER:";
    public static final String SOCKET_IO_UNREAD_MSG_LIST = SOCKET_IO_PREFIX + "UNREAD_MSG_OF_USER:";
    /**
     * PUB/SUB
     */
    private static final String SOCKET_IO_TOPIC_KEY = SOCKET_IO_PREFIX + "TOPIC:";
    public static final String SOCKET_IO_MSG_TOPIC_KEY = SOCKET_IO_TOPIC_KEY + "MSG";
}
