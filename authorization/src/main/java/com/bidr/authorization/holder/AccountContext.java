package com.bidr.authorization.holder;

import com.bidr.authorization.bo.account.AccountInfo;
import com.bidr.authorization.bo.role.RoleInfo;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.JsonUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.utils.StringUtil;

import java.util.List;
import java.util.Map;

/**
 * Title: AccountContextHolder
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/27 11:27
 */
public class AccountContext {
    private static final ThreadLocal<AccountInfo> ACCOUNT_INFO_HOLDER = new ThreadLocal<>();

    private AccountContext() {
    }

    public static void set(AccountInfo s) {
        ACCOUNT_INFO_HOLDER.set(s);
    }

    public static void addExtraData(String key, Object value) {
        ACCOUNT_INFO_HOLDER.get().getExtraData().put(key, value);
    }

    public static void removeExtraData(String key) {
        ACCOUNT_INFO_HOLDER.get().getExtraData().remove(key);
    }

    public static Map<String, Object> getExtraData() {
        return ACCOUNT_INFO_HOLDER.get().getExtraData();
    }

    public static Object getExtraData(String key) {
        return ACCOUNT_INFO_HOLDER.get().getExtraData().get(key);
    }

    public static <T> T getExtraData(String key, Class<?> collectionClass, Class<?>... elementClasses) {
        Object obj = ACCOUNT_INFO_HOLDER.get().getExtraData().get(key);
        return JsonUtil.readJson(obj, collectionClass, elementClasses);
    }

    public static void remove() {
        if (ACCOUNT_INFO_HOLDER.get() != null) {
            ACCOUNT_INFO_HOLDER.remove();
        }
    }

    public static String getOperator() {
        AccountInfo accountInfo = get();
        if (FuncUtil.isNotEmpty(accountInfo)) {
            return accountInfo.getCustomerNumber();
        } else {
            return StringUtil.EMPTY;
        }
    }

    public static AccountInfo get() {
        return ACCOUNT_INFO_HOLDER.get();
    }

    public static Long getUserId() {
        AccountInfo accountInfo = get();
        if (FuncUtil.isNotEmpty(accountInfo)) {
            return accountInfo.getUserId();
        } else {
            return null;
        }
    }

    public static List<Long> getRoleIdList() {
        AccountInfo accountInfo = get();
        if (FuncUtil.isNotEmpty(accountInfo)) {
            return ReflectionUtil.getFieldList(accountInfo.getRoleInfoMap().values(), RoleInfo::getRoleId);
        } else {
            return null;
        }
    }
}
