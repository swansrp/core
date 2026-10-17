package com.bidr.td.vo;

import com.bidr.kernel.vo.portal.AdvancedQueryReq;
import lombok.Data;
import java.util.List;

@Data
public class TdGroupReq {
    private AdvancedQueryReq advanced;
    private List<String> groupByTags;
    private List<String> funcs; // aggregate functions
}
