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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

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
 * <p>T2.4a 增 {@link #applyUserScope}：子系统登录时只写「当前登录用户自己的镜像行＋自己的租户归属」
 * （单用户作用域写，不需要运维凭据拉全量快照）——写入面仍唯一在本类，不变量不变。</p>
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

    /**
     * 单用户作用域写（登录同步，T2.4a）：只写本用户的镜像行与（可选）本用户的租户归属。
     * <ul>
     *   <li>{@code customerNumber} <b>必填</b>（镜像侧按 uk_customer_number 定位/落库）：空或纯空白 ⇒
     *       warn ＋ 返回 null，<b>零写</b>（fail-closed，调用方按「镜像无此人」处理）。</li>
     *   <li>用户行按 id upsert；id 为空时按 userName → customerNumber 回落定位已有行；
     *       仍定位不到且 id 也为空 ⇒ 不写，返回 null。</li>
     *   <li>同 userName / 同 customerNumber 的异 id 残留行 ⇒ 删旧插新（对账单 #23 方案 A），
     *       并把被删旧 id 的残留关系行一并清掉（否则旧 id 关系行成永久孤儿，
     *       见 {@link #purgeUserRelations}）。</li>
     *   <li>{@code tenantItems == null} ⇒ 只写用户行，不碰租户与关系；
     *       非 null 空列表 ⇒ 本用户关系全删（与 null 语义刻意区分，由调用方决定给不给空列表）。</li>
     *   <li>{@code tenantItems != null} ⇒ 先按 id upsert 这些租户行，再对本用户关系做全量比对增删
     *       （只动本用户，不碰其他人）。</li>
     *   <li>并发首登撞唯一键（用户行 / 关系行）按「已存在」处置，异常不穿出登录链。</li>
     *   <li>不写水位（全量同步才写）、不做作用域外删除。</li>
     * </ul>
     * 写入面仍唯一（本类）：子系统登录链不需要运维凭据拉全量快照，只写自己那一行。
     * 🔴 不加 {@code @Transactional}：与全量同步同语义，逐写幂等，下一周期自愈。
     *
     * @param userId         上游用户 id（可空，空则按业务键回落定位）
     * @param customerNumber 用户编码（<b>必填</b>；空或纯空白 ⇒ 不写、返回 null）
     * @param userName       登录名（可空；子系统按其查人）
     * @param name           用户姓名（<b>可空</b>；null 时 update 腿<b>不覆盖</b>镜像既有值——
     *                       {@code updateById} 忽略 null 字段，老用户改名不会被空值抹平）
     * @param tenantItems    本用户当前有效租户行（null ⇒ 只写用户行，不碰租户与关系；
     *                       非 null 空列表 ⇒ 本用户关系全删）
     * @return 传入 userId 时恒等于上游 id；回落定位时＝镜像行 id；无法定位（含 customerNumber 为空）时为 null
     */
    public Long applyUserScope(Long userId, String customerNumber, String userName, String name,
                               List<SnapshotRes.TenantItem> tenantItems) {
        if (!StringUtils.hasText(customerNumber)) {
            log.warn("[身份镜像]登录作用域写缺少 customerNumber（镜像定位/落库必填），本次零写: userId={} userName={}",
                    userId, userName);
            return null;
        }
        Date now = new Date();
        // 业务键命中的既有行（用于「同键异 id 残留行」删旧插新；map 只装定位到的行，供复用 helper）
        Map<Long, IdmUserMirror> located = new HashMap<>();
        IdmUserMirror byUserName = userMirrorService.findByUserName(userName);
        IdmUserMirror byCustomerNumber = userMirrorService.findByCustomerNumber(customerNumber);
        if (byUserName != null) {
            located.put(byUserName.getId(), byUserName);
        }
        if (byCustomerNumber != null) {
            located.put(byCustomerNumber.getId(), byCustomerNumber);
        }

        Long resolvedId = userId;
        if (resolvedId == null) {
            IdmUserMirror fallback = byUserName != null ? byUserName : byCustomerNumber;
            if (fallback == null) {
                log.warn("[身份镜像]登录作用域写定位失败（id/userName/customerNumber 都落不到镜像行）: userName={} customerNumber={}",
                        userName, customerNumber);
                return null;
            }
            resolvedId = fallback.getId();
        }
        // 删旧插新跟随上游新 id：两个业务键各自可能命中残留行（同一行去重），旧 id 的关系随后清孤儿
        Set<Long> staleIds = new HashSet<>();
        Long staleByCustomerNumber = followRebuiltRow(located, byCustomerNumber, resolvedId, customerNumber,
                "customerNumber", IdmUserMirror::getId, staleId -> userMirrorService.deleteById(staleId));
        if (staleByCustomerNumber != null) {
            staleIds.add(staleByCustomerNumber);
        }
        Long staleByUserName = followRebuiltRow(located, byUserName, resolvedId, userName, "userName",
                IdmUserMirror::getId, staleId -> userMirrorService.deleteById(staleId));
        if (staleByUserName != null) {
            staleIds.add(staleByUserName);
        }

        IdmUserMirror current = userMirrorService.selectById(resolvedId);
        boolean inserted = false;
        if (current == null) {
            IdmUserMirror row = new IdmUserMirror();
            row.setId(resolvedId);
            row.setCustomerNumber(customerNumber);
            row.setUserName(userName);
            row.setName(name);
            row.setValid(CommonConst.YES);
            row.setCreateAt(now);
            row.setUpdateAt(now);
            try {
                userMirrorService.insert(row);
                inserted = true;
            } catch (DuplicateKeyException e) {
                // 并发首登：另一请求先插了同一业务键行 ⇒ 重读后走 update 腿；重读仍为空才是真异常（原样抛）
                IdmUserMirror concurrent = userMirrorService.selectById(resolvedId);
                if (concurrent == null) {
                    throw e;
                }
                log.warn("[身份镜像]登录作用域写撞唯一键（并发首登），改走 update 腿: id={} userName={} customerNumber={}",
                        resolvedId, userName, customerNumber);
                current = concurrent;
            }
        }
        if (!inserted && current != null && userChanged(current, customerNumber, userName, name)) {
            current.setCustomerNumber(customerNumber);
            current.setUserName(userName);
            current.setName(name);
            current.setUpdateAt(now);
            userMirrorService.updateById(current);
        }

        // 用户行已就位后清旧 id 孤儿关系（旧 id ≠ resolvedId，不影响随后按 resolvedId 重插）
        for (Long staleId : staleIds) {
            purgeUserRelations(staleId);
        }

        int relationsInserted = 0;
        int relationsDeleted = 0;
        int tenantCount = 0;
        if (tenantItems != null) {
            tenantCount = tenantItems.size();
            syncTenants(tenantItems, now);
            Map<Long, IdmUserTenantMirror> existingRelations = new HashMap<>();
            for (IdmUserTenantMirror relation : userTenantMirrorService.listByUserId(resolvedId)) {
                if (relation.getTenantId() != null) {
                    existingRelations.put(relation.getTenantId(), relation);
                }
            }
            Set<Long> upstreamTenantIds = new HashSet<>();
            for (SnapshotRes.TenantItem item : tenantItems) {
                if (item == null || item.getId() == null) {
                    continue;
                }
                upstreamTenantIds.add(item.getId());
                if (!existingRelations.containsKey(item.getId())) {
                    IdmUserTenantMirror row = new IdmUserTenantMirror();
                    row.setUserId(resolvedId);
                    row.setTenantId(item.getId());
                    row.setValid(CommonConst.YES);
                    row.setCreateAt(now);
                    row.setUpdateAt(now);
                    try {
                        userTenantMirrorService.insert(row);
                        relationsInserted++;
                    } catch (DuplicateKeyException e) {
                        // 并发首登撞 (userId,tenantId) 唯一键 ⇒ 关系已存在：不计数、不抛
                        log.warn("[身份镜像]关系行已存在（并发写吞掉唯一键冲突）: userId={} tenantId={}",
                                resolvedId, item.getId());
                    }
                }
            }
            for (Map.Entry<Long, IdmUserTenantMirror> entry : existingRelations.entrySet()) {
                if (!upstreamTenantIds.contains(entry.getKey())
                        && userTenantMirrorService.deleteByUserAndTenant(resolvedId, entry.getKey())) {
                    relationsDeleted++;
                }
            }
        }
        log.info("[身份镜像]登录作用域写: user={} userName={} tenants={} relations +{}/-{} 重建清孤儿用户 {}",
                resolvedId, userName, tenantCount, relationsInserted, relationsDeleted, staleIds.size());
        return resolvedId;
    }

    /**
     * 清掉被删旧 id 的残留关系行（F1-1：重建跟随的孤儿兜底）。
     * <p>「同业务键异 id 删旧插新」只删用户行；旧 id 的 {@code idm_user_tenant_mirror} 行之后不会再有
     * 任何一次全量比对认领它（本形态全量 job 可能不装配）⇒ 必须在此显式逐条删除。
     * 只按 userId 收窄，绝不碰其他人的关系行。</p>
     *
     * @param staleId 被删旧用户 id（null ⇒ 直接返回 0）
     * @return 删到的关系行数（日志/排障用）
     */
    private int purgeUserRelations(Long staleId) {
        if (staleId == null) {
            return 0;
        }
        int purged = 0;
        for (IdmUserTenantMirror relation : userTenantMirrorService.listByUserId(staleId)) {
            if (userTenantMirrorService.deleteByUserAndTenant(staleId, relation.getTenantId())) {
                purged++;
            }
        }
        if (purged > 0) {
            log.warn("[身份镜像]重建跟随清孤儿关系: 旧用户 id={} 删除关系 {} 条", staleId, purged);
        }
        return purged;
    }

    /** 用户按 id upsert：insert miss 行、有差异才 update、无差异跳过（返回 {inserted, updated}） */
    private int[] syncUsers(List<SnapshotRes.UserItem> items, Date now) {
        Map<Long, IdmUserMirror> existing = new HashMap<>();
        Map<String, IdmUserMirror> existingByCustomerNumber = new HashMap<>();
        Map<String, IdmUserMirror> existingByUserName = new HashMap<>();
        for (IdmUserMirror row : userMirrorService.listAll()) {
            existing.put(row.getId(), row);
            if (row.getCustomerNumber() != null) {
                existingByCustomerNumber.put(row.getCustomerNumber(), row);
            }
            if (row.getUserName() != null) {
                existingByUserName.put(row.getUserName(), row);
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
                    Long staleByCustomerNumber = followRebuiltRow(existing,
                            existingByCustomerNumber.get(item.getCustomerNumber()),
                            item.getId(), item.getCustomerNumber(), "customerNumber",
                            IdmUserMirror::getId, staleId -> userMirrorService.deleteById(staleId));
                    Long staleByUserName = followRebuiltRow(existing,
                            existingByUserName.get(item.getUserName()),
                            item.getId(), item.getUserName(), "userName",
                            IdmUserMirror::getId, staleId -> userMirrorService.deleteById(staleId));
                    // 删旧行后业务键 map 同步剔除旧 id 行：保持「map 值必在库」不变式（F1-4）
                    removeStaleEntry(existingByCustomerNumber, staleByCustomerNumber, IdmUserMirror::getId);
                    removeStaleEntry(existingByUserName, staleByUserName, IdmUserMirror::getId);
                    IdmUserMirror row = new IdmUserMirror();
                    row.setId(item.getId());
                    row.setCustomerNumber(item.getCustomerNumber());
                    row.setUserName(item.getUserName());
                    row.setName(item.getName());
                    row.setValid(CommonConst.YES);
                    row.setCreateAt(now);
                    row.setUpdateAt(now);
                    userMirrorService.insert(row);
                    existing.put(row.getId(), row);
                    if (row.getCustomerNumber() != null) {
                        existingByCustomerNumber.put(row.getCustomerNumber(), row);
                    }
                    if (row.getUserName() != null) {
                        existingByUserName.put(row.getUserName(), row);
                    }
                    inserted++;
                } else if (userChanged(current, item)) {
                    current.setCustomerNumber(item.getCustomerNumber());
                    current.setUserName(item.getUserName());
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
     * （uk_customer_number/uk_user_name/uk_tenant_code）挡住新行，撞键即同步永久失败。
     * 此处按业务键找到该保留行，<b>删旧插新</b>让镜像跟随上游新 id——恢复不变式
     * 「镜像行＝上游当前 valid 实体、id 逐字等于上游 id」；绑定键（customer_number/user_name）语义不变，
     * 关系镜像随后续全量比对自愈（删旧 id 关系、插新 id 关系）。
     * 🔴 删旧与插新非原子（同步无事务包裹）：若删后插前崩溃，下一周期全量快照自愈。
     * 上游同业务键出现多个 valid 行属脏数据，按快照序 last-write-wins（每次 warn 留痕）。
     *
     * @return 被删掉的旧行 id（未删 ⇒ null）——单用户作用域写用它清旧 id 的残留关系行；
     *         全量同步忽略返回值（其后全量比对自愈）
     */
    private <T> Long followRebuiltRow(Map<Long, T> existing, T stale, Long incomingId, String keyValue,
                                      String keyName, Function<T, Long> idGetter,
                                      Consumer<Long> deleter) {
        if (stale == null) {
            return null;
        }
        Long staleId = idGetter.apply(stale);
        // 同一残留行可能同时命中两个业务键（customer_number 与 user_name）：已删过的不重复删
        if (Objects.equals(incomingId, staleId) || !existing.containsKey(staleId)) {
            return null;
        }
        log.warn("[身份镜像]上游同 {} 重建：镜像保留行 id={} → 跟随上游新 id={}，删旧插新",
                keyName, staleId, incomingId);
        deleter.accept(staleId);
        existing.remove(staleId);
        return staleId;
    }

    /** 业务键 map 剔除指向已删旧 id 的条目（F1-4：保持「map 值必在库」不变式） */
    private static <K, T> void removeStaleEntry(Map<K, T> byKey, Long staleId, Function<T, Long> idGetter) {
        if (staleId == null) {
            return;
        }
        for (Iterator<Map.Entry<K, T>> it = byKey.entrySet().iterator(); it.hasNext(); ) {
            if (Objects.equals(idGetter.apply(it.next().getValue()), staleId)) {
                it.remove();
            }
        }
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
        return userChanged(current, item.getCustomerNumber(), item.getUserName(), item.getName());
    }

    /** 用户行三业务属性是否有差异（全量同步与单用户作用域写共用判据） */
    private static boolean userChanged(IdmUserMirror current, String customerNumber, String userName, String name) {
        return !Objects.equals(current.getCustomerNumber(), customerNumber)
                || !Objects.equals(current.getUserName(), userName)
                || !Objects.equals(current.getName(), name);
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
