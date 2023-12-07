package com.bidr.kernel.constant.dict.common;

import com.bidr.kernel.constant.dict.Dict;
import com.bidr.kernel.constant.dict.MetaDict;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Title: MonthDict
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/12/07 10:28
 */
@Getter
@RequiredArgsConstructor
@MetaDict(value = "MONTH_DICT", remark = "月份字典")
public enum MonthDict implements Dict {
    /**
     * 月份字典
     */

    JANUARY("01", "一月"),
    FEBRUARY("02", "二月"),
    MARCH("03", "三月"),
    APRIL("04", "四月"),
    MAY("05", "五月"),
    JUNE("06", "六月"),
    JULY("07", "七月"),
    AUGUST("08", "八月"),
    SEPTEMBER("09", "九月"),
    OCTOBER("10", "十月"),
    NOVEMBER("11", "十一月"),
    DECEMBER("12", "十二月");

    private final String value;
    private final String label;

}
