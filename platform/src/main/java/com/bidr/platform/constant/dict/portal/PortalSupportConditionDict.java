package com.bidr.platform.constant.dict.portal;

import com.bidr.kernel.constant.dict.MetaDict;
import com.bidr.kernel.constant.dict.portal.PortalConditionDict;
import com.bidr.kernel.constant.dict.portal.PortalFieldDict;
import com.bidr.kernel.utils.StringUtil;
import com.bidr.platform.constant.dict.IDynamicDict;
import com.bidr.platform.dao.entity.SysDict;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Title: PortalSupportConditionDict
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/11/08 15:20
 */
@Component
@MetaDict(value = "PORTAL_SUPPORT_CONDITION_DICT", remark = "前端字段支持查询条件")
@AllArgsConstructor
public class PortalSupportConditionDict implements IDynamicDict {

    private static final Map<PortalFieldDict, PortalConditionDict[]> MAP = new HashMap<>(
            PortalFieldDict.values().length);

    static {
        MAP.put(PortalFieldDict.STRING, new PortalConditionDict[]{PortalConditionDict.EQUAL, PortalConditionDict.LIKE});
        MAP.put(PortalFieldDict.BOOLEAN,
                new PortalConditionDict[]{PortalConditionDict.EQUAL, PortalConditionDict.NOT_EQUAL});
        MAP.put(PortalFieldDict.NUMBER,
                new PortalConditionDict[]{PortalConditionDict.EQUAL, PortalConditionDict.NOT_EQUAL,
                        PortalConditionDict.BETWEEN, PortalConditionDict.GREATER, PortalConditionDict.GREATER_EQUAL,
                        PortalConditionDict.LESS, PortalConditionDict.LESS_EQUAL});
        MAP.put(PortalFieldDict.ENUM,
                new PortalConditionDict[]{PortalConditionDict.EQUAL, PortalConditionDict.NOT_EQUAL,
                        PortalConditionDict.IN});
        MAP.put(PortalFieldDict.TREE, new PortalConditionDict[]{PortalConditionDict.EQUAL, PortalConditionDict.IN});
        MAP.put(PortalFieldDict.DATE,
                new PortalConditionDict[]{PortalConditionDict.EQUAL, PortalConditionDict.BETWEEN,
                        PortalConditionDict.GREATER, PortalConditionDict.GREATER_EQUAL, PortalConditionDict.LESS,
                        PortalConditionDict.LESS_EQUAL});
        MAP.put(PortalFieldDict.DATETIME,
                new PortalConditionDict[]{PortalConditionDict.BETWEEN, PortalConditionDict.GREATER,
                        PortalConditionDict.GREATER_EQUAL, PortalConditionDict.LESS, PortalConditionDict.LESS_EQUAL});
        MAP.put(PortalFieldDict.LINK, new PortalConditionDict[]{PortalConditionDict.LIKE});
        MAP.put(PortalFieldDict.HTML, new PortalConditionDict[]{PortalConditionDict.LIKE});
        MAP.put(PortalFieldDict.TEXT, new PortalConditionDict[]{PortalConditionDict.LIKE});
        MAP.put(PortalFieldDict.DEFAULT, new PortalConditionDict[]{PortalConditionDict.EQUAL});
    }


    @Override
    public Collection<SysDict> generate() {
        List<SysDict> list = new ArrayList<>();
        MAP.forEach((key, values) -> {
            List<String> conditions = new ArrayList<>();
            for (PortalConditionDict value : values) {
                conditions.add(StringUtil.parse(value.getValue()));
            }
            list.add(buildSysDict(key, conditions.toArray(new String[0])));
        });
        return list;
    }

    private SysDict buildSysDict(PortalFieldDict field, String... conditionDict) {
        SysDict sysDict = new SysDict();
        sysDict.setDictId(StringUtil.join("_", getDictName(), StringUtil.parse(field.getValue())));
        sysDict.setDictSort(field.ordinal());
        sysDict.setDictName(getDictName());
        sysDict.setDictTitle(getDictRemark());
        sysDict.setDictItem(null);
        sysDict.setDictValue(StringUtil.parse(field.getValue()));
        sysDict.setDictLabel(StringUtil.joinWith(StringUtil.COMMA, conditionDict));
        return sysDict;
    }
}
