package com.bidr.kernel.mybatis.func;

import com.github.yulichang.wrapper.enums.BaseFuncEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Title: MySqlFuncEnum
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/10 13:41
 */
@AllArgsConstructor
@Getter
public enum MySqlFuncEnum implements BaseFuncEnum {
    /**
     * MYSQL 基础函数
     */
    SUM("SUM(%s) as %s"),
    COUNT("COUNT(%s) as %s"),
    MAX("MAX(%s) as %s"),
    MIN("MIN(%s) as %s"),
    AVG("AVG(%s) as %s"),
    LEN("LEN(%s) as %s"),
    GROUP_CONCAT("GROUP_CONCAT(%s) as %s");

    private final String sql;

    public String getSql() {
        return this.sql;
    }

    public String getSql(String column, String alias) {
        return String.format(this.sql, column, alias);
    }
}
