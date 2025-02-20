package com.bidr.socket.io.utils;

import com.bidr.authorization.constants.token.TokenItem;
import com.bidr.kernel.utils.JsonUtil;
import com.corundumstudio.socketio.SocketIOClient;

/**
 * Title: ClientUtil
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/2/3 9:51
 */

public class ClientUtil {

    public static String get(SocketIOClient client, String itemKey) {
        return client.get(itemKey);
    }

    public static String get(SocketIOClient client, TokenItem item) {
        return client.get(item.name());
    }

    public static  <T> T get(SocketIOClient client, String itemKey, Class<?> collectionClass, Class<?>... elementClasses) {
        return JsonUtil.readJson(client.get(itemKey), collectionClass, elementClasses);
    }

    public static <T> T get(SocketIOClient client, TokenItem item, Class<?> collectionClass, Class<?>... elementClasses) {
        return JsonUtil.readJson(client.get(item.name()), collectionClass, elementClasses);
    }
}