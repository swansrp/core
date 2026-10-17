package com.bidr.insight.smartquery.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bidr.insight.smartquery.dao.entity.InsightAgent;
import com.bidr.insight.smartquery.dao.entity.InsightAgentAsset;
import com.bidr.insight.smartquery.dao.entity.InsightAgentTable;
import com.bidr.insight.smartquery.dao.entity.InsightAgentProposal;
import com.bidr.insight.smartquery.dao.repository.InsightAgentAssetService;
import com.bidr.insight.smartquery.dao.repository.InsightAgentProposalService;
import com.bidr.insight.smartquery.dao.repository.InsightAgentService;
import com.bidr.insight.smartquery.dao.repository.InsightAgentTableService;
import com.bidr.insight.smartquery.exec.SmartQueryJdbcExecutor;
import com.bidr.insight.smartquery.flow.AssetGenAgentDefinition;
import com.bidr.insight.smartquery.meta.CertifiedDraftMerger;
import com.bidr.insight.smartquery.meta.TableTemplateSupport;
import com.bidr.insight.smartquery.semantic.SemanticLayerRegistry;
import com.bidr.insight.smartquery.validate.conflict.ConfigCheckFinding;
import com.bidr.insight.smartquery.validate.conflict.ConfigCheckResolution;
import com.bidr.insight.smartquery.service.AgentAssetCacheService;
import com.bidr.forge.datasource.service.DataSourceCacheService;
import com.bidr.insight.smartquery.service.GenTaskContext;
import com.bidr.insight.smartquery.service.SmartAgentAssetGenerateService;
import com.bidr.insight.smartquery.service.SmartAgentMetaService;
import com.bidr.insight.smartquery.validate.AssetConsistencyValidator;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.controller.BaseAdminController;
import com.bidr.kernel.exception.NoticeException;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.vo.common.IdReqVO;
import com.bidr.llm.agent.session.AgentSessionService;
import com.bidr.llm.agent.session.AgentSessionState;
import com.bidr.platform.config.anno.ApiTrace;
import com.bidr.platform.vo.upload.PortalUploadProgressRes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.Data;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Title: AdminSmartAgentController
 * Description: 多 Agent 管理：通用 CRUD 经 BaseAdminController 提供（列表/新增/编辑/删除），
 * 自定义端点覆盖配置闭环：选表（/tables*）→ 生成资产草稿（/generate）→ 编辑草稿
 * （/asset*）→ 发布（/publish）→ 刷新缓存生效（/refresh，同参数管理流程）。
 * 数据源绑定：运行期问数执行按 Agent 绑定（ds_name）解析，多个 Agent 可共用同一数据源
 *
 * @author Sharp
 * @since 2026/8/18
 */
@Api(tags = "智能问数 - Agent 管理")
@RestController
@RequestMapping(path = {"/web/insight/agent/admin"})
public class AdminSmartAgentController extends BaseAdminController<InsightAgent, InsightAgent> {

    private static final java.util.regex.Pattern CODE_PATTERN =
            java.util.regex.Pattern.compile("^[a-z0-9][a-z0-9_-]*$");

    @Resource
    private InsightAgentService insightAgentService;
    @Resource
    private InsightAgentTableService insightAgentTableService;
    @Resource
    private InsightAgentAssetService insightAgentAssetService;
    @Resource
    private InsightAgentProposalService insightAgentProposalService;
    @Resource
    private SmartAgentMetaService smartAgentMetaService;
    @Resource
    private CertifiedDraftMerger certifiedDraftMerger;
    @Resource
    private TableTemplateSupport tableTemplateSupport;
    @Resource
    private SmartAgentAssetGenerateService smartAgentAssetGenerateService;
    @Resource
    private AgentAssetCacheService agentAssetCacheService;
    @Resource
    private DataSourceCacheService dataSourceCacheService;
    @Resource
    private SemanticLayerRegistry semanticLayerRegistry;
    @Resource
    private SmartQueryJdbcExecutor smartQueryJdbcExecutor;
    @Resource
    private AgentSessionService agentSessionService;

    /** 自主模式最近会话映射（agentCode → sessionId；旧 stop 端点转发通用停止，前端切通用端点后可移除） */
    private final Map<String, String> autonomousSessions = new ConcurrentHashMap<>();

    private final ObjectMapper om = new ObjectMapper();

    @Override
    public void beforeAdd(InsightAgent entity) {
        if (FuncUtil.isEmpty(entity.getAgentCode())) {
            throw new NoticeException("Agent 编码不能为空");
        }
        if (!CODE_PATTERN.matcher(entity.getAgentCode()).matches()) {
            throw new NoticeException("Agent 编码仅允许小写字母、数字、下划线、中划线");
        }
        InsightAgent existed = insightAgentService.selectOne(
                new QueryWrapper<InsightAgent>().eq("agent_code", entity.getAgentCode()));
        if (existed != null) {
            throw new NoticeException("Agent 编码已存在");
        }
        validateDsBinding(entity.getDsName());
    }

    @Override
    public void afterAdd(InsightAgent entity) {
        // 新绑定即时生效：清执行器绑定缓存条目（此前若有同名编码回落缓存需失效）
        smartQueryJdbcExecutor.evictAgentDs(entity.getAgentCode());
    }

    @Override
    public void beforeUpdate(InsightAgent entity) {
        // updateEntity 会先把库中原记录 merge 回实体；编码不可改，防止孤儿化选表/资产
        if (entity.getAgentId() != null) {
            InsightAgent stored = insightAgentService.selectById(entity.getAgentId());
            if (stored != null && FuncUtil.isNotEmpty(entity.getAgentCode())
                    && !stored.getAgentCode().equals(entity.getAgentCode())) {
                throw new NoticeException("Agent 编码不可修改");
            }
        }
        if (FuncUtil.isNotEmpty(entity.getDsName())) {
            validateDsBinding(entity.getDsName());
        }
    }

    @Override
    public void afterUpdate(InsightAgent entity) {
        // 绑定变更即时生效：失效执行器绑定缓存，下次执行重新解析
        smartQueryJdbcExecutor.evictAgentDs(entity.getAgentCode());
    }

    @Override
    public void beforeDelete(IdReqVO vo) {
        // 级联清理选表与资产，避免孤儿数据
        InsightAgent stored = insightAgentService.selectById(vo.getId());
        if (stored != null) {
            insightAgentTableService.delete(
                    new QueryWrapper<InsightAgentTable>().eq("agent_code", stored.getAgentCode()));
            insightAgentAssetService.delete(
                    new QueryWrapper<InsightAgentAsset>().eq("agent_code", stored.getAgentCode()));
            insightAgentProposalService.delete(
                    new QueryWrapper<InsightAgentProposal>().eq("agent_code", stored.getAgentCode()));
            // 失效执行器绑定缓存（删除后 selectOne 不命中不再入缓存，无旧值复活风险）
            smartQueryJdbcExecutor.evictAgentDs(stored.getAgentCode());
        }
    }

    @ApiOperation("列出绑定数据源下可选的物理表")
    @RequestMapping(path = {"/tables"}, method = {RequestMethod.POST})
    public List<Map<String, Object>> tables(@RequestBody InsightAgent vo) {
        return smartAgentMetaService.listTables(vo.getDsName());
    }

    @ApiOperation("已选表清单")
    @RequestMapping(path = {"/tables/selected"}, method = {RequestMethod.POST})
    public List<InsightAgentTable> selectedTables(@RequestBody InsightAgent vo) {
        requireAgentCode(vo.getAgentCode());
        return smartAgentMetaService.selectedTables(vo.getAgentCode());
    }

    @ApiOperation("敏感治理实体清单（敏感字段 tab 行源：entities 草稿优先，无骨架按选表派生 + 实时列元数据，不依赖骨架前置）")
    @RequestMapping(path = {"/govern/entities"}, method = {RequestMethod.POST})
    public List<Map<String, Object>> governEntities(@RequestBody InsightAgent vo) {
        requireAgentCode(vo.getAgentCode());
        return smartAgentMetaService.governEntities(vo.getAgentCode());
    }

    @ApiOperation("键/分区预选（实体确认页行源：预选业务键+依据、候选列、分区识别、索引全清单实时读）")
    @RequestMapping(path = {"/govern/keys"}, method = {RequestMethod.POST})
    public List<Map<String, Object>> governKeys(@RequestBody InsightAgent vo) {
        requireAgentCode(vo.getAgentCode());
        return smartAgentMetaService.governKeys(vo.getAgentCode());
    }

    @ApiOperation("保存选表（全量替换）")
    @RequestMapping(path = {"/tables/save"}, method = {RequestMethod.POST})
    public void saveTables(@RequestBody TablesSaveReq req) {
        if (FuncUtil.isEmpty(req.getAgentCode())) {
            throw new NoticeException("Agent 编码不能为空");
        }
        smartAgentMetaService.saveTables(req.getAgentCode(), req.getTables());
        Resp.notice("选表已保存");
    }

    @ApiOperation("按选表异步生成七类资产草稿（AsyncProcessInf 协议，进度经 /generate/progress 轮询）。"
            + "mode：skeleton 仅骨架 / pipeline 固定流水线逐类 LLM 生成 / autonomous AI 自主模式"
            + "（agent 会话驱动：返回 sessionId，事件流/暂停/恢复/停止经 /web/api/agent/session/*）；"
            + "未传 mode 时按 useLlm 兼容（false=skeleton、true=pipeline）")
    @RequestMapping(path = {"/generate"}, method = {RequestMethod.POST})
    public Map<String, Object> generate(@RequestBody GenerateReq req) {
        requireAgentCode(req.getAgentCode());
        String mode = resolveMode(req);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", mode);
        if (GenTaskContext.MODE_AUTONOMOUS.equals(mode)) {
            // 生成在途预检：锁占用直接拒请求带友好提示（前端 toast），避免先建会话再抢锁失败的秒死 FAILED 会话
            smartAgentAssetGenerateService.assertNotGenerating();
            // 自主模式接 agent 会话层：runTask 全程桥接会话（事件流/暂停/停止跨实例可达）
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put(AssetGenAgentDefinition.PAYLOAD_AGENT_CODE, req.getAgentCode());
            AgentSessionState state = agentSessionService.start(AssetGenAgentDefinition.AGENT_KEY,
                    payload, AccountContext.getDisplayName());
            autonomousSessions.put(req.getAgentCode(), state.getSessionId());
            result.put("sessionId", state.getSessionId());
            // Resp.notice 单参版会抛异常结束请求且 payload 为空，前端拿不到 sessionId 会误开 flow 抽屉；
            // 双参版把 result 挂上 payload（会话已启动的提示照弹）
            Resp.notice(result, "自主生成会话已启动（可暂停/恢复/停止）");
            return result;
        }
        // 前置校验同步完成；handleTask 经代理触发 @Async（避免同类自调用失效）
        List<InsightAgentTable> tables = smartAgentAssetGenerateService.beginGenerate(req.getAgentCode(), mode);
        smartAgentAssetGenerateService.handleTask(req.getAgentCode(), mode, tables, AccountContext.getDisplayName());
        Resp.notice("资产草稿生成已启动");
        return result;
    }

    /** 解析生成模式：显式 mode 优先；未传时按 useLlm 兼容（false=skeleton、true=pipeline） */
    private static String resolveMode(GenerateReq req) {
        if (FuncUtil.isNotEmpty(req.getMode())) {
            return req.getMode();
        }
        return req.isUseLlm() ? GenTaskContext.MODE_PIPELINE : GenTaskContext.MODE_SKELETON;
    }

    @ApiOperation("停止生成任务（全局单任务；写 Redis 停止键 + 中断属主实例任务线程，"
            + "已完成部分保留在草稿，重新发起可继续；自主会话优先转发通用停止）")
    @RequestMapping(path = {"/generate/stop"}, method = {RequestMethod.POST})
    public PortalUploadProgressRes generateStop(@RequestBody InsightAgent vo) {
        requireAgentCode(vo.getAgentCode());
        // 自主会话优先转发通用停止（会话状态即时收口）；Redis 停止键兑底（pipeline/skeleton 及他实例任务）
        String sessionId = autonomousSessions.remove(vo.getAgentCode());
        if (sessionId != null) {
            try {
                agentSessionService.stop(sessionId);
            } catch (Exception ignored) {
                // 会话已收口/不存在：仅走 Redis 停止键兑底
            }
        }
        return smartAgentAssetGenerateService.stopGenerate();
    }

    @ApiOperation("查询资产草稿生成进度（step/loaded/total/comments/logs；含孤儿检测——"
            + "运行中任务心跳超时判执行实例失联转 STOPPED，前端 AsyncProcess 组件轮询）")
    @ApiTrace(log = false)
    @RequestMapping(path = {"/generate/progress"}, method = {RequestMethod.POST})
    public PortalUploadProgressRes generateProgress(@RequestBody InsightAgent vo) {
        requireAgentCode(vo.getAgentCode());
        return smartAgentAssetGenerateService.queryProgress();
    }

    @ApiOperation("取单类资产（草稿编辑回显）")
    @RequestMapping(path = {"/asset"}, method = {RequestMethod.POST})
    public InsightAgentAsset asset(@RequestBody InsightAgentAsset vo) {
        requireAgentCode(vo.getAgentCode());
        validateAssetType(vo.getAssetType());
        return smartAgentMetaService.getAsset(vo.getAgentCode(), vo.getAssetType());
    }

    @ApiOperation("保存资产草稿（改稿即回到草稿态，须重新发布）")
    @RequestMapping(path = {"/asset/save"}, method = {RequestMethod.POST})
    public void saveAsset(@RequestBody InsightAgentAsset vo) {
        requireAgentCode(vo.getAgentCode());
        validateAssetType(vo.getAssetType());
        if (FuncUtil.isEmpty(vo.getContent())) {
            throw new NoticeException("资产内容不能为空");
        }
        try {
            om.readTree(vo.getContent());
        } catch (Exception e) {
            throw new NoticeException("资产内容不是合法 JSON: " + e.getMessage());
        }
        // 认证口径：语义三类手动保存自动盖章（手动编辑=重新认证）；
        // 骨架三类（实体/维度/码值域）不盖章，认证须页面逐条显式点击，认证的才进模板（显式值保留）
        smartAgentMetaService.saveAssetDraft(vo.getAgentCode(), vo.getAssetType(),
                certifiedDraftMerger.stampManualCertified(vo.getAssetType(), vo.getContent()));
        Resp.notice("草稿已保存，发布并刷新后生效");
    }

    @ApiOperation("表模板清单（「从模板导入」预览：当前数据源下模板的表全名/来源 Agent/更新时间）")
    @RequestMapping(path = {"/table-template/list"}, method = {RequestMethod.POST})
    public List<Map<String, Object>> tableTemplates(@RequestBody InsightAgent vo) {
        requireAgentCode(vo.getAgentCode());
        return smartAgentMetaService.tableTemplates(vo.getAgentCode());
    }

    @ApiOperation("保存到表模板（显式沉淀）：当前 entities 草稿已认证实体按 数据源+表 沉淀模板库；"
            + "人工填的不默认算认证，逐条点认证的才进模板；tables 非空时仅沉淀清单内表（认证本表按表同步）；"
            + "不随实体保存自动沉淀，防个别 Agent 特化配置污染共享模板")
    @RequestMapping(path = {"/table-template/save"}, method = {RequestMethod.POST})
    public Map<String, Object> saveTemplates(@RequestBody TemplateSaveReq req) {
        requireAgentCode(req.getAgentCode());
        return smartAgentMetaService.saveTemplates(req.getAgentCode(), req.getTables());
    }

    @ApiOperation("从表模板导入（显式套用）：模板人工结论套用到当前草稿未确认实体（已确认不覆盖）；"
            + "骨架链路选表时的自动套用不受影响")
    @RequestMapping(path = {"/table-template/import"}, method = {RequestMethod.POST})
    public Map<String, Object> importTemplates(@RequestBody InsightAgent vo) {
        requireAgentCode(vo.getAgentCode());
        return smartAgentMetaService.importTemplates(vo.getAgentCode());
    }

    @ApiOperation("模板库总览（跨数据源，模板库管理页）：数据源名/表全名/来源 Agent/更新时间/字段数")
    @RequestMapping(path = {"/table-template/all"}, method = {RequestMethod.POST})
    public List<Map<String, Object>> allTemplates() {
        return tableTemplateSupport.listAll();
    }

    @ApiOperation("模板详情（模板库管理页右侧编辑）：实体定义连同来源/更新时间返回")
    @RequestMapping(path = {"/table-template/detail"}, method = {RequestMethod.POST})
    public Map<String, Object> templateDetail(@RequestBody TemplateRefReq req) {
        Map<String, Object> detail = tableTemplateSupport.templateDetail(req.getDsName(), req.getTableName());
        if (detail == null) {
            throw new NoticeException("模板不存在: " + req.getTableName());
        }
        return detail;
    }

    @ApiOperation("模板手工编辑保存（模板库管理页）：实体身份以模板键为准回写；下次选表套用时生效")
    @RequestMapping(path = {"/table-template/update"}, method = {RequestMethod.POST})
    public Map<String, Object> updateTemplate(@RequestBody TemplateUpdateReq req) {
        return tableTemplateSupport.updateTemplate(req.getDsName(), req.getTableName(), req.getEntity());
    }

    @ApiOperation("删除模板（模板库管理页）：不影响已套用模板的 Agent 草稿")
    @RequestMapping(path = {"/table-template/delete"}, method = {RequestMethod.POST})
    public Map<String, Object> deleteTemplate(@RequestBody TemplateRefReq req) {
        return tableTemplateSupport.deleteTemplate(req.getDsName(), req.getTableName());
    }

    @ApiOperation("AI 评审报告读取（最新一份；无报告返空串）：评审面板/生成入口弱提醒共用；"
            + "报告由评审自主会话（asset-review-autonomous）的 submit_review 工具落盘，发布/校验不参与")
    @RequestMapping(path = {"/review/report"}, method = {RequestMethod.POST})
    public String reviewReport(@RequestBody InsightAgent vo) {
        requireAgentCode(vo.getAgentCode());
        String report = smartAgentMetaService.loadReviewReport(vo.getAgentCode());
        return report == null ? "" : report;
    }

    @ApiOperation("评审条目处理标记/撤销（人工消化闭环）：resolved 写回报告 JSON 落盘；"
            + "商榷项修正完后点「标记已处理」消除待办，重新评审覆盖报告时标记自然重置")
    @RequestMapping(path = {"/review/report/resolve"}, method = {RequestMethod.POST})
    public String resolveReviewItem(@RequestBody ReviewResolveReq req) {
        requireAgentCode(req.getAgentCode());
        return smartAgentMetaService.resolveReviewItem(req.getAgentCode(), req.getIndex(), req.isResolved());
    }

    @ApiOperation("单类资产 LLM 重生成（同步校验 + 异步生成；复用 /generate/progress 轮询，total=1；guidance 为人工指导语）")
    @RequestMapping(path = {"/asset/regenerate"}, method = {RequestMethod.POST})
    public void regenerateAsset(@RequestBody AssetRegenReq req) {
        requireAgentCode(req.getAgentCode());
        // 前置校验同步完成（类型/骨架草稿/数据源/模型）；handleRegenerate 经代理触发 @Async
        smartAgentAssetGenerateService.beginRegenerate(req.getAgentCode(), req.getAssetType());
        smartAgentAssetGenerateService.handleRegenerate(req.getAgentCode(), req.getAssetType(), req.getGuidance());
        Resp.notice("单资产重生成已启动");
    }

    @ApiOperation("资产编辑页 AI 补全：单条表单已填项保留、空缺项按该资产类型专属提示词+骨架补齐（同步返回补全后对象，不落盘；用户确认保存后经手动保存盖章认证，重生不覆盖）")
    @RequestMapping(path = {"/asset/ai-complete"}, method = {RequestMethod.POST})
    public Map<String, Object> aiCompleteAsset(@RequestBody AiCompleteReq req) {
        requireAgentCode(req.getAgentCode());
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("form", smartAgentAssetGenerateService.aiCompleteAsset(
                req.getAgentCode(), req.getAssetType(), req.getForm(), req.getGuidance()));
        return res;
    }

    /** SSE 不走 JSON 消息转换器，全局响应包装不影响本端点（同 chatbi /ask） */
    @ApiOperation("资产编辑页 AI 补全（SSE 流式，主入口）：tick 心跳+delta 增量实时下推（转圈可分辨死活），done 携带补全后 JSON；语义同同步版不落盘")
    @RequestMapping(path = {"/asset/ai-complete/stream"}, method = {RequestMethod.POST})
    public SseEmitter aiCompleteAssetStream(@RequestBody AiCompleteReq req) {
        requireAgentCode(req.getAgentCode());
        SseEmitter emitter = new SseEmitter(0L);
        smartAgentAssetGenerateService.aiCompleteAssetStream(
                req.getAgentCode(), req.getAssetType(), req.getForm(), req.getGuidance(), emitter);
        return emitter;
    }

    /** AI 补全请求体：form 为当前编辑行的部分字段对象（assetType 限指标/关系/概念），guidance 可空 */
    @Data
    public static class AiCompleteReq {
        private String agentCode;
        private String assetType;
        private JsonNode form;
        /** 人工指导语：可空（如「description 侧重业务口径」） */
        private String guidance;
    }

    @ApiOperation("上传资产包（skill 调试产物批量导入）：七类资产整体覆盖存草稿 → 直接发布 → 刷新缓存，上传即可用")
    @RequestMapping(path = {"/assets/import"}, method = {RequestMethod.POST})
    public Map<String, Object> importAssets(@RequestBody AssetsImportReq req) {
        requireAgentCode(req.getAgentCode());
        if (FuncUtil.isEmpty(req.getAssets())) {
            throw new NoticeException("资产包为空，未识别到任何资产文件");
        }
        for (AssetsImportReq.AssetItem item : req.getAssets()) {
            validateAssetType(item.getAssetType());
            if (FuncUtil.isEmpty(item.getContent())) {
                throw new NoticeException("资产 " + item.getAssetType() + ".json 内容为空");
            }
            try {
                om.readTree(item.getContent());
            } catch (Exception e) {
                throw new NoticeException("资产 " + item.getAssetType() + ".json 不是合法 JSON: " + e.getMessage());
            }
        }
        for (AssetsImportReq.AssetItem item : req.getAssets()) {
            // 导入口径同手动保存：语义三类自动盖章；骨架三类不默认认证（须页面逐条点认证）
            smartAgentMetaService.saveAssetDraft(req.getAgentCode(), item.getAssetType(),
                    certifiedDraftMerger.stampManualCertified(item.getAssetType(), item.getContent()));
        }
        smartAgentMetaService.publish(req.getAgentCode());
        agentAssetCacheService.refresh();
        semanticLayerRegistry.evictAll();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", req.getAssets().size());
        return result;
    }

    @ApiOperation("取 LLM 提示词模板（已保存值，未保存项以内置默认补齐；前端调优回显）")
    @RequestMapping(path = {"/prompts"}, method = {RequestMethod.POST})
    public Map<String, String> prompts(@RequestBody InsightAgent vo) {
        requireAgentCode(vo.getAgentCode());
        return smartAgentAssetGenerateService.effectivePrompts(vo.getAgentCode());
    }

    @ApiOperation("保存 LLM 提示词模板（配对推断 + 四类单资产模板，下次生成立即生效，无需重启）")
    @RequestMapping(path = {"/prompts/save"}, method = {RequestMethod.POST})
    public void savePrompts(@RequestBody PromptsSaveReq req) {
        requireAgentCode(req.getAgentCode());
        smartAgentAssetGenerateService.savePrompts(req.getAgentCode(), req.toPromptMap());
        Resp.notice("提示词已保存，下次生成立即生效");
    }

    @ApiOperation("内置默认提示词模板（前端「恢复默认」）")
    @RequestMapping(path = {"/prompts/defaults"}, method = {RequestMethod.POST})
    public Map<String, String> promptDefaults() {
        return smartAgentAssetGenerateService.defaultPrompts();
    }

    @ApiOperation("发布 Agent 资产并刷新运行期缓存")
    @RequestMapping(path = {"/publish"}, method = {RequestMethod.POST})
    public void publish(@RequestBody InsightAgent vo) {
        requireAgentCode(vo.getAgentCode());
        smartAgentMetaService.publish(vo.getAgentCode());
        agentAssetCacheService.refresh();
        semanticLayerRegistry.evictAll();
        Resp.notice("发布成功，已刷新生效");
    }

    @ApiOperation("资产草稿交叉校验（发布前预检）：error 阻断发布、warn 仅提示，每条带处理建议")
    @RequestMapping(path = {"/validate"}, method = {RequestMethod.POST})
    public Map<String, Object> validateAssets(@RequestBody InsightAgent vo) {
        requireAgentCode(vo.getAgentCode());
        AssetConsistencyValidator.Result vr = smartAgentMetaService.validateDrafts(vo.getAgentCode());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("hasErrors", vr.hasErrors());
        result.put("issues", vr.getIssues());
        return result;
    }

    @ApiOperation("发布校验错误一键自动修复：悬空引用类确定性修复后重校验；敏感字段/行权限类需手工")
    @RequestMapping(path = {"/validate/auto-fix"}, method = {RequestMethod.POST})
    public Map<String, Object> autoFixAssets(@RequestBody InsightAgent vo) {
        requireAgentCode(vo.getAgentCode());
        return smartAgentMetaService.autoFixDrafts(vo.getAgentCode());
    }

    @ApiOperation("配置自查（确定性无 LLM）：探人工配置疑似错误——单位矛盾/缺单位/码值域覆盖不全；"
            + "已裁决经验（单位已核/忽略码）自动免检，同表复用不重复提疑点；辅助非闸，异常返空清单")
    @RequestMapping(path = {"/conflicts/detect"}, method = {RequestMethod.POST})
    public List<ConfigCheckFinding> detectConflicts(@RequestBody InsightAgent vo) {
        requireAgentCode(vo.getAgentCode());
        return smartAgentMetaService.detectConflicts(vo.getAgentCode());
    }

    @ApiOperation("配置疑点逐条裁决：adopt 采纳建议（写配置）/ keep 维持原值（不改配置），两者都固化为经验标记；"
            + "不提供批量裁决（语义裁决须逐条点），返回裁决后剩余疑点清单")
    @RequestMapping(path = {"/conflicts/resolve"}, method = {RequestMethod.POST})
    public List<ConfigCheckFinding> resolveConflict(@RequestBody ConflictResolveReq req) {
        requireAgentCode(req.getAgentCode());
        if (req.getResolution() == null || FuncUtil.isEmpty(req.getResolution().getType())) {
            throw new NoticeException("裁决请求缺少疑点类型");
        }
        return smartAgentMetaService.resolveConflict(req.getAgentCode(), req.getResolution());
    }

    /** 疑点裁决请求体：resolution 为 ConfigCheckResolution（snake_case，与疑点清单出参对称） */
    @Data
    public static class ConflictResolveReq {
        private String agentCode;
        private ConfigCheckResolution resolution;
    }

    @ApiOperation("各 Agent 未发布草稿资产数（管理页行内徽标 + 发布提醒）")
    @RequestMapping(path = {"/draft-counts"}, method = {RequestMethod.POST})
    public Map<String, Long> draftCounts() {
        return insightAgentAssetService.draftCounts(SmartAgentMetaService.ASSET_TYPES);
    }

    @ApiOperation("刷新资产缓存（发布态重载进运行期）")
    @RequestMapping(path = {"/refresh"}, method = {RequestMethod.POST})
    public void refresh() {
        agentAssetCacheService.refresh();
        semanticLayerRegistry.evictAll();
        smartQueryJdbcExecutor.evictAgentDs(null);
        Resp.notice("Agent 资产缓存已刷新");
    }

    /** 绑定校验：数据源非空且已在数据源管理配置；运行期按 Agent 绑定解析（多 Agent 可共用同一数据源） */
    private void validateDsBinding(String dsName) {
        if (FuncUtil.isEmpty(dsName)) {
            throw new NoticeException("请绑定数据源");
        }
        dataSourceCacheService.getByName(dsName);
    }

    private void requireAgentCode(String agentCode) {
        if (FuncUtil.isEmpty(agentCode)) {
            throw new NoticeException("Agent 编码不能为空");
        }
    }

    private void validateAssetType(String assetType) {
        if (!SmartAgentMetaService.ASSET_TYPES.contains(assetType)) {
            throw new NoticeException("资产类型不合法: " + assetType);
        }
    }

    /** 保存选表请求体 */
    @Data
    public static class TablesSaveReq {
        private String agentCode;
        private List<InsightAgentTable> tables;
    }

    /** 沉淀表模板请求体：tables 为待沉淀表全名清单（空/缺省=全部已认证表，兼容旧前端） */
    @Data
    public static class TemplateSaveReq {
        private String agentCode;
        private List<String> tables;
    }

    /** 模板库管理请求体：模板身份 = 数据源名 + 表全名 */
    @Data
    public static class TemplateRefReq {
        private String dsName;
        private String tableName;
    }

    /** 评审条目处理标记请求体：index 为 items 原始下标 */
    @Data
    public static class ReviewResolveReq {
        private String agentCode;
        private int index;
        private boolean resolved;
    }

    /** 模板手工编辑保存请求体：entity 为完整实体定义（含字段级结论） */
    @Data
    public static class TemplateUpdateReq {
        private String dsName;
        private String tableName;
        private com.bidr.insight.smartquery.layer.EntityDef entity;
    }

    /** 生成草稿请求体 */
    @Data
    public static class GenerateReq {
        private String agentCode;
        /** 生成模式：skeleton 仅骨架 / pipeline 固定流水线 / autonomous AI 自主（显式传入优先） */
        private String mode;
        /** 兼容旧前端：未传 mode 时按 useLlm 推导（false=skeleton、true=pipeline） */
        private boolean useLlm;
    }

    /** 提示词模板保存请求体：配对推断 + 三类单资产模板 + 自主模式模板（键与 /prompts 返回一致） */
    @Data
    public static class PromptsSaveReq {
        private String agentCode;
        private String pairPrompt;
        private String metricsPrompt;
        private String relationsPrompt;
        private String conceptsPrompt;
        private String sensitiveFieldsPrompt;
        private String autonomousPrompt;

        /** 转为服务层保存口径的键值对（键名与 effectivePrompts 返回一致） */
        public Map<String, String> toPromptMap() {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("pairPrompt", pairPrompt);
            m.put("metricsPrompt", metricsPrompt);
            m.put("relationsPrompt", relationsPrompt);
            m.put("conceptsPrompt", conceptsPrompt);
            m.put("sensitiveFieldsPrompt", sensitiveFieldsPrompt);
            m.put("autonomousPrompt", autonomousPrompt);
            return m;
        }
    }

    /** 单资产重生成请求体：assetType 限指标/关系/概念/敏感字段，guidance 可空 */
    @Data
    public static class AssetRegenReq {
        private String agentCode;
        private String assetType;
        /** 人工指导语：拼入提示词引导模型（如「指标侧重合同额口径」） */
        private String guidance;
    }

    /** 资产包导入请求体：zip 解包后的七类资产清单（assetType 即文件名去 .json） */
    @Data
    public static class AssetsImportReq {
        private String agentCode;
        private List<AssetItem> assets;

        @Data
        public static class AssetItem {
            private String assetType;
            private String content;
        }
    }
}
