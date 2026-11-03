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
import com.bidr.kernel.constant.CommonConst;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Title: IdentityMirrorSyncService
 * Description: 身份镜像同步服务（T2.3d）——快照 → 镜像四表的<b>幂等全量 upsert</b>：
 * <ul>
 *     <li>用户/租户：按 id upsert（insert or「有差异才」updateById——<b>无差异跳过</b>，不发 SQL）；</li>
 *     <li>用户-租户关系：按 (userId,tenantId) <b>全量比对增删</b>（上游已不存在的关系行删除）；</li>
 *     <li>水位：{@code idm_mirror_state} key={@value #STATE_KEY_SNAPSHOT}，
 *         value＝本快照 watermark——<b>水位无变化不写</b>（同快照重放零 SQL 的最后一环）。</li>
 * </ul>
 * 幂等语义：同一份快照连拉两次，第二次对四表零写入（全走「无差异跳过」分支）——单测钉死。
 * 全量快照无增量语义，watermark 仅作「上次成功拉到的快照生成时刻」对账锚点（排障面），
 * 不用于增量裁剪；上游撤销用户/租户行不镜像删除（upsert-only），关系撤销随全量比对删除。
 * 上游同业务键软删重建（停用后再启用＝插新行新 id）时镜像<b>删旧插新跟随新 id</b>
 * （对账单 #23 方案 A，见 {@link #followRebuiltRow}）。
 *
 * @author Sharp
 * @since 2026/11/03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdentityMirrorSyncService {

    /** 同步水位状态键（idm_mirror_state.state_key） */
    public static final String STATE_KEY_SNAPSHOT = "identity-snapshot";

    private final IdentitySnapshotClient identitySnapshotClient;
    private final IdmUserMirrorService userMirrorService;
    private final IdmTenantMirrorService tenantMirrorService;
    private final IdmUserTenantMirrorService userTenantMirrorService;
    private final IdmMirrorStateService mirrorStateService;

    /**
     * 拉取并落库一份快照。
     *
     * @return 变更计数（各面 inserted/updated/deleted＋水位是否写入）——调用方记日志/断言用
     * @throws RuntimeException 快照拉取失败（配置缺失/HTTP 失败）向上抛，由 job 兜住
     */
    public SyncResult synchronize() {
        SnapshotRes snapshot = identitySnapshotClient.fetchSnapshot();
        Date now = new Date();
        SyncResult result = new SyncResult();
        int[] users = syncUsers(snapshot.getUsers(), now);
        result.usersInserted = users[0];
        result.usersUpdated = users[1];
        int[] tenants = syncTenants(snapshot.getTenants(), now);
        result.tenantsInserted = tenants[0];
        result.tenantsUpdated = tenants[1];
        int[] relations = syncUserTenants(snapshot.getUserTenants(), now);
        result.relationsInserted = relations[0];
        result.relationsDeleted = relations[1];
        result.stateWritten = writeWatermark(snapshot.getWatermark(), now);
        result.watermark = snapshot.getWatermark();
        log.info("[身份镜像]同步完成: users +{}/~{}, tenants +{}/~{}, relations +{}/-{}, watermark={}{}",
                result.usersInserted, result.usersUpdated, result.tenantsInserted, result.tenantsUpdated,
                result.relationsInserted, result.relationsDeleted, result.watermark,
                result.stateWritten ? "" : "（无变化未写水位）");
        return result;
    }

    /** 用户按 id upsert：insert miss 行、有差异才 update、无差异跳过（返回 {inserted, updated}） */
    private int[] syncUsers(List<SnapshotRes.UserItem> items, Date now) {
        Map<Long, IdmUserMirror> existing = new HashMap<>();
        Map<String, IdmUserMirror> existingByCustomerNumber = new HashMap<>();
        for (IdmUserMirror row : userMirrorService.listAll()) {
            existing.put(row.getId(), row);
            if (row.getCustomerNumber() != null) {
                existingByCustomerNumber.put(row.getCustomerNumber(), row);
            }
        }
        int inserted = 0;
        int updated = 0;
        if (items != null) {
            for (SnapshotRes.UserItem item : items) {
                if (item == null || item.getId() == null) {
                    continue;
                }
                IdmUserMirror current = existing.get(item.getId());
                if (current == null) {
                    followRebuiltRow(existing, existingByCustomerNumber.get(item.getCustomerNumber()),
                            item.getId(), item.getCustomerNumber(), "customerNumber",
                            IdmUserMirror::getId, staleId -> userMirrorService.deleteById(staleId));
                    IdmUserMirror row = new IdmUserMirror();
                    row.setId(item.getId());
                    row.setCustomerNumber(item.getCustomerNumber());
                    row.setName(item.getName());
                    row.setValid(CommonConst.YES);
                    row.setCreateAt(now);
                    row.setUpdateAt(now);
                    userMirrorService.insert(row);
                    existing.put(row.getId(), row);
                    if (row.getCustomerNumber() != null) {
                        existingByCustomerNumber.put(row.getCustomerNumber(), row);
                    }
                    inserted++;
                } else if (userChanged(current, item)) {
                    current.setCustomerNumber(item.getCustomerNumber());
                    current.setName(item.getName());
                    current.setUpdateAt(now);
                    userMirrorService.updateById(current);
                    updated++;
                }
            }
        }
        return new int[]{inserted, updated};
    }

    /** 租户按 id upsert（同 {@link #syncUsers} 语义，返回 {inserted, updated}） */
    private int[] syncTenants(List<SnapshotRes.TenantItem> items, Date now) {
        Map<Long, IdmTenantMirror> existing = new HashMap<>();
        Map<String, IdmTenantMirror> existingByTenantCode = new HashMap<>();
        for (IdmTenantMirror row : tenantMirrorService.listAll()) {
            existing.put(row.getId(), row);
            if (row.getTenantCode() != null) {
                existingByTenantCode.put(row.getTenantCode(), row);
            }
        }
        int inserted = 0;
        int updated = 0;
        if (items != null) {
            for (SnapshotRes.TenantItem item : items) {
                if (item == null || item.getId() == null) {
                    continue;
                }
                IdmTenantMirror current = existing.get(item.getId());
                if (current == null) {
                    followRebuiltRow(existing, existingByTenantCode.get(item.getTenantCode()),
                            item.getId(), item.getTenantCode(), "tenantCode",
                            IdmTenantMirror::getId, staleId -> tenantMirrorService.deleteById(staleId));
                    IdmTenantMirror row = new IdmTenantMirror();
                    row.setId(item.getId());
                    row.setTenantCode(item.getTenantCode());
                    row.setTenantName(item.getTenantName());
                    row.setValid(CommonConst.YES);
                    row.setCreateAt(now);
                    row.setUpdateAt(now);
                    tenantMirrorService.insert(row);
                    existing.put(row.getId(), row);
                    if (row.getTenantCode() != null) {
                        existingByTenantCode.put(row.getTenantCode(), row);
                    }
                    inserted++;
                } else if (tenantChanged(current, item)) {
                    current.setTenantCode(item.getTenantCode());
                    current.setTenantName(item.getTenantName());
                    current.setUpdateAt(now);
                    tenantMirrorService.updateById(current);
                    updated++;
                }
            }
        }
        return new int[]{inserted, updated};
    }

    /**
     * 上游软删重建降级（对账单 #23 方案 A，review 棒3 I-1）：上游生命周期为「停用＝置 valid=0
     * 行还在，再启用同业务键＝<b>插新行（新 id）</b>」（{@code AcTenantSchema}/{@code AcUserSchema}
     * 业务键故意普通 KEY）——镜像 upsert-only 不删行，保留的旧 id 行会占住唯一键
     * （uk_customer_number/uk_tenant_code）挡住新行，撞键即同步永久失败。
     * 此处按业务键找到该保留行，<b>删旧插新</b>让镜像跟随上游新 id——恢复不变式
     * 「镜像行＝上游当前 valid 实体、id 逐字等于上游 id」；绑定键（customer_number）语义不变，
     * 关系镜像随后续全量比对自愈（删旧 id 关系、插新 id 关系）。
     * 🔴 删旧与插新非原子（同步无事务包裹）：若删后插前崩溃，下一周期全量快照自愈。
     * 上游同业务键出现多个 valid 行属脏数据，按快照序 last-write-wins（每次 warn 留痕）。
     */
    private <T> void followRebuiltRow(Map<Long, T> existing, T stale, Long incomingId, String keyValue,
                                      String keyName, java.util.function.Function<T, Long> idGetter,
                                      java.util.function.Consumer<Long> deleter) {
        if (stale == null || Objects.equals(incomingId, idGetter.apply(stale))) {
            return;
        }
        Long staleId = idGetter.apply(stale);
        log.warn("[身份镜像]上游同 {} 重建：镜像保留行 id={} → 跟随上游新 id={}，删旧插新",
                keyName, staleId, incomingId);
        deleter.accept(staleId);
        existing.remove(staleId);
    }

    /** 关系按 (userId,tenantId) 全量比对增删（返回 {inserted, deleted}） */
    private int[] syncUserTenants(List<SnapshotRes.UserTenantItem> items, Date now) {
        Map<Long, IdmUserTenantMirror> existing = new HashMap<>();
        for (IdmUserTenantMirror row : userTenantMirrorService.listAll()) {
            if (row.getUserId() != null && row.getTenantId() != null) {
                existing.put(relKey(row.getUserId(), row.getTenantId()), row);
            }
        }
        Set<Long> upstream = new HashSet<>();
        int inserted = 0;
        if (items != null) {
            for (SnapshotRes.UserTenantItem item : items) {
                if (item == null || item.getUserId() == null || item.getTenantId() == null) {
                    continue;
                }
                long key = relKey(item.getUserId(), item.getTenantId());
                upstream.add(key);
                if (!existing.containsKey(key)) {
                    IdmUserTenantMirror row = new IdmUserTenantMirror();
                    row.setUserId(item.getUserId());
                    row.setTenantId(item.getTenantId());
                    row.setValid(CommonConst.YES);
                    row.setCreateAt(now);
                    row.setUpdateAt(now);
                    userTenantMirrorService.insert(row);
                    inserted++;
                }
            }
        }
        int deleted = 0;
        for (Map.Entry<Long, IdmUserTenantMirror> entry : existing.entrySet()) {
            if (!upstream.contains(entry.getKey())) {
                IdmUserTenantMirror row = entry.getValue();
                if (userTenantMirrorService.deleteByUserAndTenant(row.getUserId(), row.getTenantId())) {
                    deleted++;
                }
            }
        }
        return new int[]{inserted, deleted};
    }

    /** 水位写入（幂等：同 watermark 值不写） */
    private boolean writeWatermark(long watermark, Date now) {
        IdmMirrorState state = mirrorStateService.getByKey(STATE_KEY_SNAPSHOT);
        String value = String.valueOf(watermark);
        if (state == null) {
            IdmMirrorState row = new IdmMirrorState();
            row.setStateKey(STATE_KEY_SNAPSHOT);
            row.setStateValue(value);
            row.setUpdateAt(now);
            mirrorStateService.insert(row);
            return true;
        }
        if (Objects.equals(state.getStateValue(), value)) {
            return false;
        }
        state.setStateValue(value);
        state.setUpdateAt(now);
        mirrorStateService.updateById(state);
        return true;
    }

    private static boolean userChanged(IdmUserMirror current, SnapshotRes.UserItem item) {
        return !Objects.equals(current.getCustomerNumber(), item.getCustomerNumber())
                || !Objects.equals(current.getName(), item.getName());
    }

    private static boolean tenantChanged(IdmTenantMirror current, SnapshotRes.TenantItem item) {
        return !Objects.equals(current.getTenantCode(), item.getTenantCode())
                || !Objects.equals(current.getTenantName(), item.getTenantName());
    }

    /** (userId,tenantId) → 复合键（userId 高 32 位｜tenantId 低 32 位，同域 bigint id 收敛安全） */
    static long relKey(long userId, long tenantId) {
        return (userId << 32) | (tenantId & 0xFFFFFFFFL);
    }

    /**
     * 同步结果计数（日志/单测断言面）。
     */
    public static class SyncResult {

        public int usersInserted;
        public int usersUpdated;
        public int tenantsInserted;
        public int tenantsUpdated;
        public int relationsInserted;
        public int relationsDeleted;
        public boolean stateWritten;
        public long watermark;
    }
}
