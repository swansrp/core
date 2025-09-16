package com.bidr.admin.vo.statistic;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Title: DashboardStatisticRes
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/9/15 11:13
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DashboardStatisticRes extends DashboardStatisticVO {
    private List<DashboardStatisticRes> children;
}
