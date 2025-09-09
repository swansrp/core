package com.bidr.admin.vo.statistic;

import lombok.Data;

/**
 * Title: IndicatorRes
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/9/9 22:38
 */
@Data
public class IndicatorItem {
    private String key;
    private String title;
    private String condition;
    private String dynamicColumns;
}