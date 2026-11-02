package com.bidr.identity.mirror.service;

import com.bidr.identity.mirror.dao.entity.IdmMirrorState;
import com.bidr.identity.mirror.dao.entity.IdmTenantMirror;
import com.bidr.identity.mirror.dao.entity.IdmUserMirror;
import com.bidr.identity.mirror.dao.entity.IdmUserTenantMirror;
import com.bidr.identity.mirror.dao.repository.IdmMirrorStateService;
import com.bidr.identity.mirror.dao.repository.IdmTenantMirrorService;
import com.bidr.identity.mirror.dao.repository.IdmUserMirrorService;
import com.bidr.identity.mirror.dao.repository.IdmUserTenantMirrorService;
import com.bidr.identity.mirror.service.dto.SnapshotRes;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Title: IdentityMirrorSyncServiceTest
 * Description: 快照同步幂等单测（T2.3d）——纯 JUnit4＋Mockito 内存库，不起 Spring 不连库。
 * <ul>
 *     <li>腿 1（首拉落库）：空库拉首份快照 → 全量 insert＋水位写入；</li>
 *     <li>腿 2（幂等重放）：同一份快照再拉一次 → 四表<b>零写调用</b>（insert/updateById/delete
 *         全 never，「无差异跳过」分支吃掉一切）＋水位不重写；</li>
 *     <li>腿 3（差异面真实可写）：改名/撤关系/水位前移 → 只动差异行（证明腿 2 的零写是
 *         diff 判定结果而非"永远不写"的假幂等）。</li>
 * </ul>
 *
 * @author Sharp
 * @since 2026/11/03
 */
public class IdentityMirrorSyncServiceTest {

    // ======================== 内存世界（mock repo＝内存库） ========================

    private IdentitySnapshotClient client;
    private IdmUserMirrorService userRepo;
    private IdmTenantMirrorService tenantRepo;
    private IdmUserTenantMirrorService userTenantRepo;
    private IdmMirrorStateService stateRepo;

    private final Map<Long, IdmUserMirror> users = new LinkedHashMap<>();
    private final Map<Long, IdmTenantMirror> tenants = new LinkedHashMap<>();
    private final List<IdmUserTenantMirror> relations = new ArrayList<>();
    private final Map<String, IdmMirrorState> states = new LinkedHashMap<>();
    private final AtomicLong relationIdSeq = new AtomicLong();

    private IdentityMirrorSyncService service;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        client = Mockito.mock(IdentitySnapshotClient.class);
        userRepo = Mockito.mock(IdmUserMirrorService.class);
        tenantRepo = Mockito.mock(IdmTenantMirrorService.class);
        userTenantRepo = Mockito.mock(IdmUserTenantMirrorService.class);
        stateRepo = Mockito.mock(IdmMirrorStateService.class);

        Mockito.when(userRepo.listAll()).thenAnswer(inv -> new ArrayList<>(users.values()));
        Mockito.when(userRepo.insert(Mockito.any(IdmUserMirror.class))).thenAnswer(inv -> {
            IdmUserMirror row = inv.getArgument(0);
            users.put(row.getId(), row);
            return true;
        });
        Mockito.when(userRepo.updateById(Mockito.any(IdmUserMirror.class))).thenAnswer(inv -> {
            IdmUserMirror row = inv.getArgument(0);
            users.put(row.getId(), row);
            return true;
        });

        Mockito.when(tenantRepo.listAll()).thenAnswer(inv -> new ArrayList<>(tenants.values()));
        Mockito.when(tenantRepo.insert(Mockito.any(IdmTenantMirror.class))).thenAnswer(inv -> {
            IdmTenantMirror row = inv.getArgument(0);
            tenants.put(row.getId(), row);
            return true;
        });
        Mockito.when(tenantRepo.updateById(Mockito.any(IdmTenantMirror.class))).thenAnswer(inv -> {
            IdmTenantMirror row = inv.getArgument(0);
            tenants.put(row.getId(), row);
            return true;
        });

        Mockito.when(userTenantRepo.listAll()).thenAnswer(inv -> new ArrayList<>(relations));
        Mockito.when(userTenantRepo.insert(Mockito.any(IdmUserTenantMirror.class))).thenAnswer(inv -> {
            IdmUserTenantMirror row = inv.getArgument(0);
            row.setId(relationIdSeq.incrementAndGet());
            relations.add(row);
            return true;
        });
        Mockito.when(userTenantRepo.deleteByUserAndTenant(Mockito.anyLong(), Mockito.anyLong()))
                .thenAnswer(inv -> {
                    Long userId = inv.getArgument(0);
                    Long tenantId = inv.getArgument(1);
                    boolean removed = false;
                    for (Iterator<IdmUserTenantMirror> it = relations.iterator(); it.hasNext(); ) {
                        IdmUserTenantMirror row = it.next();
                        if (row.getUserId().equals(userId) && row.getTenantId().equals(tenantId)) {
                            it.remove();
                            removed = true;
                        }
                    }
                    return removed;
                });

        Mockito.when(stateRepo.getByKey(Mockito.anyString())).thenAnswer(inv -> states.get(inv.getArgument(0)));
        Mockito.when(stateRepo.insert(Mockito.any(IdmMirrorState.class))).thenAnswer(inv -> {
            IdmMirrorState row = inv.getArgument(0);
            states.put(row.getStateKey(), row);
            return true;
        });
        Mockito.when(stateRepo.updateById(Mockito.any(IdmMirrorState.class))).thenAnswer(inv -> {
            IdmMirrorState row = inv.getArgument(0);
            states.put(row.getStateKey(), row);
            return true;
        });

        service = new IdentityMirrorSyncService(client, userRepo, tenantRepo, userTenantRepo, stateRepo);
    }

    // ======================== 快照夹具 ========================

    private static SnapshotRes.UserItem user(long id, String customerNumber, String name) {
        SnapshotRes.UserItem item = new SnapshotRes.UserItem();
        item.setId(id);
        item.setCustomerNumber(customerNumber);
        item.setName(name);
        return item;
    }

    private static SnapshotRes.TenantItem tenant(long id, String code, String name) {
        SnapshotRes.TenantItem item = new SnapshotRes.TenantItem();
        item.setId(id);
        item.setTenantCode(code);
        item.setTenantName(name);
        return item;
    }

    private static SnapshotRes.UserTenantItem relation(long userId, long tenantId) {
        SnapshotRes.UserTenantItem item = new SnapshotRes.UserTenantItem();
        item.setUserId(userId);
        item.setTenantId(tenantId);
        return item;
    }

    private static SnapshotRes snapshot(long watermark) {
        SnapshotRes snapshot = new SnapshotRes();
        snapshot.setWatermark(watermark);
        snapshot.getUsers().add(user(1L, "000001", "张三"));
        snapshot.getUsers().add(user(2L, "000002", "李四"));
        snapshot.getTenants().add(tenant(10L, "T01", "租户一"));
        snapshot.getUserTenants().add(relation(1L, 10L));
        snapshot.getUserTenants().add(relation(2L, 10L));
        return snapshot;
    }

    // ======================== 腿 1：首拉落库 ========================

    @Test
    public void firstSyncInsertsEverythingAndWritesWatermark() {
        Mockito.when(client.fetchSnapshot()).thenReturn(snapshot(1000L));

        IdentityMirrorSyncService.SyncResult result = service.synchronize();

        Assert.assertEquals(2, result.usersInserted);
        Assert.assertEquals(0, result.usersUpdated);
        Assert.assertEquals(1, result.tenantsInserted);
        Assert.assertEquals(2, result.relationsInserted);
        Assert.assertEquals(0, result.relationsDeleted);
        Assert.assertTrue("首拉必须写水位", result.stateWritten);
        Assert.assertEquals(1000L, result.watermark);

        Assert.assertEquals("张三", users.get(1L).getName());
        Assert.assertEquals("000002", users.get(2L).getCustomerNumber());
        Assert.assertEquals("租户一", tenants.get(10L).getTenantName());
        Assert.assertEquals(2, relations.size());
        IdmMirrorState state = states.get(IdentityMirrorSyncService.STATE_KEY_SNAPSHOT);
        Assert.assertNotNull("idm_mirror_state 缺 identity-snapshot 行", state);
        Assert.assertEquals("1000", state.getStateValue());
    }

    // ======================== 腿 2：同快照重放零写调用（幂等） ========================

    @Test
    public void replayingSameSnapshotIssuesZeroWriteCalls() {
        Mockito.when(client.fetchSnapshot()).thenReturn(snapshot(1000L));
        service.synchronize();

        Mockito.clearInvocations(userRepo, tenantRepo, userTenantRepo, stateRepo);
        IdentityMirrorSyncService.SyncResult result = service.synchronize();

        Assert.assertEquals("同快照重放：用户零变更", 0, result.usersInserted + result.usersUpdated);
        Assert.assertEquals("同快照重放：租户零变更", 0, result.tenantsInserted + result.tenantsUpdated);
        Assert.assertEquals("同快照重放：关系零变更", 0, result.relationsInserted + result.relationsDeleted);
        Assert.assertFalse("同水位不得重写 state 行", result.stateWritten);

        Mockito.verify(userRepo, Mockito.never()).insert(Mockito.any(IdmUserMirror.class));
        Mockito.verify(userRepo, Mockito.never()).updateById(Mockito.any(IdmUserMirror.class));
        Mockito.verify(tenantRepo, Mockito.never()).insert(Mockito.any(IdmTenantMirror.class));
        Mockito.verify(tenantRepo, Mockito.never()).updateById(Mockito.any(IdmTenantMirror.class));
        Mockito.verify(userTenantRepo, Mockito.never()).insert(Mockito.any(IdmUserTenantMirror.class));
        Mockito.verify(userTenantRepo, Mockito.never()).deleteByUserAndTenant(Mockito.anyLong(), Mockito.anyLong());
        Mockito.verify(stateRepo, Mockito.never()).insert(Mockito.any(IdmMirrorState.class));
        Mockito.verify(stateRepo, Mockito.never()).updateById(Mockito.any(IdmMirrorState.class));
    }

    // ======================== 腿 3：差异面真实可写（幂等不是假死） ========================

    @Test
    public void changedSnapshotOnlyTouchesDiffRows() {
        Mockito.when(client.fetchSnapshot()).thenReturn(snapshot(1000L));
        service.synchronize();

        // 第二份快照：user1 改名、关系 (2,10) 上游撤销、水位前移
        SnapshotRes changed = snapshot(2000L);
        changed.getUsers().get(0).setName("张三丰");
        changed.getUserTenants().remove(1);
        Mockito.when(client.fetchSnapshot()).thenReturn(changed);

        IdentityMirrorSyncService.SyncResult result = service.synchronize();

        Assert.assertEquals("改名走 update 腿（不是删插）", 1, result.usersUpdated);
        Assert.assertEquals("改名的用户不得重复 insert", 0, result.usersInserted);
        Assert.assertEquals("撤销的关系必须删", 1, result.relationsDeleted);
        Assert.assertTrue("水位前移必须重写", result.stateWritten);

        Assert.assertEquals("张三丰", users.get(1L).getName());
        Mockito.verify(userRepo, Mockito.times(1)).updateById(Mockito.any(IdmUserMirror.class));
        Mockito.verify(userTenantRepo, Mockito.times(1)).deleteByUserAndTenant(2L, 10L);
        Assert.assertEquals("撤销后只剩一条关系", 1, relations.size());
        Assert.assertEquals("2000", states.get(IdentityMirrorSyncService.STATE_KEY_SNAPSHOT).getStateValue());
    }
}
