package com.bidr.insight.smartquery.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: QueryRows
 * Description: 查询执行结果：列名 + 行数据（值保持原始类型）
 *
 * @author Sharp
 * @since 2026/8/18
 */
@Data
public class QueryRows {

    private List<String> columns = new ArrayList<>();

    private List<List<Object>> rows = new ArrayList<>();
}
