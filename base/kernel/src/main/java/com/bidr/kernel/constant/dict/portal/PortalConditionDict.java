package com.bidr.kernel.constant.dict.portal;

import cn.hutool.core.util.EnumUtil;
import com.bidr.kernel.constant.dict.Dict;
import com.bidr.kernel.constant.dict.MetaDict;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Title: PortalConditionDict
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/05/05 15:53
 */
@MetaDict(value = "PORTAL_CONDITION_DICT", remark = "前端查询条件字典")
@AllArgsConstructor
public enum PortalConditionDict implements Dict {
    /**
     * 管理界面条件关系字典
     */
    EQUAL(1, "等于"),
    NOT_EQUAL(2, "不等于"),
    GREATER(3, "大于"),
    GREATER_EQUAL(4, "大于等于"),
    LESS(5, "小于"),
    LESS_EQUAL(6, "小于等于"),
    NULL(7, "空"),
    NOT_NULL(8, "非空"),
    LIKE(9, "包含"),
    NOT_LIKE(10, "不包含"),
    IN(11, "介于"),
    NOT_IN(12, "不介于"),
    BETWEEN(13, "之间"),
    NOT_BETWEEN(14, "之外"),
    CONTAIN(15, "其中之一"),
    CONTAIN_IN_OR(16, "多个其中之一(or)"),
    CONTAIN_IN_AND(17, "多个其中之一(and)");

    @Getter
    private final Integer value;
    @Getter
    private final String label;

    public static PortalConditionDict of(Integer value) {
        return EnumUtil.getBy(PortalConditionDict::getValue, value);
    }

    /**
     * 拼装该关系条件所需的最少可用值个数
     * 各 SQL 构建器（字面量内联/命名参数/MyBatis-Plus wrapper）统一按此口径校验条件值，
     * 值不足时跳过该条件，避免拼出 `col = ` 、`col IN ()` 这类让数据库直接语法报错的残缺 SQL
     * 注意：方法名刻意不使用 get 前缀，避免被当成字典的序列化属性暴露给前端
     *
     * @return 需要的值个数，NULL/NOT_NULL 为 0，BETWEEN 系为 2，其余为 1
     */
    public int requiredValueCount() {
        switch (this) {
            case NULL:
            case NOT_NULL:
                return 0;
            case BETWEEN:
            case NOT_BETWEEN:
                return 2;
            default:
                return 1;
        }
    }
}
