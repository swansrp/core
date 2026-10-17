package com.bidr.mcp.endpoint;

import com.bidr.mcp.config.IMcpServerEndpoint;
import com.bidr.mcp.config.McpResultConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.noear.solon.ai.annotation.ToolMapping;
import org.noear.solon.annotation.Param;
import org.springframework.stereotype.Component;

/**
 * Title: McpEndpoint
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/7/30 20:06
 */

@Slf4j
@Component
@RequiredArgsConstructor
//@McpServerEndpoint(name = "测试MCP", sseEndpoint = "/mcp")
public class McpEndpoint implements IMcpServerEndpoint {


    @ToolMapping(description = "返回输入的内容", resultConverter = McpResultConverter.class)
    public String getContent(@Param(description = "内容") String content) {
        return content;
    }
}