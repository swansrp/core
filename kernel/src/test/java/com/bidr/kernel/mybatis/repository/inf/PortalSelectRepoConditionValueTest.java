package com.bidr.kernel.mybatis.repository.inf;

import com.bidr.kernel.vo.portal.AdvancedQuery;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

/**
 * Title: PortalSelectRepoConditionValueTest
 * Description: {@link PortalSelectRepo#normalizeConditionValue} 条件值归一单测（MyBatis-Plus wrapper 路径）。
 * <p>
 * wrapper 路径此前只在部分分支用 isNotEmpty(get(0)) 兜底，遇到脏值 [ [] ] 会把嵌套集合交给 MyBatis 绑定，
 * between 只给一个值时更会越界取值抛 IndexOutOfBounds；归一后统一口径：值可用才拼装，不可用整条跳过。
 *
 * @author Sharp
 * @since 2026-09-20
 */
public class PortalSelectRepoConditionValueTest {

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final PortalSelectRepo REPO = Mockito.mock(PortalSelectRepo.class, Mockito.CALLS_REAL_METHODS);

    private static AdvancedQuery condition(Integer relation, Object... values) {
        AdvancedQuery query = new AdvancedQuery();
        query.setProperty("projectName");
        query.setRelation(relation);
        query.setValue(new ArrayList<>(Arrays.asList(values)));
        return query;
    }

    @Test
    public void testNestedElementDroppedAndScalarsKept() {
        AdvancedQuery query = condition(11, "a", Collections.emptyList(), null, "b");

        Assert.assertTrue(REPO.normalizeConditionValue(query));
        Assert.assertEquals(Arrays.asList("a", "b"), query.getValue());
    }

    @Test
    public void testEmptyStringIsUsableValue() {
        AdvancedQuery query = condition(1, "");

        Assert.assertTrue(REPO.normalizeConditionValue(query));
        Assert.assertEquals(Collections.singletonList(""), query.getValue());
    }

    @Test
    public void testNullAndNotNullUsableWithoutValue() {
        Assert.assertTrue(REPO.normalizeConditionValue(condition(7)));
        Assert.assertTrue(REPO.normalizeConditionValue(condition(8, Collections.emptyList())));
    }

    @Test
    public void testDirtyOnlyValueNotUsable() {
        Assert.assertFalse(REPO.normalizeConditionValue(condition(1, Collections.emptyList())));
        Assert.assertFalse(REPO.normalizeConditionValue(condition(11)));
    }

    @Test
    public void testBetweenRequiresTwoValues() {
        Assert.assertFalse(REPO.normalizeConditionValue(condition(13, "2026-01-01")));
        Assert.assertTrue(REPO.normalizeConditionValue(condition(13, "2026-01-01", "2026-02-01")));
    }

    @Test
    public void testUnknownOrMissingRelationNotUsable() {
        // 未知/缺失关系码此前会在 switch(null) 处抛 NPE
        Assert.assertFalse(REPO.normalizeConditionValue(condition(999, "a")));
        Assert.assertFalse(REPO.normalizeConditionValue(condition(null, "a")));
    }
}
