package com.bidr.authorization.mybatis.permission;

import net.sf.jsqlparser.expression.Expression;

import java.util.List;
import java.util.Map;

/**
 * Title: NoDataPermission
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/06/07 17:16
 */
public class NoDataPermission implements DataPermissionInf {
    @Override
    public Expression expression(Map<String, String> tableAliasMap) {
        return null;
    }

    @Override
    public Map<String, List<String>> getFilterMap() {
        return null;
    }

    @Override
    public void run(String... args) throws Exception {

    }
}
