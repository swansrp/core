package com.bidr.td.vo;

import com.bidr.kernel.vo.portal.AdvancedQueryReq;
import lombok.Data;

@Data
public class TdAdvancedReq {
    private AdvancedQueryReq advanced;
    private Long from;
    private Long to;
}
