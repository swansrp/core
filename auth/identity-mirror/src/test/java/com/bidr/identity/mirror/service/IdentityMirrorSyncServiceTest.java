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
import org.springframework.dao.DuplicateKeyException;

import java.io.Serializable;
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
 *     <li>腿 4~7：上游同业务键软删重建（customer_number／user_name／tenant_code）删旧插新；</li>
 *     <li>腿 8~13（T2.4a）：{@code applyUserScope} 单用户作用域写——新用户首写、老用户改名走 update、
 *         tenantItems=null 只写用户行、同登录名重建跟随、关系收缩只动本用户、定位不到返回 null。</li>
 *     <li>腿 14~20（修复轮 1／F1）：重建跟随清旧 id 孤儿关系、空列表删本用户关系（与 null 区分）、
 *         不写水位、并发撞唯一键重读走 update（重读仍空则原样抛）、customerNumber 空⇒零写返回 null、
 *         腿 11 补「旧行真被 deleteById」、userName=null 按 customerNumber 定位。</li>
 *     <li>腿 21~23（修复轮 2／C1）：关系行 insert 撞键吞掉不计数、不抛；租户行 upsert 撞键跳过租户写入
 *         但用户行与关系比对照常完成；入参 name 为 null 且无其它差异 ⇒ 零 UPDATE（不恒发空更名）。</li>
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
        // 🔴 UK 强制（对账单 #23）：模拟真库 uk_customer_number / uk_user_name——同键不同 id 的 insert 必炸
        Mockito.when(userRepo.insert(Mockito.any(IdmUserMirror.class))).thenAnswer(inv -> {
            IdmUserMirror row = inv.getArgument(0);
            for (IdmUserMirror other : users.values()) {
                if (row.getCustomerNumber() != null && row.getCustomerNumber().equals(other.getCustomerNumber())
                        && !row.getId().equals(other.getId())) {
                    throw new DuplicateKeyException("uk_customer_number: " + row.getCustomerNumber());
                }
                if (row.getUserName() != null && row.getUserName().equals(other.getUserName())
                        && !row.getId().equals(other.getId())) {
                    throw new DuplicateKeyException("uk_user_name: " + row.getUserName());
                }
            }
            users.put(row.getId(), row);
            return true;
        });
        Mockito.when(userRepo.updateById(Mockito.any(IdmUserMirror.class))).thenAnswer(inv -> {
            IdmUserMirror row = inv.getArgument(0);
            users.put(row.getId(), row);
            return true;
        });
        Mockito.when(userRepo.deleteById(Mockito.any(Serializable.class))).thenAnswer(inv -> {
            Long id = ((Number) inv.getArgument(0)).longValue();
            return users.remove(id) != null;
        });
        Mockito.when(userRepo.selectById(Mockito.any(Serializable.class))).thenAnswer(inv -> {
            Long id = ((Number) inv.getArgument(0)).longValue();
            return users.get(id);
        });
        Mockito.when(userRepo.findByUserName(Mockito.any())).thenAnswer(inv -> {
            String userName = inv.getArgument(0);
            if (userName == null || userName.isEmpty()) {
                return null;
            }
            for (IdmUserMirror row : users.values()) {
                if (userName.equals(row.getUserName())) {
                    return row;
                }
            }
            return null;
        });
        Mockito.when(userRepo.findByCustomerNumber(Mockito.any())).thenAnswer(inv -> {
            String customerNumber = inv.getArgument(0);
            if (customerNumber == null || customerNumber.isEmpty()) {
                return null;
            }
            for (IdmUserMirror row : users.values()) {
                if (customerNumber.equals(row.getCustomerNumber())) {
                    return row;
                }
            }
            return null;
        });

        Mockito.when(tenantRepo.listAll()).thenAnswer(inv -> new ArrayList<>(tenants.values()));
        // 🔴 UK 强制（对账单 #23）：模拟真库 uk_tenant_code
        Mockito.when(tenantRepo.insert(Mockito.any(IdmTenantMirror.class))).thenAnswer(inv -> {
            IdmTenantMirror row = inv.getArgument(0);
            for (IdmTenantMirror other : tenants.values()) {
                if (row.getTenantCode() != null && row.getTenantCode().equals(other.getTenantCode())
                        && !row.getId().equals(other.getId())) {
                    throw new DuplicateKeyException("uk_tenant_code: " + row.getTenantCode());
                }
            }
            tenants.put(row.getId(), row);
            return true;
        });
        Mockito.when(tenantRepo.updateById(Mockito.any(IdmTenantMirror.class))).thenAnswer(inv -> {
            IdmTenantMirror row = inv.getArgument(0);
            tenants.put(row.getId(), row);
            return true;
        });
        Mockito.when(tenantRepo.deleteById(Mockito.any(Serializable.class))).thenAnswer(inv -> {
            Long id = ((Number) inv.getArgument(0)).longValue();
            return tenants.remove(id) != null;
        });

        Mockito.when(userTenantRepo.listAll()).thenAnswer(inv -> new ArrayList<>(relations));
        Mockito.when(userTenantRepo.listByUserId(Mockito.any())).thenAnswer(inv -> {
            Long userId = inv.getArgument(0);
            if (userId == null) {
                return new ArrayList<IdmUserTenantMirror>();
            }
            List<IdmUserTenantMirror> hits = new ArrayList<>();
            for (IdmUserTenantMirror row : relations) {
                if (userId.equals(row.getUserId())) {
                    hits.add(row);
                }
            }
            return hits;
        });
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

    private static SnapshotRes.UserItem user(long id, String customerNumber, String userName, String name) {
        SnapshotRes.UserItem item = new SnapshotRes.UserItem();
        item.setId(id);
        item.setCustomerNumber(customerNumber);
        item.setUserName(userName);
        item.setName(name);
        return item;
    }

    private static IdmUserMirror userRow(long id, String customerNumber, String userName, String name) {
        IdmUserMirror row = new IdmUserMirror();
        row.setId(id);
        row.setCustomerNumber(customerNumber);
        row.setUserName(userName);
        row.setName(name);
        row.setValid("1");
        return row;
    }

    private static IdmTenantMirror tenantRow(long id, String code, String name) {
        IdmTenantMirror row = new IdmTenantMirror();
        row.setId(id);
        row.setTenantCode(code);
        row.setTenantName(name);
        row.setValid("1");
        return row;
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

    private static IdmUserTenantMirror relRow(long userId, long tenantId) {
        IdmUserTenantMirror row = new IdmUserTenantMirror();
        row.setUserId(userId);
        row.setTenantId(tenantId);
        row.setValid("1");
        return row;
    }

    private static SnapshotRes snapshot(long watermark) {
        SnapshotRes snapshot = new SnapshotRes();
        snapshot.setWatermark(watermark);
        snapshot.getUsers().add(user(1L, "000001", "bidr", "张三"));
        snapshot.getUsers().add(user(2L, "000002", "lisi", "李四"));
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
        Assert.assertEquals("T2.4a：登录名必须落库", "bidr", users.get(1L).getUserName());
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

    // ======================== 腿 4~6：上游软删重建（对账单 #23 方案 A） ========================
    // 场景：上游「停用=置 valid=0 行还在，再启用同 code=插新行（新 id）」——镜像保留行
    // （upsert-only 不删行）占住唯一键，同步必须删旧插新跟随上游新 id，否则永久撞键。

    /** 腿 4：用户同 customer_number 重建——镜像旧 id=5 让位，新 id=9 落库且仅一行 */
    @Test
    public void userRebuiltWithSameCustomerNumberFollowsNewUpstreamId() {
        IdmUserMirror stale = new IdmUserMirror();
        stale.setId(5L);
        stale.setCustomerNumber("000009");
        stale.setName("老王");
        stale.setValid("1");
        users.put(5L, stale);
        SnapshotRes snap = new SnapshotRes();
        snap.setWatermark(3000L);
        snap.getUsers().add(user(9L, "000009", "wangwu", "王五新"));
        Mockito.when(client.fetchSnapshot()).thenReturn(snap);

        IdentityMirrorSyncService.SyncResult result = service.synchronize();

        Assert.assertFalse("旧 id 行必须让位（删旧插新）", users.containsKey(5L));
        IdmUserMirror row = users.get(9L);
        Assert.assertNotNull("新 id 行必须落库", row);
        Assert.assertEquals("000009", row.getCustomerNumber());
        Assert.assertEquals("王五新", row.getName());
        Assert.assertEquals(1, result.usersInserted);
    }

    /** 腿 5：租户同 tenant_code 重建——与腿 4 同构 */
    @Test
    public void tenantRebuiltWithSameTenantCodeFollowsNewUpstreamId() {
        IdmTenantMirror stale = new IdmTenantMirror();
        stale.setId(5L);
        stale.setTenantCode("TOLD");
        stale.setTenantName("旧租户");
        stale.setValid("1");
        tenants.put(5L, stale);
        SnapshotRes snap = new SnapshotRes();
        snap.setWatermark(3000L);
        snap.getTenants().add(tenant(9L, "TOLD", "新租户名"));
        Mockito.when(client.fetchSnapshot()).thenReturn(snap);

        IdentityMirrorSyncService.SyncResult result = service.synchronize();

        Assert.assertFalse("旧 id 行必须让位（删旧插新）", tenants.containsKey(5L));
        IdmTenantMirror row = tenants.get(9L);
        Assert.assertNotNull("新 id 行必须落库", row);
        Assert.assertEquals("TOLD", row.getTenantCode());
        Assert.assertEquals("新租户名", row.getTenantName());
        Assert.assertEquals(1, result.tenantsInserted);
    }

    /** 腿 6：重建用户的历史关系自愈——旧 (5,10) 删、新 (9,10) 插，零孤儿残留 */
    @Test
    public void relationsOfRebuiltUserSelfHealToNewId() {
        IdmUserMirror stale = new IdmUserMirror();
        stale.setId(5L);
        stale.setCustomerNumber("000009");
        stale.setName("老王");
        stale.setValid("1");
        users.put(5L, stale);
        relations.add(relRow(5L, 10L));
        SnapshotRes snap = new SnapshotRes();
        snap.setWatermark(3000L);
        snap.getUsers().add(user(9L, "000009", "wangwu", "王五新"));
        snap.getTenants().add(tenant(10L, "T01", "租户一"));
        snap.getUserTenants().add(relation(9L, 10L));
        Mockito.when(client.fetchSnapshot()).thenReturn(snap);

        IdentityMirrorSyncService.SyncResult result = service.synchronize();

        Assert.assertEquals("旧关系 (5,10) 必须删", 1, result.relationsDeleted);
        Assert.assertEquals("新关系 (9,10) 必须插", 1, result.relationsInserted);
        Assert.assertEquals("零孤儿：只剩指向新 id 的一条", 1, relations.size());
        Assert.assertEquals(9L, relations.get(0).getUserId().longValue());
        Assert.assertEquals(10L, relations.get(0).getTenantId().longValue());
        Assert.assertFalse("旧用户行已让位", users.containsKey(5L));
        Assert.assertTrue("新用户行在位", users.containsKey(9L));
    }

    /** 腿 7（T2.4a）：用户同 user_name 重建（客户编码同时换了）——uk_user_name 也要跟随新 id */
    @Test
    public void userRebuiltWithSameUserNameFollowsNewUpstreamId() {
        users.put(5L, userRow(5L, "000005", "wangwu", "老王"));
        SnapshotRes snap = new SnapshotRes();
        snap.setWatermark(3000L);
        snap.getUsers().add(user(9L, "000009", "wangwu", "王五新"));
        Mockito.when(client.fetchSnapshot()).thenReturn(snap);

        IdentityMirrorSyncService.SyncResult result = service.synchronize();

        Assert.assertFalse("旧 id 行必须让位（uk_user_name 撞键前删旧）", users.containsKey(5L));
        Assert.assertEquals("登录名跟随新 id", "wangwu", users.get(9L).getUserName());
        Assert.assertEquals(1, result.usersInserted);
    }

    // ======================== 腿 8~13：applyUserScope 单用户作用域写（T2.4a 登录同步） ========================

    /**
     * 腿 8：新用户首写——用户行（含 userName）＋传入 tenantItems 时落租户行与本用户关系；
     * 作用域外的他人关系一行不动。
     */
    @Test
    public void applyUserScopeInsertsNewUserAndOwnRelations() {
        relations.add(relRow(30L, 10L));
        List<SnapshotRes.TenantItem> items = new ArrayList<>();
        items.add(tenant(10L, "T01", "租户一"));

        Long mirrorId = service.applyUserScope(20L, "000020", "newbie", "新人", items);

        Assert.assertEquals("返回生效用户 id＝上游 id", Long.valueOf(20L), mirrorId);
        IdmUserMirror row = users.get(20L);
        Assert.assertNotNull("新用户镜像行必须落库", row);
        Assert.assertEquals("000020", row.getCustomerNumber());
        Assert.assertEquals("newbie", row.getUserName());
        Assert.assertEquals("新人", row.getName());
        Assert.assertNotNull("tenantItems 非空必须落租户行", tenants.get(10L));
        Assert.assertEquals("本用户关系必须插、他人关系保留", 2, relations.size());
        boolean ownRelation = false;
        for (IdmUserTenantMirror relation : relations) {
            if (Long.valueOf(20L).equals(relation.getUserId())) {
                ownRelation = true;
                Assert.assertEquals(Long.valueOf(10L), relation.getTenantId());
                Assert.assertEquals("1", relation.getValid());
            }
        }
        Assert.assertTrue("本用户 (20,10) 关系缺失", ownRelation);
        Mockito.verify(userTenantRepo, Mockito.never())
                .deleteByUserAndTenant(Mockito.eq(Long.valueOf(30L)), Mockito.anyLong());
    }

    /** 腿 9：老用户改名/换登录名——走 update 不 insert，关系与租户无变化时零关系写 */
    @Test
    public void applyUserScopeUpdatesExistingUserWithoutRelationWrites() {
        users.put(20L, userRow(20L, "000020", "oldname", "旧名"));
        tenants.put(10L, tenantRow(10L, "T01", "租户一"));
        relations.add(relRow(20L, 10L));
        List<SnapshotRes.TenantItem> items = new ArrayList<>();
        items.add(tenant(10L, "T01", "租户一"));

        Long mirrorId = service.applyUserScope(20L, "000020", "newname", "新名", items);

        Assert.assertEquals(Long.valueOf(20L), mirrorId);
        Assert.assertEquals("newname", users.get(20L).getUserName());
        Assert.assertEquals("新名", users.get(20L).getName());
        Mockito.verify(userRepo, Mockito.never()).insert(Mockito.any(IdmUserMirror.class));
        Mockito.verify(userRepo, Mockito.times(1)).updateById(Mockito.any(IdmUserMirror.class));
        Mockito.verify(userTenantRepo, Mockito.never()).insert(Mockito.any(IdmUserTenantMirror.class));
        Mockito.verify(userTenantRepo, Mockito.never())
                .deleteByUserAndTenant(Mockito.anyLong(), Mockito.anyLong());
        Assert.assertEquals("关系无变化零关系写", 1, relations.size());
    }

    /** 腿 10：tenantItems＝null ⇒ 只写用户行，租户行与关系零写 */
    @Test
    public void applyUserScopeWithNullTenantsTouchesOnlyUserRow() {
        relations.add(relRow(20L, 10L));

        Long mirrorId = service.applyUserScope(20L, "000020", "newbie", "新人", null);

        Assert.assertEquals(Long.valueOf(20L), mirrorId);
        Assert.assertNotNull(users.get(20L));
        Mockito.verify(tenantRepo, Mockito.never()).insert(Mockito.any(IdmTenantMirror.class));
        Mockito.verify(tenantRepo, Mockito.never()).updateById(Mockito.any(IdmTenantMirror.class));
        Mockito.verify(userTenantRepo, Mockito.never()).insert(Mockito.any(IdmUserTenantMirror.class));
        Mockito.verify(userTenantRepo, Mockito.never())
                .deleteByUserAndTenant(Mockito.anyLong(), Mockito.anyLong());
        Mockito.verify(userTenantRepo, Mockito.never()).listByUserId(Mockito.any());
        Assert.assertEquals("既有关系不碰", 1, relations.size());
    }

    /** 腿 11：上游同 userName 重建（旧 id 残留）——删旧插新，结果仅新 id 一行 */
    @Test
    public void applyUserScopeFollowsRebuiltUserWithSameUserName() {
        users.put(5L, userRow(5L, "000009", "wangwu", "老王"));

        Long mirrorId = service.applyUserScope(9L, "000009", "wangwu", "王五新", null);

        Assert.assertEquals(Long.valueOf(9L), mirrorId);
        Assert.assertFalse("旧 id 行必须让位（uk_user_name/uk_customer_number 双键都不撞）", users.containsKey(5L));
        Assert.assertEquals("结果仅新 id 一行", 1, users.size());
        Assert.assertEquals("wangwu", users.get(9L).getUserName());
        Assert.assertEquals("王五新", users.get(9L).getName());
        // 判别力：旧行不是「留着没删」而是真删（守卫 !existing.containsKey 的腿）
        Mockito.verify(userRepo, Mockito.times(1)).deleteById(5L);
    }

    /** 腿 12：关系收缩——本用户旧关系不在本轮 tenantItems ⇒ 删；他人关系不动 */
    @Test
    public void applyUserScopeShrinksOnlyOwnRelations() {
        users.put(20L, userRow(20L, "000020", "bidr20", "本人"));
        tenants.put(10L, tenantRow(10L, "T01", "租户一"));
        tenants.put(11L, tenantRow(11L, "T02", "租户二"));
        relations.add(relRow(20L, 10L));
        relations.add(relRow(20L, 11L));
        relations.add(relRow(30L, 10L));
        List<SnapshotRes.TenantItem> items = new ArrayList<>();
        items.add(tenant(10L, "T01", "租户一"));

        Long mirrorId = service.applyUserScope(20L, "000020", "bidr20", "本人", items);

        Assert.assertEquals(Long.valueOf(20L), mirrorId);
        Assert.assertEquals("(20,11) 删、他人 (30,10) 保留", 2, relations.size());
        Mockito.verify(userTenantRepo, Mockito.times(1))
                .deleteByUserAndTenant(Long.valueOf(20L), Long.valueOf(11L));
        Mockito.verify(userTenantRepo, Mockito.never()).insert(Mockito.any(IdmUserTenantMirror.class));
        Mockito.verify(userRepo, Mockito.never()).updateById(Mockito.any(IdmUserMirror.class));
    }

    /** 腿 13：id 与两个业务键都定位不到 ⇒ 不写、返回 null（调用方 fail-closed） */
    @Test
    public void applyUserScopeReturnsNullWhenNothingLocatable() {
        Long mirrorId = service.applyUserScope(null, "999999", "ghost", "查无此人", null);

        Assert.assertNull("定位不到必须返回 null（fail-closed）", mirrorId);
        Assert.assertTrue("不得凭空造行", users.isEmpty());
        Mockito.verify(userRepo, Mockito.never()).insert(Mockito.any(IdmUserMirror.class));
        Mockito.verify(userRepo, Mockito.never()).updateById(Mockito.any(IdmUserMirror.class));
    }

    // ======================== 腿 14~20：修复轮 1（F1-1/F1-2/F1-3 的判别腿） ========================

    /**
     * 腿 14（F1-1）：重建跟随时旧 id 的残留关系必须被清掉——否则旧 id 关系行成永久孤儿
     * （本形态全量 job 可能不装配，没有兜底）。
     */
    @Test
    public void applyUserScopePurgesStaleRelationsOfRebuiltUser() {
        users.put(5L, userRow(5L, "000009", "wangwu", "老王"));
        relations.add(relRow(5L, 10L));
        relations.add(relRow(5L, 11L));
        relations.add(relRow(30L, 10L));

        Long mirrorId = service.applyUserScope(9L, "000009", "wangwu", "王五新", null);

        Assert.assertEquals(Long.valueOf(9L), mirrorId);
        Assert.assertFalse("旧用户行必须让位", users.containsKey(5L));
        Assert.assertEquals("旧 id 两条孤儿关系必须清掉、他人关系保留", 1, relations.size());
        Assert.assertEquals("保留的必须是别人的关系", Long.valueOf(30L), relations.get(0).getUserId());
        Mockito.verify(userTenantRepo, Mockito.times(1)).listByUserId(5L);
        Mockito.verify(userTenantRepo, Mockito.times(1)).deleteByUserAndTenant(5L, 10L);
        Mockito.verify(userTenantRepo, Mockito.times(1)).deleteByUserAndTenant(5L, 11L);
        Mockito.verify(userTenantRepo, Mockito.never())
                .deleteByUserAndTenant(Mockito.eq(Long.valueOf(30L)), Mockito.anyLong());
    }

    /** 腿 15（F1-1 对偶）：tenantItems＝空列表（≠ null）⇒ 本用户关系全删、他人关系不动 */
    @Test
    public void applyUserScopeWithEmptyTenantListDeletesAllOwnRelations() {
        users.put(20L, userRow(20L, "000020", "bidr20", "本人"));
        tenants.put(10L, tenantRow(10L, "T01", "租户一"));
        relations.add(relRow(20L, 10L));
        relations.add(relRow(20L, 11L));
        relations.add(relRow(30L, 10L));

        Long mirrorId = service.applyUserScope(20L, "000020", "bidr20", "本人", new ArrayList<>());

        Assert.assertEquals(Long.valueOf(20L), mirrorId);
        Assert.assertEquals("空列表＝本用户关系全删，他人关系保留", 1, relations.size());
        Assert.assertEquals("保留的必须是别人的关系", Long.valueOf(30L), relations.get(0).getUserId());
        Mockito.verify(userTenantRepo, Mockito.times(1)).deleteByUserAndTenant(20L, 10L);
        Mockito.verify(userTenantRepo, Mockito.times(1)).deleteByUserAndTenant(20L, 11L);
        Mockito.verify(userTenantRepo, Mockito.never())
                .deleteByUserAndTenant(Mockito.eq(Long.valueOf(30L)), Mockito.anyLong());
        Mockito.verify(userTenantRepo, Mockito.never()).insert(Mockito.any(IdmUserTenantMirror.class));
    }

    /** 腿 16（F1-1）：登录作用域写不写水位——stateRepo 全程零交互（水位只属全量同步） */
    @Test
    public void applyUserScopeNeverTouchesWatermark() {
        List<SnapshotRes.TenantItem> items = new ArrayList<>();
        items.add(tenant(10L, "T01", "租户一"));

        Long mirrorId = service.applyUserScope(20L, "000020", "newbie", "新人", items);

        Assert.assertEquals(Long.valueOf(20L), mirrorId);
        Mockito.verifyNoInteractions(stateRepo);
        Assert.assertTrue("不得写水位行", states.isEmpty());
    }

    /**
     * 腿 17（F1-2）：并发首登——insert 撞唯一键但重读拿得到行 ⇒ 走 update 腿、异常不穿出登录链。
     * 桩：首次 selectById 返回 null（进 insert 腿）、insert 必抛、第二次 selectById 返回「抢先插入」的行。
     */
    @Test
    public void applyUserScopeFallsBackToUpdateOnConcurrentInsertDuplicateKey() {
        Mockito.when(userRepo.selectById(20L)).thenReturn(null, userRow(20L, "000020", "raced", "抢先者"));
        Mockito.when(userRepo.insert(Mockito.any(IdmUserMirror.class)))
                .thenThrow(new DuplicateKeyException("uk_customer_number: 000020"));

        Long mirrorId = service.applyUserScope(20L, "000020", "newbie", "新人", null);

        Assert.assertEquals(Long.valueOf(20L), mirrorId);
        Mockito.verify(userRepo, Mockito.times(1)).insert(Mockito.any(IdmUserMirror.class));
        Mockito.verify(userRepo, Mockito.times(1)).updateById(Mockito.any(IdmUserMirror.class));
        Assert.assertEquals("重读到的行必须按本次入参补齐", "newbie", users.get(20L).getUserName());
        Assert.assertEquals("新人", users.get(20L).getName());
    }

    /** 腿 18（F1-2 后半）：撞唯一键且重读仍无行 ⇒ 原样抛出，不吞未知错误 */
    @Test
    public void applyUserScopeRethrowsDuplicateKeyWhenRereadStillMissing() {
        Mockito.when(userRepo.insert(Mockito.any(IdmUserMirror.class)))
                .thenThrow(new DuplicateKeyException("uk_customer_number: 000020"));

        try {
            service.applyUserScope(20L, "000020", "newbie", "新人", null);
            Assert.fail("重读仍无行必须原样抛出 DuplicateKeyException（不得吞未知错误）");
        } catch (DuplicateKeyException expected) {
            Assert.assertTrue("必须是原异常原样抛出", expected.getMessage().contains("000020"));
        }
        Mockito.verify(userRepo, Mockito.never()).updateById(Mockito.any(IdmUserMirror.class));
        Assert.assertTrue("失败腿不得留下半截用户行", users.isEmpty());
    }

    /** 腿 19（F1-3）：customerNumber 空/纯空白 ⇒ 返回 null 且四仓零交互（fail-closed 零写） */
    @Test
    public void applyUserScopeReturnsNullAndWritesNothingWhenCustomerNumberBlank() {
        List<SnapshotRes.TenantItem> items = new ArrayList<>();
        items.add(tenant(10L, "T01", "租户一"));

        Assert.assertNull("null customerNumber 必须返回 null", service.applyUserScope(20L, null, "newbie", "新人", items));
        Assert.assertNull("纯空白 customerNumber 必须返回 null", service.applyUserScope(20L, "   ", "newbie", "新人", items));

        Mockito.verifyNoInteractions(userRepo, tenantRepo, userTenantRepo, stateRepo);
        Assert.assertTrue("零写：不得造用户行", users.isEmpty());
        Assert.assertTrue("零写：不得造租户行", tenants.isEmpty());
        Assert.assertTrue("零写：不得造关系行", relations.isEmpty());
    }

    /** 腿 20（F1-6）：userName＝null 时仍按 customerNumber 定位到既有行，不得凭空造行 */
    @Test
    public void applyUserScopeLocatesByCustomerNumberWhenUserNameNull() {
        users.put(20L, userRow(20L, "000020", null, "本人"));

        Long mirrorId = service.applyUserScope(null, "000020", null, "本人", null);

        Assert.assertEquals("按 customerNumber 回落定位＝镜像行 id", Long.valueOf(20L), mirrorId);
        Assert.assertEquals("不得凭空造行", 1, users.size());
        Mockito.verify(userRepo, Mockito.times(1)).findByUserName(null);
        Mockito.verify(userRepo, Mockito.never()).insert(Mockito.any(IdmUserMirror.class));
        Mockito.verify(userRepo, Mockito.never()).updateById(Mockito.any(IdmUserMirror.class));
    }

    // ============ 腿 21~23：修复轮 2（C1-8 关系行撞键 / C1-1 租户行撞键 / C1-7 name=null 判差）============

    /**
     * 腿 21（C1-8）：关系行 insert 撞 (userId,tenantId) 唯一键 ⇒ 吞掉、不计数、不抛，登录链正常返回。
     * 桩：{@code listByUserId} 返回空（看似该插）＋ insert 一律抛（模拟另一并发请求已插好）。
     */
    @Test
    public void applyUserScopeSwallowsDuplicateKeyOnRelationInsert() {
        users.put(20L, userRow(20L, "000020", "bidr20", "本人"));
        tenants.put(10L, tenantRow(10L, "T01", "租户一"));
        Mockito.when(userTenantRepo.listByUserId(20L)).thenReturn(new ArrayList<>());
        Mockito.when(userTenantRepo.insert(Mockito.any(IdmUserTenantMirror.class)))
                .thenThrow(new DuplicateKeyException("uk_user_tenant: 000020-10"));
        List<SnapshotRes.TenantItem> items = new ArrayList<>();
        items.add(tenant(10L, "T01", "租户一"));

        Long mirrorId = service.applyUserScope(20L, "000020", "bidr20", "本人", items);

        Assert.assertEquals("撞键不得穿出登录链，仍返回上游 id", Long.valueOf(20L), mirrorId);
        Mockito.verify(userTenantRepo, Mockito.times(1)).insert(Mockito.any(IdmUserTenantMirror.class));
        Assert.assertTrue("撞键关系行不得落库（关系零新增）", relations.isEmpty());
        Mockito.verify(userTenantRepo, Mockito.never())
                .deleteByUserAndTenant(Mockito.anyLong(), Mockito.anyLong());
        Assert.assertEquals("撞键不得牵连租户行", 1, tenants.size());
        Assert.assertTrue("用户行照常就位", users.containsKey(20L));
    }

    /**
     * 腿 22（C1-1）：租户行 upsert 撞唯一键（两用户并发首登同一新租户）⇒ 本作用域吞掉并跳过租户 upsert，
     * 用户行与关系比对照常完成（不整段丢弃）。
     */
    @Test
    public void applyUserScopeSwallowsDuplicateKeyOnTenantInsert() {
        Mockito.when(tenantRepo.insert(Mockito.any(IdmTenantMirror.class)))
                .thenThrow(new DuplicateKeyException("PRIMARY: 10"));
        List<SnapshotRes.TenantItem> items = new ArrayList<>();
        items.add(tenant(10L, "T01", "租户一"));

        Long mirrorId = service.applyUserScope(20L, "000020", "newbie", "新人", items);

        Assert.assertEquals("租户行撞键不得穿出登录链", Long.valueOf(20L), mirrorId);
        Assert.assertNotNull("用户行必须照常落库（不整段丢弃）", users.get(20L));
        Mockito.verify(tenantRepo, Mockito.times(1)).insert(Mockito.any(IdmTenantMirror.class));
        Assert.assertTrue("撞键的租户行不得落库", tenants.isEmpty());
        Mockito.verify(userTenantRepo, Mockito.times(1)).insert(Mockito.any(IdmUserTenantMirror.class));
        Assert.assertEquals("关系照常写（租户撞键不牵连关系比对）", 1, relations.size());
    }

    /**
     * 腿 23（C1-7）：入参 name 为 null ⇒ 以镜像既有姓名参与判差，其余字段也没变 ⇒ 零 UPDATE
     * （修复前拿 null 判差恒为「有差异」，每次登录都发一条把 name 写空的 UPDATE）。
     */
    @Test
    public void applyUserScopeSkipsUpdateWhenOnlyIncomingNameIsNull() {
        users.put(20L, userRow(20L, "000020", "bidr20", "既有姓名"));

        Long mirrorId = service.applyUserScope(20L, "000020", "bidr20", null, null);

        Assert.assertEquals(Long.valueOf(20L), mirrorId);
        Assert.assertEquals("既有姓名不得被空值抹平", "既有姓名", users.get(20L).getName());
        Mockito.verify(userRepo, Mockito.never()).updateById(Mockito.any(IdmUserMirror.class));
        Mockito.verify(userRepo, Mockito.never()).insert(Mockito.any(IdmUserMirror.class));
    }
}
