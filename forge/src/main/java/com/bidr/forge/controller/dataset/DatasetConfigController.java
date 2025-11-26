package com.bidr.forge.controller.dataset;

import com.bidr.forge.dao.entity.SysDatasetColumn;
import com.bidr.forge.service.dataset.DatasetConfigService;
import com.bidr.forge.vo.dataset.DatasetColumnReq;
import com.bidr.forge.vo.dataset.DatasetConfigReq;
import com.bidr.forge.vo.dataset.DatasetConfigRes;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.exception.NoticeException;
import com.bidr.kernel.vo.common.IdOrderReqVO;
import com.bidr.kernel.vo.common.IdReqVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Dataset配置管理接口
 * 用于前端创建和管理Dataset及其列配置
 *
 * @author Sharp
 * @since 2025-11-25
 */
@Slf4j
@RestController
@Api(tags = "Dataset配置管理")
@RequestMapping("/web/dataset/config")
@RequiredArgsConstructor
public class DatasetConfigController {

    private final DatasetConfigService datasetConfigService;

    @ApiOperation("解析SQL生成配置（仅预览，不保存，datasetId可为空）")
    @PostMapping("/parse")
    public DatasetConfigRes parseSql(@Validated @RequestBody DatasetConfigReq req) {
        try {
            return datasetConfigService.parseSql(req);
        } catch (JSQLParserException e) {
            log.error("SQL解析失败: {}", e.getMessage(), e);
            throw new NoticeException("SQL解析失败: " + e.getMessage());
        }
    }

    @ApiOperation("解析SQL并保存配置（替换原有配置，datasetId必须存在）")
    @PostMapping("/replace")
    @Transactional(rollbackFor = Exception.class)
    public DatasetConfigRes parseSqlAndSave(@Validated @RequestBody DatasetConfigReq req) {
        try {
            DatasetConfigRes res = datasetConfigService.parseSqlAndSave(req);
            Resp.notice("解析并保存成功，共生成 " + res.getTables().size() + " 个数据集，"
                    + res.getColumns().size() + " 个字段");
            return res;
        } catch (JSQLParserException e) {
            log.error("SQL解析失败: {}", e.getMessage(), e);
            throw new NoticeException("SQL解析失败: " + e.getMessage());
        }
    }

    @ApiOperation("解析SQL并新增保存配置（datasetId可为空，自动创建）")
    @PostMapping("/create")
    @Transactional(rollbackFor = Exception.class)
    public DatasetConfigRes parseSqlAndCreate(@Validated @RequestBody DatasetConfigReq req) {
        try {
            DatasetConfigRes res = datasetConfigService.parseSqlAndCreate(req);
            Resp.notice("新增并保存成功，datasetId=" + res.getDatasetId() + "，共生成 "
                    + res.getTables().size() + " 个数据集，" + res.getColumns().size() + " 个字段");
            return res;
        } catch (JSQLParserException e) {
            log.error("SQL解析失败: {}", e.getMessage(), e);
            throw new NoticeException("SQL解析失败: " + e.getMessage());
        }
    }

    @ApiOperation("获取指定datasetId的完整配置")
    @GetMapping("")
    public DatasetConfigRes getConfig(@RequestParam Long datasetId) {
        return datasetConfigService.getConfig(datasetId);
    }

    // ==================== 列配置管理 ====================

    @ApiOperation("获取指定datasetId的所有列配置")
    @GetMapping("/column/list")
    public List<SysDatasetColumn> getColumns(@RequestParam Long datasetId) {
        return datasetConfigService.getColumns(datasetId);
    }

    @ApiOperation("根据ID获取单个列配置")
    @GetMapping("/column")
    public SysDatasetColumn getColumnById(@RequestParam Long id) {
        return datasetConfigService.getColumnById(id);
    }

    @ApiOperation("新增列配置")
    @PostMapping("/column/add")
    @Transactional(rollbackFor = Exception.class)
    public SysDatasetColumn addColumn(@Validated @RequestBody DatasetColumnReq req) {
        SysDatasetColumn column = datasetConfigService.addColumn(req);
        Resp.notice("新增列配置成功");
        return column;
    }

    @ApiOperation("更新列配置")
    @PostMapping("/column/update")
    @Transactional(rollbackFor = Exception.class)
    public SysDatasetColumn updateColumn(@Validated @RequestBody DatasetColumnReq req) {
        SysDatasetColumn column = datasetConfigService.updateColumn(req);
        Resp.notice("更新列配置成功");
        return column;
    }

    @ApiOperation("删除列配置")
    @PostMapping("/column/delete")
    @Transactional(rollbackFor = Exception.class)
    public void deleteColumn(@RequestBody IdReqVO req) {
        datasetConfigService.deleteColumn(Long.parseLong(req.getId()));
        Resp.notice("删除列配置成功");
    }

    @ApiOperation("批量更新列显示顺序")
    @PostMapping("/column/update/order")
    @Transactional(rollbackFor = Exception.class)
    public void updateColumnsOrder(@RequestBody List<IdOrderReqVO> columns) {
        datasetConfigService.updateColumnsOrder(columns);
        Resp.notice("更新列顺序成功");
    }
}
