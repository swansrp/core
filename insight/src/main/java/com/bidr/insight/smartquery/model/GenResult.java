package com.bidr.insight.smartquery.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: GenResult
 * Description: SQL 生成结果（对应 Python sql_gen.py 输出）：
 * sql（JDBC ? 占位符，等价 Python %s）+ params + columns + notes + translate
 *
 * @author Sharp
 * @since 2026/8/18
 */
@Data
public class GenResult {

    /** 参数化 SQL，占位符为 ?（Python 版为 %s） */
    private String sql;

    /** 绑定参数（顺序与 ? 对应，末位恒为 LIMIT 值） */
    private List<Object> params = new ArrayList<>();

    /** 输出列元数据 */
    private List<ColumnInfo> columns = new ArrayList<>();

    /** 口径备注（默认快照年/码值映射/半连接说明等） */
    private List<String> notes = new ArrayList<>();

    /** 需要码值→标签翻译的输出列（alias → alias 或 "_entity_field:实体.字段"） */
    private Map<String, String> translate = new LinkedHashMap<>();

    /** 输出列元数据项 */
    @Data
    public static class ColumnInfo {
        /** SQL 输出别名（指标/维度/字段英文名） */
        private String alias;
        /** dimension / metric / field */
        private String kind;
        /** 中文显示名 */
        private String display;

        public ColumnInfo(String alias, String kind, String display) {
            this.alias = alias;
            this.kind = kind;
            this.display = display;
        }
    }
}
