package com.bidr.platform.bo.excel;

import lombok.Data;

import java.util.*;

/**
 * Title: ExcelExportBO
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/01/25 22:47
 */
@Data
public class ExcelExportBO {
    private String title;
    private Set<String> columnTitles;
    private List<List<String>> records;

    public ExcelExportBO() {
        columnTitles = new LinkedHashSet<>();
        records = new ArrayList<>();
    }
}
