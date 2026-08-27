package com.bidr.insight.smartquery.layer;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: RowPolicyDef
 * Description: 行级权限资产（row-policies.json）结构映射：按物理表声明行过滤策略，
 * 渲染期由 SqlGenerator 注入 WHERE（参数化绑定，对 semantic_query 载荷不可见不可绕过）。
 * value 支持登录态模板（${user.customerNumber} 等，fail-closed：解析不出值即拒绝生成）；
 * desc 为人工盖章说明。资产属人工治理类（LLM 可提案，审批合并后生效）
 *
 * @author Sharp
 * @since 2026/8/23
 */
@Data
public class RowPolicyDef {

    private String schemaVersion;

    private List<TablePolicies> tables = new ArrayList<>();

    @Data
    public static class TablePolicies {

        /** 物理表全名 db.tbl（须已注册为实体） */
        private String table;

        private List<Policy> policies = new ArrayList<>();
    }

    @Data
    public static class Policy {

        /** 过滤列（须在实体字段清单内） */
        private String column;

        /** 操作符白名单：= != > >= < <= in not_in */
        private String op;

        /** 过滤值：常量或 ${user.xxx} 登录态模板（数组配 in/not_in） */
        private Object value;

        /** 策略用途说明（人工盖章口径） */
        private String desc;
    }
}
