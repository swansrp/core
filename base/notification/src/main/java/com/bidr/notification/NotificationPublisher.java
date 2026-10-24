package com.bidr.notification;

import com.bidr.authorization.holder.TenantContext;
import com.bidr.notification.dao.entity.NotificationRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 发送口（唯一生产入口；业务与 {@code AsyncTaskNotificationBridge} 都走这里）。
 *
 * <p><b>两条铁律</b>：①publish 自身<b>绝不抛</b>（内部 try-catch＋log.error——"提示失败"不许
 * 变成"业务二次失败"，同审计 writer 纪律）；②写腿由 store 的 {@code REQUIRES_NEW} 承担
 * （业务事务回滚不吞提醒）。宿主未装 store＝丢弃＋log.error（不抛、不影响业务）。</p>
 *
 * <p><b>租户兜底</b>：draft.tenantId 为 null 且当前线程有租户上下文时才取
 * {@link TenantContext} 填充；显式传值（含业务对象归属）永远优先。</p>
 *
 * <p><b>action 格式校验</b>（违规只丢动作、消息照发）：码 {@code ^[a-z][a-z0-9_.-]{0,63}$}；
 * 键 {@code ^[a-zA-Z][a-zA-Z0-9_]{0,31}$}；值只收 String/Number/Boolean（拒嵌套）；≤10 键；
 * 序列化 ≤1000 字符。</p>
 *
 * @author sharp (M21：站内信机制 2026-10-25)
 */
@Slf4j
@Component
public class NotificationPublisher {

    private static final Pattern ACTION_CODE = Pattern.compile("^[a-z][a-z0-9_.-]{0,63}$");
    private static final Pattern PARAM_KEY = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{0,31}$");
    private static final int PARAM_MAX_KEYS = 10;
    private static final int PARAMS_JSON_MAX = 1000;

    private final ObjectProvider<NotificationStore> storeProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NotificationPublisher(ObjectProvider<NotificationStore> storeProvider) {
        this.storeProvider = storeProvider;
    }

    /** 发送（扇开：N 收件人＝N 行）；任何失败只 log.error，绝不出本方法 */
    public void publish(NotificationDraft draft) {
        if (draft == null) {
            return;
        }
        try {
            NotificationStore store = storeProvider.getIfAvailable();
            if (store == null) {
                log.error("站内信无 NotificationStore 装配，消息丢弃（宿主需装实现或开默认 store）: type={} title={}",
                        draft.getMsgType(), draft.getTitle());
                return;
            }
            String actionParamsJson = serializeActionParams(draft);
            String extraJson = draft.getExtra() == null || draft.getExtra().isEmpty()
                    ? null : objectMapper.writeValueAsString(draft.getExtra());
            Long tenantId = draft.getTenantId() != null ? draft.getTenantId() : currentTenantOrNull();

            List<NotificationRecord> rows = new ArrayList<>(draft.getReceivers().size());
            for (String receiver : draft.getReceivers()) {
                if (NotificationText.blank(receiver)) {
                    continue;
                }
                NotificationRecord row = new NotificationRecord();
                row.setMsgType(NotificationText.truncate(draft.getMsgType(), 64));
                row.setLevel(draft.getLevel().name());
                row.setTitle(NotificationText.truncate(draft.getTitle(), 255));
                row.setSummary(NotificationText.truncate(draft.getSummary(), 300));
                row.setContent(NotificationText.truncate(draft.getContent(), 2000));
                row.setActionType(actionParamsJson != null || draft.getActionType() != null
                        ? validActionType(draft) : null);
                row.setActionParams(actionParamsJson);
                row.setBizType(NotificationText.truncate(draft.getBizType(), 64));
                row.setBizId(NotificationText.truncate(draft.getBizId(), 128));
                row.setDedupeKey(NotificationText.truncate(draft.getDedupeKey(), 128));
                row.setTargetUser(receiver.trim());
                row.setTenantId(tenantId);
                row.setExtra(NotificationText.truncate(extraJson, 1000));
                rows.add(row);
            }
            if (!rows.isEmpty()) {
                store.save(rows);
            }
        } catch (Exception e) {
            log.error("站内信发送失败（不影响业务）: type={} title={} {}", draft.getMsgType(),
                    draft.getTitle(), e.getMessage(), e);
        }
    }

    /** 校验通过的 action_type（不合规＝null＝不可点击，消息照发） */
    private String validActionType(NotificationDraft draft) {
        String code = draft.getActionType();
        if (code == null || !ACTION_CODE.matcher(code).matches()) {
            if (code != null) {
                log.warn("站内信 action_type 不合规（丢弃动作，消息照发）: {}", code);
            }
            return null;
        }
        return code;
    }

    /** 参数序列化＋格式校验（键值不合规的键丢弃；整体超长＝丢弃动作） */
    private String serializeActionParams(NotificationDraft draft) {
        Map<String, Object> params = draft.getActionParams();
        if (params == null || params.isEmpty()) {
            return draft.getActionType() == null ? null : "{}";
        }
        StringBuilder json = new StringBuilder("{");
        int kept = 0;
        for (Map.Entry<String, Object> e : params.entrySet()) {
            if (kept >= PARAM_MAX_KEYS) {
                log.warn("站内信 action_params 超 {} 键（超出丢弃）: action={}", PARAM_MAX_KEYS,
                        draft.getActionType());
                break;
            }
            String key = e.getKey();
            Object value = e.getValue();
            if (key == null || !PARAM_KEY.matcher(key).matches()
                    || !(value == null || value instanceof String || value instanceof Number
                        || value instanceof Boolean)) {
                log.warn("站内信 action_param 键/值不合规（丢弃该键）: key={} valueClass={}", key,
                        value == null ? null : value.getClass().getSimpleName());
                continue;
            }
            if (kept > 0) {
                json.append(',');
            }
            try {
                json.append(objectMapper.writeValueAsString(key)).append(':')
                        .append(value == null ? "null" : objectMapper.writeValueAsString(value));
            } catch (Exception ex) {
                continue;
            }
            kept++;
        }
        json.append('}');
        if (json.length() > PARAMS_JSON_MAX) {
            log.warn("站内信 action_params 序列化超 {} 字符（整体丢弃动作）: action={}",
                    PARAMS_JSON_MAX, draft.getActionType());
            return null;
        }
        return json.toString();
    }

    private Long currentTenantOrNull() {
        List<Long> tenantIds = TenantContext.getTenantIds();
        return tenantIds == null || tenantIds.isEmpty() ? null : tenantIds.get(0);
    }
}
