package com.bidr.td.vo;

import com.bidr.kernel.vo.portal.AdvancedQueryReq;
import lombok.Data;
import java.util.List;

@Data
public class TdIntervalReq {
    private AdvancedQueryReq advanced;
    private Long from;
    private Long to;
    private String window;    // e.g. "1m", "1h"
    private String sliding;   // optional, e.g. "30s"
    private String fill;      // optional, e.g. "NONE", "PREV", "LINEAR"
    private List<String> funcs; // aggregate functions, e.g. ["AVG(temperature)", "MAX(humidity)"]
}
