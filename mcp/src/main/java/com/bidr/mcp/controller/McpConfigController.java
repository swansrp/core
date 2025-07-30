package com.bidr.mcp.controller;

import com.bidr.kernel.controller.BaseAdminController;
import com.bidr.kernel.service.PortalCommonService;
import com.bidr.mcp.dao.entity.SysMcp;
import com.bidr.mcp.dao.repository.SysMcpService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
public class McpConfigController extends BaseAdminController<SysMcp, SysMcp> {
    private final SysMcpService sysMcpService;

    @Override
    public PortalCommonService<SysMcp, SysMcp> getPortalService() {
        return super.getPortalService();
    }
}