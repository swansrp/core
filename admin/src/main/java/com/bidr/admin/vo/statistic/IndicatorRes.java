package com.bidr.admin.vo.statistic;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: IndicatorRes
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/9/9 22:38
 */
@Data
public class IndicatorRes {
    private String id;
    private String key;
    private String pid;
    private String title;
    private Integer displayOrder;
    private List<IndicatorItem> items;
    private List<IndicatorRes> children = new ArrayList<>();
}