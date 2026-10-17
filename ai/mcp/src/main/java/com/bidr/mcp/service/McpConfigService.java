package com.bidr.mcp.service;

import com.bidr.kernel.vo.common.KeyValueResVO;
import com.bidr.mcp.config.McpServerConfig;
import com.bidr.mcp.dao.entity.SysMcp;
import com.bidr.mcp.dao.repository.SysMcpService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Title: McpConfigService
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/7/30 22:37
 */
@Service
@RequiredArgsConstructor
public class McpConfigService {
    private final SysMcpService sysMcpService;
    private final McpServerConfig mcpServerConfig;


    public List<KeyValueResVO> getMcpEndpoint() {
        return sysMcpService.groupBy();
    }

    public List<SysMcp> getMcp(String endpoint, String type) {
        return sysMcpService.get(endpoint, type);
    }

    public void updateMcpDescription(SysMcp sysMcp) throws IllegalAccessException {
        mcpServerConfig.updateDescription(sysMcp);
    }
}