package com.bidr.insight.smartquery;

import com.bidr.insight.smartquery.layer.EntityDef;
import com.bidr.insight.smartquery.meta.ColumnConventions;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Title: ColumnConventionsPairTest
 * Description: 编码↔名称同词干配对与维度瘦身单测（2026-08-24 修复背景：数仓常见
 * 裸词干列配套写法 dept + dept_name 无 _code 后缀，旧逻辑只认 _code/_no 尾缀导致
 * name 侧漏成维度）：尾缀配对优先、裸词干兜底、名称侧落 junk、名称列互不配对
 *
 * @author Sharp
 * @since 2026/8/24
 */
public class ColumnConventionsPairTest {

    private EntityDef.EntityFieldDef field(String name, String type) {
        EntityDef.EntityFieldDef f = new EntityDef.EntityFieldDef();
        f.setName(name);
        f.setType(type);
        return f;
    }

    private Map<String, String> pairs(String... cols) {
        List<EntityDef.EntityFieldDef> fields = Arrays.stream(cols)
                .map(c -> field(c, "String")).collect(Collectors.toList());
        return ColumnConventions.findCodeLabelPairs(fields);
    }

    private Set<String> labelCols(Map<String, String> pairs) {
        Set<String> s = new HashSet<>();
        pairs.values().forEach(c -> s.add(c.toLowerCase()));
        return s;
    }

    /** 尾缀编码列配对（原有口径不回归）：phase_code ↔ phase_name */
    @Test
    public void suffixedCodePair() {
        Map<String, String> p = pairs("phase_code", "phase_name", "amount");
        Assert.assertEquals("phase_name", p.get("phase_code"));
        Assert.assertEquals(1, p.size());
    }

    /** 修复主场景：无 _code 后缀的裸词干配套列（dept + dept_name / org + org_label）也配对 */
    @Test
    public void bareStemFallbackPair() {
        Map<String, String> p = pairs("dept", "dept_name", "org", "org_label");
        Assert.assertEquals("dept_name", p.get("dept"));
        Assert.assertEquals("org_label", p.get("org"));
        Assert.assertEquals(2, p.size());
    }

    /** 孤立名称列不配对（无同词干编码列时 name 列按普通 String 维度处理，不受本次规则影响） */
    @Test
    public void labelAloneNoPair() {
        Assert.assertTrue(pairs("dept_name").isEmpty());
        Assert.assertTrue(pairs("dept_name", "amount").isEmpty());
    }

    /** 同词干冲突时尾缀列优先：dept_code 配对，裸词干 dept 不重复配 */
    @Test
    public void suffixPreferredOverBare() {
        Map<String, String> p = pairs("dept", "dept_code", "dept_name");
        Assert.assertEquals("dept_name", p.get("dept_code"));
        Assert.assertEquals(1, p.size());
    }

    /** 名称列之间互不配对（dept_name + dept_label 无编码侧时不产出） */
    @Test
    public void labelColumnsDoNotPairEachOther() {
        Assert.assertTrue(pairs("dept_name", "dept_label").isEmpty());
    }

    /** 配对的名称侧落 junk（维度瘦身），编码侧不落——大小写不敏感 */
    @Test
    public void labelSideIsJunkCodeSideNot() {
        Map<String, String> p = pairs("DEPT", "Dept_Name");
        Assert.assertEquals("Dept_Name", p.get("DEPT"));
        Set<String> labels = labelCols(p);
        Assert.assertTrue(ColumnConventions.isJunkDimension("dept_name", labels, p));
        Assert.assertFalse(ColumnConventions.isJunkDimension("dept", labels, p));
    }
}
