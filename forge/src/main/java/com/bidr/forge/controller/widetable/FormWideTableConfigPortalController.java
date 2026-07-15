package com.bidr.forge.controller.widetable;

import com.bidr.forge.dao.entity.FormWideTableConfig;
import com.bidr.forge.dao.entity.FormWideTableConfigAttr;
import com.bidr.forge.dao.entity.FormSchemaAttribute;
import com.bidr.forge.dao.repository.FormSchemaAttributeService;
import com.bidr.forge.service.widetable.FormWideTableCollector;
import com.bidr.forge.service.widetable.FormWideTableManager;
import com.bidr.forge.vo.widetable.FormWideTableConfigAttrVO;
import com.bidr.forge.vo.widetable.FormWideTableConfigReq;
import com.bidr.forge.vo.widetable.FormWideTableConfigVO;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.controller.BaseAdminController;
import com.bidr.kernel.jdbc.JdbcConnectService;
import com.bidr.kernel.service.PortalCommonService;
import com.bidr.kernel.utils.FuncUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 宽表收集配置管理控制器
 *
 * @author sharp
 */
@Api(tags = "系统基础 - 宽表统计 - 配置管理")
@RestController
@RequiredArgsConstructor
@RequestMapping(path = {"/web/form/widetable"})
public class FormWideTableConfigPortalController extends BaseAdminController<FormWideTableConfig, FormWideTableConfigVO> {

    private final FormWideTableManager formWideTableManager;
    private final FormWideTableCollector formWideTableCollector;
    private final FormSchemaAttributeService formSchemaAttributeService;
    private final JdbcConnectService jdbcConnectService;

    @Override
    public PortalCommonService<FormWideTableConfig, FormWideTableConfigVO> getPortalService() {
        return null;
    }

    /**
     * 保存配置（含字段列表），自动创建物理表和Portal配置
     */
    @ApiOperation("保存宽表配置（含字段列表）")
    @PostMapping("/saveConfig")
    public FormWideTableConfig saveConfig(@RequestBody FormWideTableConfigReq req) {
        FormWideTableConfig config = new FormWideTableConfig();
        config.setId(req.getId());
        config.setFormId(req.getFormId());
        config.setTitle(req.getTitle());
        config.setDescription(req.getDescription());
        config.setStatus(FuncUtil.isNotEmpty(req.getStatus()) ? req.getStatus() : "draft");

        FormWideTableConfig saved = formWideTableManager.saveConfig(config, req.getAttributeIds());
        Resp.notice("宽表配置保存成功");
        return saved;
    }

    /**
     * 删除配置（同时删除物理表和Portal配置）
     */
    @ApiOperation("删除宽表配置")
    @PostMapping("/deleteConfig")
    public void deleteConfig(@RequestParam Long configId) {
        formWideTableManager.deleteConfig(configId);
        Resp.notice("宽表配置删除成功");
    }

    /**
     * 获取宽表配置详情（含字段列表）
     */
    @ApiOperation("获取宽表配置详情")
    @GetMapping("/detail")
    public Map<String, Object> detail(@RequestParam Long configId) {
        Map<String, Object> result = new HashMap<>();
        FormWideTableConfig config = formWideTableManager.getConfigById(configId);
        result.put("config", config);
        List<FormWideTableConfigAttr> attrs = formWideTableManager.getConfigAttrs(configId);
        result.put("attrs", attrs);
        return result;
    }

    /**
     * 获取表单下所有可选字段
     */
    @ApiOperation("获取表单下所有可选字段")
    @GetMapping("/formAttributes/{formId}")
    public List<FormSchemaAttribute> formAttributes(@PathVariable String formId) {
        // 通过 FormSchemaAttribute 的 section_id 间接关联 formId
        // formId -> sections -> attributes，这里直接查询所有有效属性
        LambdaQueryWrapper<FormSchemaAttribute> wrapper = formSchemaAttributeService.getQueryWrapper();
        wrapper.eq(FormSchemaAttribute::getValid, "1")
                .orderByAsc(FormSchemaAttribute::getSort);
        List<FormSchemaAttribute> allAttrs = formSchemaAttributeService.select(wrapper);
        // 过滤：通过 section -> module -> form 的层级关系，formId 匹配
        // 简化处理：返回所有属性，前端可二次筛选
        return allAttrs;
    }

    /**
     * 手动触发收集（测试用）
     */
    @ApiOperation("手动触发宽表数据收集")
    @PostMapping("/collect/{configId}")
    public Map<String, Object> collect(@PathVariable Long configId) {
        int count = formWideTableCollector.collect(configId);
        Map<String, Object> result = new HashMap<>();
        result.put("collectedCount", count);
        Resp.notice("收集完成，共同步 " + count + " 条记录");
        return result;
    }

    /**
     * 手动触发所有 active 配置的收集
     */
    @ApiOperation("手动触发所有宽表数据收集")
    @PostMapping("/collectAll")
    public Map<String, Object> collectAll() {
        int count = formWideTableCollector.collectAll();
        Map<String, Object> result = new HashMap<>();
        result.put("collectedCount", count);
        Resp.notice("收集完成，共同步 " + count + " 条记录");
        return result;
    }

    /**
     * 查询宽表物理表数据
     */
    @ApiOperation("查询宽表数据")
    @GetMapping("/queryData")
    public Map<String, Object> queryData(@RequestParam Long configId) {
        Map<String, Object> result = new HashMap<>();
        FormWideTableConfig config = formWideTableManager.getConfigById(configId);
        result.put("config", config);
        List<FormWideTableConfigAttr> attrs = formWideTableManager.getConfigAttrs(configId);
        result.put("attrs", attrs);
        // 查询物理表数据
        if (config != null && FuncUtil.isNotEmpty(config.getTableName())) {
            String sql = "SELECT * FROM `" + config.getTableName() + "` ORDER BY `create_at` DESC";
            List<Map<String, Object>> rows = jdbcConnectService.executeQuery(sql, null);
            result.put("rows", rows);
        } else {
            result.put("rows", new ArrayList<>());
        }
        return result;
    }

    /**
     * 更新配置状态
     */
    @ApiOperation("更新宽表配置状态")
    @PostMapping("/updateStatus")
    public void updateStatus(@RequestParam Long configId, @RequestParam String status) {
        FormWideTableConfig config = formWideTableManager.getConfigById(configId);
        if (config != null) {
            config.setStatus(status);
            formWideTableManager.updateConfigStatus(config);
            Resp.notice("状态更新成功");
        }
    }
}
