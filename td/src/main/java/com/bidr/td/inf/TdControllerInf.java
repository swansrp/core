package com.bidr.td.inf;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.td.vo.*;
import java.util.List;
import java.util.Map;

public interface TdControllerInf<VO> {
    void insert(String subTableName, VO vo);
    void insertBatch(String subTableName, List<VO> voList);
    void insertMultiBatch(TdInsertMultiBatchReq<VO> req);
    Page<Map<String, Object>> queryRange(TdRangeReq req);
    List<Map<String, Object>> queryLast(TdLastReq req);
    List<Map<String, Object>> queryAdvanced(TdAdvancedReq req);
    List<Map<String, Object>> aggInterval(TdIntervalReq req);
    List<Map<String, Object>> aggGroupByTag(TdGroupReq req);
    List<Map<String, Object>> aggTopN(TdTopNReq req);
    void importCsv(String subTableName);
}
