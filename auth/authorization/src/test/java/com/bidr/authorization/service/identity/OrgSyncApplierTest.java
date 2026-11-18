package com.bidr.authorization.service.identity;

import com.bidr.authorization.dao.entity.AcAccount;
import com.bidr.authorization.dao.entity.AcDept;
import com.bidr.authorization.dao.repository.AcAccountService;
import com.bidr.authorization.dao.repository.AcDeptService;
import com.bidr.authorization.service.account.AccountInitService;
import com.bidr.authorization.identity.OrgSyncResult;
import com.bidr.kernel.constant.dict.common.ActiveStatusDict;
import com.bidr.kernel.constant.dict.common.BoolDict;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Title: OrgSyncApplierTest
 * Description: 组织同步落库器离线单测（T1.1 棒4）——编排与映射两条腿：全量替换（delete＋
 * saveBatch，事务内可回滚）＋建号漏斗接续（accountSyncUser）；空腿护栏；UserRow 状态字典到
 * employ_status/status 两列的映射。纯 Mockito 不碰真库。
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-11-01
 */
public class OrgSyncApplierTest {

    private AcDeptService acDeptService;
    private AcAccountService acAccountService;
    private AccountInitService accountInitService;
    private OrgSyncApplier applier;

    @Before
    public void setUp() {
        acDeptService = Mockito.mock(AcDeptService.class);
        acAccountService = Mockito.mock(AcAccountService.class);
        accountInitService = Mockito.mock(AccountInitService.class);
        applier = new OrgSyncApplier(acDeptService, acAccountService, accountInitService);
    }

    private static OrgSyncResult.DeptRow deptRow(String id, String pid, String name) {
        OrgSyncResult.DeptRow row = new OrgSyncResult.DeptRow();
        row.externalId = id;
        row.parentExternalId = pid;
        row.name = name;
        row.showOrder = 5;
        row.leader = "E100";
        return row;
    }

    private static OrgSyncResult.UserRow userRow(String id, String username, String status) {
        OrgSyncResult.UserRow row = new OrgSyncResult.UserRow();
        row.externalId = id;
        row.username = username;
        row.displayName = username + "-name";
        row.deptExternalId = "D001";
        row.email = username + "@bidr.com.cn";
        row.mobile = "13800000000";
        row.avatar = "/pic.png";
        row.status = status;
        return row;
    }

    /** 全量替换编排：部门/账号两腿 delete＋saveBatch（映射断言）＋建号漏斗接续一次 */
    @Test
    public void applyReplacesBothLegsThenRunsAccountFunnel() {
        OrgSyncResult result = new OrgSyncResult();
        result.depts = Collections.singletonList(deptRow("D001", "ROOT", "研发部"));
        result.users = Arrays.asList(userRow("E100", "zhangsan", "active"),
                userRow("E200", "lisi", "frozen"));

        applier.apply(result);

        Mockito.verify(acDeptService).delete(Mockito.any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        ArgumentCaptor<List<AcDept>> deptCaptor = ArgumentCaptor.forClass((Class) List.class);
        Mockito.verify(acDeptService).saveBatch(deptCaptor.capture());
        Assert.assertEquals(1, deptCaptor.getValue().size());
        AcDept dept = deptCaptor.getValue().get(0);
        Assert.assertEquals("D001", dept.getDeptId());
        Assert.assertEquals("ROOT", dept.getPid());
        Assert.assertEquals("研发部", dept.getName());
        Assert.assertEquals("E100", dept.getLeader());
        Assert.assertEquals(Integer.valueOf(5), dept.getShowOrder());
        Assert.assertEquals(ActiveStatusDict.ACTIVATE.getValue(), dept.getStatus());

        Mockito.verify(acAccountService).delete(Mockito.any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        ArgumentCaptor<List<AcAccount>> userCaptor = ArgumentCaptor.forClass((Class) List.class);
        Mockito.verify(acAccountService).saveBatch(userCaptor.capture());
        Assert.assertEquals(2, userCaptor.getValue().size());
        AcAccount active = userCaptor.getValue().get(0);
        Assert.assertEquals("E100", active.getId());
        Assert.assertEquals("zhangsan", active.getUserName());
        Assert.assertEquals("zhangsan-name", active.getName());
        Assert.assertEquals("D001", active.getDepartment());
        Assert.assertEquals("zhangsan@bidr.com.cn", active.getEmail());
        Assert.assertEquals("/pic.png", active.getPictureLink());
        Assert.assertEquals(BoolDict.YES.getValue(), active.getEmployStatus());
        Assert.assertEquals(ActiveStatusDict.ACTIVATE.getValue(), active.getStatus());
        // frozen 账号：在职腿 NO＋启用腿停用（selectActive 建号链不再捞起）
        AcAccount frozen = userCaptor.getValue().get(1);
        Assert.assertEquals(BoolDict.NO.getValue(), frozen.getEmployStatus());
        Assert.assertEquals(ActiveStatusDict.DEACTIVATE.getValue(), frozen.getStatus());

        Mockito.verify(accountInitService).accountSyncUser();
    }

    /** 空腿护栏：部门快照空 → 不 delete 部门（不清空既有组织树），账号腿照常、漏斗照常 */
    @Test
    public void applySkipsDeleteOnEmptyDeptSnapshot() {
        OrgSyncResult result = new OrgSyncResult();
        result.depts = Collections.emptyList();
        result.users = Collections.singletonList(userRow("E100", "zhangsan", "active"));

        applier.apply(result);

        Mockito.verify(acDeptService, Mockito.never()).delete(Mockito.any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        Mockito.verify(acDeptService, Mockito.never()).saveBatch(Mockito.anyList());
        Mockito.verify(acAccountService).delete(Mockito.any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        Mockito.verify(accountInitService).accountSyncUser();
    }

    /** null/双空快照：两腿都跳过（不清任何表），漏斗腿仍按原链语义执行（读库对齐） */
    @Test
    public void applyToleratesNullAndEmptySnapshotWithoutWipingTables() {
        applier.apply(null);
        Mockito.verify(acDeptService, Mockito.never()).delete(Mockito.any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        Mockito.verify(acAccountService, Mockito.never()).delete(Mockito.any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        Mockito.verify(accountInitService).accountSyncUser();

        OrgSyncResult empty = new OrgSyncResult();
        empty.depts = null;
        empty.users = null;
        applier.apply(empty);
        Mockito.verify(acDeptService, Mockito.never()).delete(Mockito.any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        Mockito.verify(acAccountService, Mockito.never()).delete(Mockito.any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        Mockito.verify(accountInitService, Mockito.times(2)).accountSyncUser();
    }
}
