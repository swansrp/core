package com.bidr.mcp.endpoint;

import com.bidr.mcp.anno.Param;
import com.bidr.mcp.anno.ToolMapping;
import com.bidr.mcp.config.IMcpServerEndpoint;
import com.bidr.mcp.config.McpResultConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Title: McpEndpoint
 * Description: 框架自带 demo 端点（默认不注册——类级注解未标，标上即生效），
 * 展示自有注解的最小形态。
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
    public Map<String, Object> getContent(@Param(description = "内容") String content) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", content);
        return result;
    }
}
