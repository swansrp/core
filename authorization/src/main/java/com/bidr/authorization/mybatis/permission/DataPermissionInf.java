package com.bidr.authorization.mybatis.permission;

import com.bidr.authorization.constants.dict.DataPermitScopeDict;
import com.bidr.kernel.common.func.GetFunc;
import net.sf.jsqlparser.expression.operators.relational.InExpression;

/**
 * Title: DataPermissionInf
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/05/02 22:18
 */
public interface DataPermissionInf {
    /**
     * 获得需要权限控制的表和字段
     *
     * @return 字段lambda函数
     */
    GetFunc getFunc();

    /**
     * 构建控制查询语句
     *
     * @param columnName      字段名
     * @param dataPermitScope 角色
     * @return 查询语句
     */
    InExpression expression(String columnName, DataPermitScopeDict dataPermitScope);
}
