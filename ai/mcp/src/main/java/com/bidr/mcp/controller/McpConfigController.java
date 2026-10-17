package com.bidr.mcp.controller;

import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.vo.common.KeyValueResVO;
import com.bidr.mcp.dao.entity.SysMcp;
import com.bidr.mcp.service.McpConfigService;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Title: McpConfigController
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/7/30 22:35
 */
@RequiredArgsConstructor
@RestController
@RequestMapping(value = "/web/sys/mcp/config")
public class McpConfigController {
    private final McpConfigService mcpConfigService;

    @ApiOperation(value = "获取MCP节点", notes = "获取MCP节点")
    @RequestMapping(value = "/endpoint", method = RequestMethod.GET)
    public List<KeyValueResVO> getMcpEndpoint() {
        return mcpConfigService.getMcpEndpoint();
    }

    @ApiOperation(value = "获取MCP列表", notes = "获取MCP列表")
    @RequestMapping(value = "", method = RequestMethod.GET)
    public List<SysMcp> getMcp(String endpoint, String type) {
        return mcpConfigService.getMcp(endpoint, type);
    }

    @ApiOperation(value = "修改mcp描述", notes = "修改mcp描述")
    @RequestMapping(value = "/description", method = RequestMethod.POST)
    public void updateMcpDescription(@RequestBody SysMcp sysMcp) throws IllegalAccessException {
        mcpConfigService.updateMcpDescription(sysMcp);
        Resp.notice("修改mcp描述成功");
    }
}