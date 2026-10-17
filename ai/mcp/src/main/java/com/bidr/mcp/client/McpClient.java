package com.bidr.mcp.client;

import lombok.extern.slf4j.Slf4j;
import org.noear.solon.ai.chat.tool.FunctionTool;
import org.noear.solon.ai.mcp.client.McpClientProvider;

import java.util.Collection;
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
        McpClientProvider clientProvider = McpClientProvider.builder().apiUrl("http://localhost:8000/mcp").build();
        Collection<FunctionTool> tools = clientProvider.getTools();
        Map<String, Object> parameter = new LinkedHashMap<>();
        parameter.put("content", "sharp");
        String rst = clientProvider.callToolAsText("getContnet", parameter).getContent();
        log.info(rst);
    }
}
