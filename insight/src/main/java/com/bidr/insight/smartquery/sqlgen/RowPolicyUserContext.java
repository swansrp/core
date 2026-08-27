package com.bidr.insight.smartquery.sqlgen;

import com.bidr.authorization.bo.account.AccountInfo;
import com.bidr.authorization.holder.AccountContext;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Title: RowPolicyUserContext
 * Description: 行权限渲染期用户上下文（P2-3 用户上下文桥）：问数发起人登录态快照，
 * 供 row-policies 资产的 ${user.xxx} 模板解析。值全部走参数化绑定，不拼 SQL 字面量。
 * fail-closed：配了行策略的表在无用户上下文/变量解析不出值时直接拒绝生成，绝不静默放行。
 * 维护链/生成链等后台线程不构建本上下文（管理员全量视角，不注入行权限）
 *
 * @author Sharp
 * @since 2026/8/23
 */
public class RowPolicyUserContext {

    /** ${user.xxx} 模板：xxx 支持内置属性名或 attr.KEY（登录 extraData 任意键，KEY 可含字母数字下划线） */
    private static final Pattern TEMPLATE = Pattern.compile("\\$\\{user\\.([A-Za-z0-9_.]+)}");

    private final Long userId;
    private final String customerNumber;
    private final String userName;
    private final String name;
    /** 登录态扩展属性（extraData 字符串化视图：部门等按 token 实际携带项取） */
    private final Map<String, String> attrs;

    public RowPolicyUserContext(Long userId, String customerNumber, String userName,
                                String name, Map<String, String> attrs) {
        this.userId = userId;
        this.customerNumber = customerNumber;
        this.userName = userName;
        this.name = name;
        this.attrs = attrs == null ? new HashMap<>() : attrs;
    }

    /**
     * 从当前登录态构建（HTTP 请求线程内调用）；未登录返回 null——
     * null 语义为「不注入行权限」，配了行策略的表会在渲染期 fail-closed 拒绝
     */
    public static RowPolicyUserContext fromCurrent() {
        AccountInfo account = AccountContext.get();
        if (account == null) {
            return null;
        }
        Map<String, String> attrs = new HashMap<>();
        if (account.getExtraData() != null) {
            account.getExtraData().forEach((k, v) -> {
                if (v != null) {
                    attrs.put(k, String.valueOf(v));
                }
            });
        }
        return new RowPolicyUserContext(account.getUserId(), account.getCustomerNumber(),
                account.getUserName(), account.getName(), attrs);
    }

    /**
     * 解析 ${user.xxx} 模板：内置属性（id/customerNumber/userName/name）+ attr.KEY 扩展位；
     * 含无法解析的模板时抛 SqlGenException（fail-closed，防越权放行）；纯常量原样返回
     */
    public Object resolve(String value) {
        if (value == null) {
            throw new SqlGenException("行权限策略 value 为空，无法解析用户上下文");
        }
        if (!value.contains("${user.")) {
            return value;
        }
        Matcher m = TEMPLATE.matcher(value);
        StringBuffer sb = new StringBuffer();
        boolean resolved = false;
        while (m.find()) {
            String key = m.group(1);
            String v = builtin(key);
            if (v == null) {
                // attr.KEY 形式剥前缀查扩展属性；裸 KEY（非内置）也回落扩展属性，兼容 token 键名直接引用
                v = attrs.containsKey(key) ? attrs.get(key) : attrs.get(key.startsWith("attr.") ? key.substring(5) : key);
            }
            if (v == null || v.isEmpty()) {
                throw new SqlGenException("行权限模板 ${user." + key + "} 在当前登录态中无值，拒绝生成（fail-closed）");
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(v));
            resolved = true;
        }
        m.appendTail(sb);
        if (!resolved) {
            throw new SqlGenException("行权限 value 含非法模板语法: " + value);
        }
        return sb.toString();
    }

    /** 内置属性（与 AccountContext 取值口径对齐） */
    private String builtin(String key) {
        switch (key) {
            case "id":
            case "userId":
                return userId == null ? null : String.valueOf(userId);
            case "customerNumber":
            case "operator":
                return customerNumber;
            case "userName":
                return userName;
            case "name":
            case "nickName":
                return name;
            default:
                return null;
        }
    }
}
