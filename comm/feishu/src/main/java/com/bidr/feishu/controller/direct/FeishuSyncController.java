package com.bidr.feishu.controller.direct;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import com.bidr.authorization.annotation.auth.Auth;
import com.bidr.authorization.annotation.auth.AuthAdmin;
import com.bidr.feishu.dto.sync.FeishuSyncResult;
import com.bidr.feishu.service.contact.FeishuSyncService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * Title: FeishuSyncController
 * Description: 飞书组织同步控制器（超管手动触发）
 * Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/01/01 00:00
 */
@Slf4j
@Api(tags = "系统基础 - 飞书组织同步")
@RestController("FeishuSyncController")
@RequestMapping(value = "/web/feishu/sync")
@ConditionalOnProperty(prefix = "feishu", name = "app-id")
@ConditionalOnExpression("'${feishu.direct:false}' == 'true'")
public class FeishuSyncController {

    @Resource
    private FeishuSyncService feishuSyncService;

    @Auth(AuthAdmin.class)
    @ApiOperation("全量同步飞书部门与用户")
    @PostMapping("/all")
    public FeishuSyncResult syncAll() {
        return feishuSyncService.syncAll();
    }
}
