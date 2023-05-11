package com.bidr.platform.constant.dict;

import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.constant.dict.MetaDict;
import com.bidr.kernel.utils.StringUtil;
import com.bidr.platform.dao.entity.SysDict;

import java.util.Collection;

/**
 * Title: IDynamicDict
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2021/3/16 22:36
 */
public interface IDynamicDict {
    /**
     * 生成字典列表
     *
     * @return 字典列表
     */
    Collection<SysDict> generate();

    /**
     * 生成字典条目
     *
     * @param value   字典值
     * @param display 显示
     * @param order   顺序
     * @return 字典条目
     */
    default SysDict buildSysDict(Object value, String display, Integer order) {
        SysDict item = new SysDict();
        String dictName = this.getClass().getAnnotation(MetaDict.class).value();
        String title = this.getClass().getAnnotation(MetaDict.class).remark();
        item.setDictId(dictName + StringUtil.parse(value));
        item.setReadOnly(CommonConst.YES);
        item.setDictTitle(title);
        item.setDictName(dictName);
        item.setDictValue(StringUtil.parse(value));
        item.setDictLabel(display);
        item.setDictSort(order);
        item.setShow(CommonConst.YES);
        item.setRemark(display);
        return item;
    }
}
