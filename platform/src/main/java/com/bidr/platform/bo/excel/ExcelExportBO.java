package com.bidr.platform.bo.excel;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        columnTitles = new HashSet<>();
        records = new ArrayList<>();
    }
}
