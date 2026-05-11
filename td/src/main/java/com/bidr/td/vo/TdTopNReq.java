package com.bidr.td.vo;

import com.bidr.kernel.vo.portal.AdvancedQueryReq;
import lombok.Data;

@Data
public class TdTopNReq {
    private AdvancedQueryReq advanced;
    private String field;
    private int n;
    private String direction; // "TOP" or "BOTTOM"
}
