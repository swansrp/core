package com.bidr.kernel.vo.portal;

import lombok.Data;

/**
 * Title: MetricCondition
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/4/29 22:35
 */
@Data
public class MetricCondition {
    private String value;
    private String label;
    private AdvancedQuery condition;
}