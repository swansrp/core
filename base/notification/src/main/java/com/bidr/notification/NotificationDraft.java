package com.bidr.notification;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 发送草稿（不可变；{@link NotificationPublisher#publish} 的唯一入参形态）。
 *
 * <p><b>action 的写法</b>：不存 URL——多端各自解析路由。业务侧用 {@link #action(String, String, Object)}
 * 给动作码＋首个参数、{@link #actionParam(String, Object)} 链式追加；core 落库前统一序列化为 JSON
 * 并做格式校验（码/键/值/个数/长度，违规只丢动作不丢消息，见 {@code NotificationPublisher}）。</p>
 *
 * <p><b>租户归属</b>：可空。归属取自业务对象（不是发送方线程上下文——"发送方身份 ≠ 消息归属"）；
 * publisher 只在宿主未显式给出且当前线程有租户时兜底填充。null＝全局消息，任何租户下都可见。</p>
 *
 * @author sharp (M21：站内信机制 2026-10-25)
 */
public final class NotificationDraft {

    private final String msgType;
    private final NotificationLevel level;
    private final String title;
    private final String summary;
    private final String content;
    private final String actionType;
    private final Map<String, Object> actionParams;
    private final String bizType;
    private final String bizId;
    private final String dedupeKey;
    private final List<String> receivers;
    private final Long tenantId;
    private final Map<String, Object> extra;

    private NotificationDraft(Builder b) {
        this.msgType = b.msgType;
        this.level = b.level;
        this.title = b.title;
        this.summary = b.summary;
        this.content = b.content;
        this.actionType = b.actionType;
        this.actionParams = b.actionParams;
        this.bizType = b.bizType;
        this.bizId = b.bizId;
        this.dedupeKey = b.dedupeKey;
        this.receivers = b.receivers;
        this.tenantId = b.tenantId;
        this.extra = b.extra;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getMsgType() {
        return msgType;
    }

    public NotificationLevel getLevel() {
        return level;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public String getContent() {
        return content;
    }

    public String getActionType() {
        return actionType;
    }

    public Map<String, Object> getActionParams() {
        return actionParams;
    }

    public String getBizType() {
        return bizType;
    }

    public String getBizId() {
        return bizId;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }

    public List<String> getReceivers() {
        return receivers;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public Map<String, Object> getExtra() {
        return extra;
    }

    /** 单收件人便捷口（receivers 首位） */
    public String getTargetUser() {
        return receivers == null || receivers.isEmpty() ? null : receivers.get(0);
    }

    /** 构建器（msgType/title/receivers 必填，level 缺省 INFO） */
    public static final class Builder {
        private String msgType;
        private NotificationLevel level;
        private String title;
        private String summary;
        private String content;
        private String actionType;
        private Map<String, Object> actionParams;
        private String bizType;
        private String bizId;
        private String dedupeKey;
        private List<String> receivers = new ArrayList<>();
        private Long tenantId;
        private Map<String, Object> extra;

        public Builder msgType(String v) {
            this.msgType = v;
            return this;
        }

        public Builder level(NotificationLevel v) {
            this.level = v;
            return this;
        }

        public Builder title(String v) {
            this.title = v;
            return this;
        }

        public Builder summary(String v) {
            this.summary = v;
            return this;
        }

        public Builder content(String v) {
            this.content = v;
            return this;
        }

        public Builder bizType(String v) {
            this.bizType = v;
            return this;
        }

        public Builder bizId(String v) {
            this.bizId = v;
            return this;
        }

        public Builder dedupeKey(String v) {
            this.dedupeKey = v;
            return this;
        }

        public Builder targetUser(String v) {
            this.receivers.clear();
            if (v != null) {
                this.receivers.add(v);
            }
            return this;
        }

        public Builder receivers(List<String> v) {
            this.receivers = v == null ? new ArrayList<>() : v;
            return this;
        }

        public Builder tenantId(Long v) {
            this.tenantId = v;
            return this;
        }

        /** 业务上下文进 extra JSON（core 不认识这些键） */
        public Builder extra(String key, Object value) {
            if (this.extra == null) {
                this.extra = new LinkedHashMap<>();
            }
            this.extra.put(key, value);
            return this;
        }

        /** 动作码＋首个参数（无参动作可只给 code，key 传 null） */
        public Builder action(String actionType, String key, Object value) {
            this.actionType = actionType;
            if (key != null) {
                actionParam(key, value);
            }
            return this;
        }

        public Builder actionType(String v) { this.actionType = v; return this; }

        public Builder actionParams(Map<String, Object> v) { this.actionParams = v; return this; }

        public Builder actionParam(String key, Object value) {
            if (this.actionParams == null) {
                this.actionParams = new LinkedHashMap<>();
            }
            this.actionParams.put(key, value);
            return this;
        }

        public NotificationDraft build() {
            if (msgType == null || msgType.trim().isEmpty()) {
                throw new IllegalStateException("msgType 必填");
            }
            if (title == null || title.trim().isEmpty()) {
                throw new IllegalStateException("title 必填");
            }
            if (receivers == null || receivers.isEmpty()) {
                throw new IllegalStateException("receivers 必填（至少一位收件人）");
            }
            if (level == null) {
                level = NotificationLevel.INFO;
            }
            return new NotificationDraft(this);
        }
    }
}
