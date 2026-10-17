package com.bidr.td.vo;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class TdInsertMultiBatchReq<VO> {
    private Map<String, List<VO>> dataMap;
}
