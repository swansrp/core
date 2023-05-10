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
    SUM("SUM(%s)"),
    COUNT("COUNT(%s)"),
    MAX("MAX(%s)"),
    MIN("MIN(%s)"),
    AVG("AVG(%s)"),
    LEN("LEN(%s)"),
    GROUP_CONCAT("GROUP_CONCAT(%s)");

    private final String sql;

    public String getSql() {
        return this.sql;
    }
}
