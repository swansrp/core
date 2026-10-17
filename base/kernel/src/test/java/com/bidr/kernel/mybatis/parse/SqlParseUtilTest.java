package com.bidr.kernel.mybatis.parse;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;

/**
 * Title: SqlParseUtilTest
 * Description: {@code @DataScope} 依赖的 SQL 解析入口单测（离线，真解析器）。
 *
 * <p>存在意义有两层。第一层是行为：这是框架数据权限拦截器唯一的解析入口
 * （{@code DataPermissionIntercept} 调 {@code buildTableAliasMap} / {@code mergeWhere}），
 * 过去没有任何测试守着，"能不能解析"完全靠运气。</p>
 *
 * <p>第二层是版本取证：MyBatis-Plus 的动态 SQL 与手写 XML 常带连续空行，
 * 而 JSqlParser <b>4.6 起</b>对"子句里连续 3 个换行"直接抛 {@code ParseException}
 * （4.4 可以）。业务侧的空间隔离拦截器已经因为这个炸过一次，
 * 所以这里把带空行的形状钉住：换解析器版本时，本测试就是判据。</p>
 *
 * @author Sharp
 */
public class SqlParseUtilTest {

    /** 手写 XML / 格式化器常见的形状：列清单里有连续空行 */
    private static final String SELECT_WITH_BLANK_RUNS = "SELECT\n    id,\n\n\n"
            + "    space_id,\n\n    file_name\n\nFROM inkhub.kb_file\nWHERE id = ?";

    /** 同一条 SQL 的无空行形状（语义完全相同） */
    private static final String SELECT_COLLAPSED = "SELECT id, space_id, file_name "
            + "FROM inkhub.kb_file WHERE id = ?";

    private static final String SELECT_WITH_JOIN = "SELECT a.id, b.role FROM inkhub.kb_space_member a "
            + "LEFT JOIN inkhub.kb_space b ON a.space_id = b.id WHERE a.user_id = ?";

    /** 把实际生效的解析器版本打出来：换版本跑测试时，日志要能自证跑的是哪个 jar */
    @Test
    public void reportParserVersion() {
        System.out.println("[SqlParseUtilTest] jsqlparser from "
                + CCJSqlParserUtil.class.getProtectionDomain().getCodeSource().getLocation());
    }

    /**
     * 注意键名：{@code Table.getName()} <b>不含库名前缀</b>，所以 {@code inkhub.kb_file} 的键是
     * {@code kb_file}。本项目实体写的是 {@code @TableName("inkhub.xxx")}，
     * {@code @DataScope} 拼条件时必须按去库名的表名匹配——这条是行为规格，不是巧合。
     */
    @Test
    public void buildsTableAliasMapForPlainSelect() {
        Map<String, String> aliases = SqlParseUtil.buildTableAliasMap(SELECT_COLLAPSED);

        Assert.assertEquals("单表只应解析出一个表: " + aliases, 1, aliases.size());
        Assert.assertTrue("键是不含库名的表名: " + aliases, aliases.containsKey("kb_file"));
        Assert.assertNull("无别名时别名应为 null（数据权限按表名拼条件）", aliases.get("kb_file"));
    }

    @Test
    public void buildsTableAliasMapForJoinedSelect() {
        Map<String, String> aliases = SqlParseUtil.buildTableAliasMap(SELECT_WITH_JOIN);

        Assert.assertEquals(2, aliases.size());
        Assert.assertEquals("a", aliases.get("kb_space_member"));
        Assert.assertEquals("b", aliases.get("kb_space"));
    }

    /** 空行只是空白，不该改变解析结果——JSqlParser 4.6 在这条上会直接抛错 */
    @Test
    public void blankLinesAreTreatedAsWhitespace() {
        Map<String, String> withBlanks = SqlParseUtil.buildTableAliasMap(SELECT_WITH_BLANK_RUNS);
        Map<String, String> collapsed = SqlParseUtil.buildTableAliasMap(SELECT_COLLAPSED);

        Assert.assertEquals("同一语义的 SQL 不该因空行解析出不同结果", collapsed, withBlanks);
    }

    /** mergeWhere 要保留原有条件与列，只在末尾与权限条件求与 */
    @Test
    public void mergeWhereAppendsPermissionCondition() {
        Expression permission = new EqualsTo(new Column("space_id"), new LongValue(39L));

        String merged = SqlParseUtil.mergeWhere(SELECT_COLLAPSED, permission);

        Assert.assertTrue("原 WHERE 要留着: " + merged, merged.contains("id = ?"));
        Assert.assertTrue("权限条件要并进 WHERE: " + merged, merged.contains("space_id = 39"));
        Assert.assertTrue("SELECT 列不该被改写: " + merged, merged.contains("id, space_id, file_name"));
    }

    /** 无权限条件时原样返回（等价于不启用 @DataScope） */
    @Test
    public void mergeWithoutExpressionKeepsQuery() {
        String merged = SqlParseUtil.mergeWhere(SELECT_COLLAPSED, null);

        Assert.assertTrue(merged.toLowerCase().contains("from inkhub.kb_file"));
        Assert.assertTrue(merged.toLowerCase().contains("where id = ?"));
    }

    /** 框架解析入口必须吃下带空行的 SQL（{@code @DataScope} 依赖它，与解析器版本无关） */
    @Test
    public void parseEntryHandlesBlankLineRuns() {
        Assert.assertNotNull("SqlParseUtil 解析不了带空行的 SELECT",
                SqlParseUtil.getPlainSelect(SELECT_WITH_BLANK_RUNS));
    }

    /** 只作记录：裸解析器能不能容忍空行取决于钉的是哪个版本，不构成我们的契约 */
    @Test
    public void reportRawParserBlankLineTolerance() {
        try {
            CCJSqlParserUtil.parse(SELECT_WITH_BLANK_RUNS);
            System.out.println("[SqlParseUtilTest] 裸解析器容忍空行（当前版本本身没问题）");
        } catch (Exception e) {
            System.out.println("[SqlParseUtilTest] 裸解析器不容忍空行，靠 SqlParseUtil 规整兜住："
                    + e.getMessage().split("\n")[0]);
        }
    }
}
