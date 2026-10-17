package com.bidr.insight.smartquery;

import com.bidr.insight.smartquery.layer.ValueDomainDef;
import com.bidr.insight.smartquery.meta.CommentValueParser;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * Title: SmartQueryCommentParseTest
 * Description: 列备注码值规则解析单测（骨架路径①）：常见分隔符写法、中文在左侧的
 * 「启用=0」写法、防误判（纯中文枚举/单项/label 全同）
 *
 * @author Sharp
 * @since 2026/8/20
 */
public class SmartQueryCommentParseTest {

    private List<ValueDomainDef.DomainValue> parse(String comment) {
        return CommentValueParser.parseCommentCodeVals(comment);
    }

    private String labelOf(List<ValueDomainDef.DomainValue> values, String code) {
        return values.stream().filter(v -> code.equals(v.getCode()))
                .map(ValueDomainDef.DomainValue::getLabel).findFirst().orElse(null);
    }

    /** 标准等号/冒号/中文冒号写法（码在左） */
    @Test
    public void standardSeparators() {
        List<ValueDomainDef.DomainValue> eq = parse("0=启用 1=停用");
        Assert.assertEquals(2, eq.size());
        Assert.assertEquals("启用", labelOf(eq, "0"));
        List<ValueDomainDef.DomainValue> colon = parse("1:男，2:女");
        Assert.assertEquals(2, colon.size());
        Assert.assertEquals("女", labelOf(colon, "2"));
    }

    /** 中文名称在左侧的写法（启用=0 / 男:1）：码取右侧，label 取左侧 */
    @Test
    public void chineseOnLeftSide() {
        List<ValueDomainDef.DomainValue> values = parse("启用=0、停用=1");
        Assert.assertEquals(2, values.size());
        Assert.assertEquals("启用", labelOf(values, "0"));
        Assert.assertEquals("停用", labelOf(values, "1"));
        List<ValueDomainDef.DomainValue> colon = parse("男:1 女:2");
        Assert.assertEquals(2, colon.size());
        Assert.assertEquals("男", labelOf(colon, "1"));
    }

    /** 前缀包裹与直连无分隔符写法 */
    @Test
    public void wrappedAndPlain() {
        List<ValueDomainDef.DomainValue> wrapped = parse("性别(1:男 2:女)");
        Assert.assertEquals(2, wrapped.size());
        Assert.assertEquals("男", labelOf(wrapped, "1"));
        List<ValueDomainDef.DomainValue> plain = parse("0传统 1创新");
        Assert.assertEquals(2, plain.size());
        Assert.assertEquals("创新", labelOf(plain, "1"));
    }

    /** 防误判：纯中文枚举（分不清码和名，留给 LLM 备注枚举层）/ 单项 / label 全同 */
    @Test
    public void rejectAmbiguous() {
        Assert.assertTrue(parse("未开工/在建/完工").isEmpty());
        Assert.assertTrue(parse("0=启用").isEmpty());
        Assert.assertTrue(parse("10分 20分").isEmpty());
        Assert.assertTrue(parse("").isEmpty());
        Assert.assertTrue(parse(null).isEmpty());
    }
}
