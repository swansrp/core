package com.bidr.td.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.vo.portal.AdvancedQueryReq;
import com.bidr.td.inf.TdControllerInf;
import com.bidr.td.repository.BaseTdRepo;
import com.bidr.td.vo.*;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@SuppressWarnings("unchecked")
public abstract class BaseTdController<VO> implements TdControllerInf<VO> {

    protected abstract BaseTdRepo getRepo();

    @ApiOperation("单行写入")
    @RequestMapping(value = "/td/insert", method = RequestMethod.POST)
    @Override
    public void insert(@RequestParam String subTableName, @RequestBody VO vo) {
        getRepo().insertOne(subTableName, vo);
        Resp.notice("写入成功");
    }

    @ApiOperation("批量写入（同一子表）")
    @RequestMapping(value = "/td/insert/batch", method = RequestMethod.POST)
    @Override
    public void insertBatch(@RequestParam String subTableName, @RequestBody List<VO> voList) {
        if (FuncUtil.isNotEmpty(voList)) {
            getRepo().insertBatch(subTableName, voList);
        }
        Resp.notice("批量写入成功");
    }

    @ApiOperation("跨子表批量写入")
    @RequestMapping(value = "/td/insert/multiBatch", method = RequestMethod.POST)
    @Override
    public void insertMultiBatch(@RequestBody TdInsertMultiBatchReq<VO> req) {
        if (req.getDataMap() != null && !req.getDataMap().isEmpty()) {
            getRepo().insertMultiTableBatch(req.getDataMap());
        }
        Resp.notice("跨子表批量写入成功");
    }

    @ApiOperation("时间范围 + tag 过滤查询")
    @RequestMapping(value = "/td/query/range", method = RequestMethod.POST)
    @Override
    public Page<Map<String, Object>> queryRange(@RequestBody TdRangeReq req) {
        return getRepo().queryRange(req);
    }

    @ApiOperation("每组最新一条 LAST_ROW")
    @RequestMapping(value = "/td/query/last", method = RequestMethod.POST)
    @Override
    public List<Map<String, Object>> queryLast(@RequestBody TdLastReq req) {
        return getRepo().queryLast(req.getAdvanced(), req.getGroupByTags());
    }

    @ApiOperation("高级查询（分页+排序+返回字段）")
    @RequestMapping(value = "/td/query/advanced", method = RequestMethod.POST)
    @Override
    public List<Map<String, Object>> queryAdvanced(@RequestBody TdAdvancedReq req) {
        return getRepo().queryAdvanced(req);
    }

    @ApiOperation("聚合降采样 INTERVAL")
    @RequestMapping(value = "/td/agg/interval", method = RequestMethod.POST)
    @Override
    public List<Map<String, Object>> aggInterval(@RequestBody TdIntervalReq req) {
        return getRepo().queryInterval(req);
    }

    @ApiOperation("按 tag 分组聚合")
    @RequestMapping(value = "/td/agg/groupByTag", method = RequestMethod.POST)
    @Override
    public List<Map<String, Object>> aggGroupByTag(@RequestBody TdGroupReq req) {
        return getRepo().queryGroupByTag(req);
    }

    @ApiOperation("TOP/BOTTOM 查询")
    @RequestMapping(value = "/td/agg/topN", method = RequestMethod.POST)
    @Override
    public List<Map<String, Object>> aggTopN(@RequestBody TdTopNReq req) {
        return getRepo().queryTopN(req);
    }

    @ApiOperation("CSV 导入")
    @RequestMapping(value = "/td/import/csv", method = RequestMethod.POST)
    @Override
    public void importCsv(@RequestParam String subTableName) {
        throw new UnsupportedOperationException("CSV import not yet implemented for TDengine");
    }
}
