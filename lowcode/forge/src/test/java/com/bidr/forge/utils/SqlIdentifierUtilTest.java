package com.bidr.forge.utils;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Title: SqlIdentifierUtilTest
 * Description: {@link SqlIdentifierUtil} 标识符白名单字符集单测（请求侧字段名拼入 SQL 的最后一道闸门）。
 * <p>
 * 漏洞形态：透视/统计/排序的请求字段名（sort.property、group.value、measure.field、pivot 列标识）会被
 * 原样拼进 {@code `...`} 引用位置，其中 pivot 的 ORDER BY 此前只拼不校验 —— 字段名里带一个反引号即可
 * 闭合引用改写整条 SQL；Dataset 的 ORDER BY 更把映射值不加引号拼在 LIMIT 之前，`a--` 能把后续子句注释掉。
 * 期望口径：只放行字母/数字/下划线/$/点/单连字符/中文，其余（反引号、反斜杠、引号、空白、括号、分号、
 * 注释符、成对连字符）一律判非法；且必须清楚 sanitizeQuotedIdentifier 只剥最外层成对引号，不能当防注入用。
 *
 * @author Sharp
 * @since 2026-09-23
 */
public class SqlIdentifierUtilTest {

    @DataProvider(name = "safeIdentifiers")
    public Object[][] safeIdentifiers() {
        return new Object[][]{
                {"age"},
                {"userStatus"},
                {"manager_appoint_status"},
                {"total__amount"},
                {"2024-07__amount"},
                {"2024-07"},
                {"a-b"},
                {"t.status"},
                {"amount$1"},
                {"项目名称"},
        };
    }

    @DataProvider(name = "unsafeIdentifiers")
    public Object[][] unsafeIdentifiers() {
        return new Object[][]{
                // 反引号：直接闭合 `...` 引用
                {"salary`"},
                {"a` OR `1`=`1"},
                // 反斜杠：MySQL 默认模式下可转义掉闭合反引号，使引用提前失效
                {"a\\` , (SELECT 1) `"},
                // 引号/空白/括号/分号/注释符：均可改写语句结构
                {"'age'"},
                {"\"age\""},
                {"a b"},
                {"sum(a)"},
                {"a;DROP TABLE b"},
                {"a/*x*/b"},
                // 成对连字符：行注释起点，会吃掉拼在后面 LIMIT/WHERE 之前的子句
                {"a--"},
                {"a--b"},
                {"a--b ASC"},
                {""},
        };
    }

    @Test(dataProvider = "safeIdentifiers")
    public void testRealRequestFieldNamesAreAccepted(String identifier) {
        Assert.assertTrue(SqlIdentifierUtil.isSafeIdentifier(identifier),
                "真实请求字段名被误拦：" + identifier);
    }

    @Test(dataProvider = "unsafeIdentifiers")
    public void testEscapeAttemptsAreRejected(String identifier) {
        Assert.assertFalse(SqlIdentifierUtil.isSafeIdentifier(identifier),
                "可改写 SQL 结构的字段名被放行：[" + identifier + "]");
    }

    /**
     * 固化一次踩坑结论：sanitizeQuotedIdentifier 只处理最外层成对引号，对内嵌反引号无约束力。
     * 防注入必须走 isSafeIdentifier，不能再拿它当清洗手段用。
     */
    @Test
    public void testQuotedIdentifierSanitizeIsNotAnInjectionGuard() {
        String payload = "salary`x";
        Assert.assertEquals(SqlIdentifierUtil.sanitizeQuotedIdentifier(payload), payload,
                "该用例前提失效：清洗函数行为已变，请重新评估它是否足以防护");
        Assert.assertFalse(SqlIdentifierUtil.isSafeIdentifier(payload));
        // 成对包裹的脏数据仍能正确剥掉，与字符集校验互不影响
        Assert.assertEquals(SqlIdentifierUtil.sanitizeQuotedIdentifier("`age`"), "age");
        Assert.assertTrue(SqlIdentifierUtil.isSafeIdentifier("age"));
    }
}
