package com.bidr.insight.smartquery.sqlgen;

/**
 * Title: SqlGenException
 * Description: SQL 生成期错误（等价 Python SqlGenError）：
 * 语义层静态定义缺失、JOIN 路径不可达、协议字段非法等。
 * 到达此处说明输入已通过校验层——属于防御性兜底
 *
 * @author Sharp
 * @since 2026/8/18
 */
public class SqlGenException extends RuntimeException {

    public SqlGenException(String message) {
        super(message);
    }
}
