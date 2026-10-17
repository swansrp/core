package com.bidr.kernel.utils;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * Title: FuncUtilScalarValueTest
 * Description: {@link FuncUtil#isScalar} 与 {@link FuncUtil#scalarValues} 条件值清洗口径单测。
 * <p>
 * 查询条件值来自前端拼装，脏配置里会出现嵌套数组（如 value:[[]]），这类值一旦被送进 SQL 生成器
 * 会得到 `col = ` / `IN ()` 这种残缺语句，故清洗口径需固定：只剔除 null 与嵌套结构，空串是合法值。
 *
 * @author Sharp
 * @since 2026-09-20
 */
public class FuncUtilScalarValueTest {

    @Test
    public void testIsScalar() {
        Assert.assertTrue(FuncUtil.isScalar("特斯拉"));
        // 空串对应库里存 '' 的维度值，属于可用的单个 SQL 值
        Assert.assertTrue(FuncUtil.isScalar(""));
        Assert.assertTrue(FuncUtil.isScalar(1));
        Assert.assertFalse(FuncUtil.isScalar(null));
        Assert.assertFalse(FuncUtil.isScalar(Collections.emptyList()));
        Assert.assertFalse(FuncUtil.isScalar(new HashMap<>()));
        Assert.assertFalse(FuncUtil.isScalar(new Object[]{"a"}));
    }

    @Test
    public void testScalarValuesKeepOrderAndDropNested() {
        List<Object> dirty = new ArrayList<>(Arrays.asList("a", Collections.emptyList(), null,
                Arrays.asList("junk"), "c"));
        Assert.assertEquals(Arrays.asList("a", "c"), FuncUtil.scalarValues(dirty));
    }

    @Test
    public void testScalarValuesOnEmptyOrNullInput() {
        Assert.assertTrue(FuncUtil.scalarValues(null).isEmpty());
        Assert.assertTrue(FuncUtil.scalarValues(Collections.emptyList()).isEmpty());
    }

    @Test
    public void testScalarValuesReturnsNewListForImmutableInput() {
        // 前端条件可能传不可变列表（如 Collections.singletonList），清洗必须返回新列表而非原地修改
        List<Object> immutable = Collections.singletonList("a");
        List<Object> scalarValues = FuncUtil.scalarValues(immutable);
        Assert.assertEquals(Collections.singletonList("a"), scalarValues);
        scalarValues.add("b");
        Assert.assertEquals(1, immutable.size());
    }
}
