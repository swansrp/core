package com.bidr.mcp.client;

import lombok.extern.slf4j.Slf4j;
import org.noear.solon.ai.mcp.client.McpClientProvider;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Title: McpClient
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/6/9 17:47
 */
@Slf4j
public class McpClient {

    public static void main(String[] args) {
        McpClientProvider clientProvider = McpClientProvider.builder().apiUrl("http://localhost:8080/mcp").build();
        Map<String, Object> parameter = new LinkedHashMap<>();
        parameter.put("userId", "sharp");
        String rst = clientProvider.callToolAsText("getUserInfo", parameter).getContent();
        log.info(rst);
    }
}
