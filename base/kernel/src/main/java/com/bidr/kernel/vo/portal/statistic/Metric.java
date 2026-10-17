package com.bidr.kernel.vo.portal.statistic;

import lombok.Data;

import java.util.Map;

/**
 * Title: Metric
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/5/1 17:20
 */
@Data
public class Metric {
    private String column;
    private Map<String, String> dictMap;
}