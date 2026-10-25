package com.bidr.notification;

import com.bidr.authorization.annotation.auth.Auth;
import com.bidr.authorization.annotation.auth.AuthLogin;
import com.bidr.authorization.holder.AccountContext;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.kernel.config.log.LogSilent;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.validate.Validator;
import com.bidr.notification.dao.entity.NotificationRecord;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 站内信通用端点（M21）。🔴 三条纪律（与 biz/erp 站内信先例对齐）：
 * ①receiver 恒取 {@code AccountContext.getOperator()}，<b>签名里不存在 receiver 入参</b>
 * ——传他人工号不会有任何效果；②{@code markRead} 的 UPDATE 再强制
 * {@code target_user = 登录人}（双保险，store 契约）；③<b>不继承 BaseAdminController</b>
 * ——其 /general/query 泛型读端点会变成"按参数任意查他人消息"的越权面（面不存在＝漏洞不存在）。
 *
 * @author sharp (M21：站内信机制 2026-10-25)
 */
@Api(tags = "站内信")
@Auth(AuthLogin.class)
@RestController
@RequestMapping(path = "/web/api/notification")
public class NotificationController {

    /** 可选注入：宿主关掉默认 store 又未提供实现时，端点降级为空/0（不让应用起不来） */
    private final org.springframework.beans.factory.ObjectProvider<NotificationStore> storeProvider;

    public NotificationController(
            org.springframework.beans.factory.ObjectProvider<NotificationStore> storeProvider) {
        this.storeProvider = storeProvider;
    }

    private NotificationStore store() {
        return storeProvider.getIfAvailable();
    }

    /** 收件箱分页（pushed_at 降序；readFlag 可选 '0'/'1'/缺省全部） */
    @ApiOperation(value = "收件箱分页", notes = "receiver 恒为登录人；readFlag 可选 '0'/'1'")
    @RequestMapping(value = "/list", method = RequestMethod.GET)
    public Page<Map<String, Object>> list(@RequestParam(value = "current", defaultValue = "1") long current,
                                          @RequestParam(value = "size", defaultValue = "20") long size,
                                          @RequestParam(value = "readFlag", required = false) String readFlag) {
        Page<NotificationRecord> page = (Page<NotificationRecord>) store().page(
                AccountContext.getOperator(), current, size, readFlag);
        Page<Map<String, Object>> out = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        java.util.List<Map<String, Object>> records = new java.util.ArrayList<>();
        for (NotificationRecord row : page.getRecords()) {
            records.add(toView(row));
        }
        out.setRecords(records);
        return out;
    }

    /** 未读数（铃铛徽标数据源） */
    @ApiOperation(value = "未读数", notes = "receiver 恒为登录人")
    @LogSilent
    @RequestMapping(value = "/unread-count", method = RequestMethod.GET)
    public long unreadCount() {
        NotificationStore s = store();
        return s == null ? 0L : s.unreadCount(AccountContext.getOperator());
    }

    /** 标记已读（幂等；越权 id 不生效） */
    @ApiOperation(value = "标记已读", notes = "body=消息 id 数组；越权 id 不生效、重复调用幂等")
    @RequestMapping(value = "/read", method = RequestMethod.POST)
    public void markRead(@RequestBody List<Long> ids) {
        Validator.assertNotEmpty(ids, com.bidr.kernel.constant.err.ErrCodeSys.SYS_ERR_MSG, "消息 ID 清单");
        NotificationStore s = store();
        if (s != null) {
            s.markRead(ids, AccountContext.getOperator());
        }
    }

    /** 全部已读 */
    @ApiOperation(value = "全部已读", notes = "receiver 恒为登录人")
    @RequestMapping(value = "/read-all", method = RequestMethod.POST)
    public void markAllRead() {
        NotificationStore s = store();
        if (s != null) {
            s.markAllRead(AccountContext.getOperator());
        }
    }

    /** 视图（action_params/extra 解析成结构返回，前端不二次 JSON.parse） */
    private Map<String, Object> toView(NotificationRecord row) {
        Map<String, Object> view = new HashMap<>();
        view.put("id", row.getId());
        view.put("msgType", row.getMsgType());
        view.put("level", row.getLevel());
        view.put("title", row.getTitle());
        view.put("summary", row.getSummary());
        view.put("actionType", row.getActionType());
        view.put("actionParams", parseJson(row.getActionParams()));
        view.put("bizType", row.getBizType());
        view.put("bizId", row.getBizId());
        view.put("readFlag", row.getReadFlag());
        view.put("readAt", row.getReadAt());
        view.put("pushedAt", row.getPushedAt());
        view.put("extra", parseJson(row.getExtra()));
        return view;
    }

    private Object parseJson(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(raw, Map.class);
        } catch (Exception e) {
            return null;
        }
    }
}
