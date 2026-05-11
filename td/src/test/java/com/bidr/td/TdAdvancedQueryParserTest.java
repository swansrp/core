package com.bidr.td;

import com.bidr.kernel.constant.dict.portal.PortalConditionDict;
import com.bidr.kernel.vo.portal.AdvancedQuery;
import com.bidr.td.sync.TdAdvancedQueryParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TdAdvancedQueryParser 测试
 */
public class TdAdvancedQueryParserTest {

    @Test
    public void testParseSingleEqualCondition() {
        AdvancedQuery condition = new AdvancedQuery();
        condition.setProperty("device_id");
        condition.setRelation(PortalConditionDict.EQUAL.getValue());
        condition.setValue(Collections.singletonList("dev001"));

        TdAdvancedQueryParser parser = new TdAdvancedQueryParser();
        TdAdvancedQueryParser.ParsedCondition result = parser.parse(condition);

        assertEquals("device_id = ?", result.getClause());
        assertEquals(1, result.getParams().size());
        assertEquals("dev001", result.getParams().get(0));
    }

    @Test
    public void testParseNestedAndOr() {
        // 模拟嵌套 AND 条件
        AdvancedQuery root = new AdvancedQuery();
        root.setAndOr(AdvancedQuery.AND);

        AdvancedQuery cond1 = new AdvancedQuery();
        cond1.setProperty("temperature");
        cond1.setRelation(PortalConditionDict.GREATER.getValue());
        cond1.setValue(Collections.singletonList(30));

        AdvancedQuery cond2 = new AdvancedQuery();
        cond2.setProperty("humidity");
        cond2.setRelation(PortalConditionDict.LESS.getValue());
        cond2.setValue(Collections.singletonList(80));

        List<AdvancedQuery> list = new ArrayList<>();
        list.add(cond1);
        list.add(cond2);
        root.setConditionList(list);

        TdAdvancedQueryParser parser = new TdAdvancedQueryParser();
        TdAdvancedQueryParser.ParsedCondition result = parser.parse(root);

        assertTrue(result.getClause().contains("temperature > ?"));
        assertTrue(result.getClause().contains("humidity < ?"));
        assertEquals(2, result.getParams().size());
    }

    @Test
    public void testParseNullCondition() {
        TdAdvancedQueryParser parser = new TdAdvancedQueryParser();
        TdAdvancedQueryParser.ParsedCondition result = parser.parse(null);
        assertEquals("1=1", result.getClause());
        assertTrue(result.getParams().isEmpty());
    }

    @Test
    public void testParseInCondition() {
        AdvancedQuery condition = new AdvancedQuery();
        condition.setProperty("device_id");
        condition.setRelation(PortalConditionDict.IN.getValue());
        List<String> values = new ArrayList<>();
        values.add("dev001");
        values.add("dev002");
        condition.setValue(values);

        TdAdvancedQueryParser parser = new TdAdvancedQueryParser();
        TdAdvancedQueryParser.ParsedCondition result = parser.parse(condition);

        assertTrue(result.getClause().contains("device_id IN ("));
        assertEquals(2, result.getParams().size());
    }
}
