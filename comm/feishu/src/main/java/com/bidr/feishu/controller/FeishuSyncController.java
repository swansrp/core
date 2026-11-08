package com.bidr.feishu.controller;

import com.bidr.feishu.config.OnFeishuBridgeForm;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import com.bidr.authorization.annotation.auth.Auth;
import com.bidr.authorization.annotation.auth.AuthAdmin;
import com.bidr.authorization.service.identity.OrgSyncApplier;
import com.bidr.authorization.identity.OrgSyncResult;
import com.bidr.feishu.bridge.FeishuIdentityBridge;
import com.bidr.feishu.dto.sync.FeishuSyncResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * Title: FeishuSyncController
 * Description: 飞书组织同步控制器（超管手动触发）。T1.1 棒5 起走"桥拉数、内核落库"两段编排——
 * {@link FeishuIdentityBridge#syncOrg()} 拉平纯数据＋内核 {@link OrgSyncApplier#apply} 全量替换落库，
 * 与定时链（core IdentitySyncJob）同一形态；原 FeishuSyncService.syncAll（拉数＋upsert 落库同体）
 * 退役。内核侧装配点 orgSyncApplier 是 IdentitySyncJob.runOnce 的同款编排（pack 不触内核写表
 * *Service，M-5 口径不变）。
 * 🔴 对外契约保持：POST /web/feishu/sync/all（AuthAdmin）→ FeishuSyncResult{deptCount, accountCount}
 * （计数含根部门行，与原链口径一致）。
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
@Conditional(OnFeishuBridgeForm.class)
public class FeishuSyncController {

    @Resource
    private FeishuIdentityBridge feishuIdentityBridge;

    @Resource
    private OrgSyncApplier orgSyncApplier;

    @Auth(AuthAdmin.class)
    @ApiOperation("全量同步飞书部门与用户")
    @PostMapping("/all")
    public FeishuSyncResult syncAll() {
        OrgSyncResult pulled = feishuIdentityBridge.syncOrg();
        orgSyncApplier.apply(pulled);
        FeishuSyncResult result = new FeishuSyncResult();
        result.setDeptCount(pulled.depts == null ? 0 : pulled.depts.size());
        result.setAccountCount(pulled.users == null ? 0 : pulled.users.size());
        log.info("手动同步飞书组织完成，部门 {} 个，用户 {} 个", result.getDeptCount(), result.getAccountCount());
        return result;
    }
}
