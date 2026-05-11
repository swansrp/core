package com.bidr.td.vo;

import com.bidr.kernel.vo.portal.AdvancedQueryReq;
import lombok.Data;

@Data
public class TdRangeReq {
    private AdvancedQueryReq advanced;
    private Long from;  // 毫秒时间戳
    private Long to;
}
