package com.bidr.insight.chatbi.service;

import com.bidr.admin.dao.entity.SysPortal;
import com.bidr.admin.dao.repository.SysPortalService;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.insight.chatbi.dao.repository.ChatBiTableDescService;
import com.bidr.insight.chatbi.flow.ChatBiRouteFlowDefinition;
import com.bidr.insight.chatbi.vo.ChatBiRouteItem;
import com.bidr.insight.chatbi.vo.ChatBiRouteReq;
import com.bidr.insight.chatbi.vo.ChatBiRouteRes;
import com.bidr.insight.chatbi.vo.ChatBiSemanticCatalog;
import com.bidr.insight.chatbi.vo.ChatBiTableDescReq;
import com.bidr.insight.smartquery.vo.SemanticField;
import com.bidr.insight.chatbi.vo.SemanticIndicator;
import com.bidr.insight.chatbi.vo.SemanticIndicatorGroup;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.validate.Validator;
import com.bidr.llm.flow.FlowContext;
import com.bidr.llm.flow.FlowEngine;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Title: ChatBiRouterService
 * Description: 智能问数全局路由入口——组装执行上下文后交 {@link FlowEngine} 跑 route 链
 * （默认：route_catalog 候选目录 → llm 结合当前看板与对话上下文选板 → extract tableId → output），
 * 前端命中后进入单看板问答流程。路由提示词与链路结构存库可经管理页编辑。
 * <p>
 * 候选注册制：insight_chatbi_table_desc 写了业务描述的看板才是路由候选（描述即路由判断依据），
 * 系统管理类看板天然无人写描述而自然出局；候选的注册/注销在管理页"看板描述"Tab 维护，
 * AI 草稿（{@link #generateDesc}）辅助运营起草。
 * </p>
 *
 * @author Sharp
 * @since 2026/8/15
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatBiRouterService {

    private final FlowEngine flowEngine;

    private final SysPortalService sysPortalService;

    private final ChatBiTableDescService chatBiTableDescService;

    private final ChatBiSemanticService chatBiSemanticService;

    /**
     * 生成描述草稿的非流式模型通道（与 llm 结点同源，缺失时调用即报错）
     */
    private final ObjectProvider<ChatLanguageModel> modelProvider;

    /**
     * 路由候选目录（候选注册制）：只返回写了业务描述的看板——运营写描述即注册进候选，
     * 描述本身是路由判断依据；看板已删除的悬挂注册不会出现。
     * 与 route 链的 route_catalog 结点同源语义，/route/catalog 端点复用
     */
    public List<ChatBiRouteItem> getRouteCatalog() {
        List<ChatBiRouteItem> catalog = new ArrayList<>();
        for (ChatBiRouteItem item : listPortalDesc()) {
            if (StringUtils.hasText(item.getDescription())) {
                catalog.add(item);
            }
        }
        return catalog;
    }

    /**
     * 全量看板 + 业务描述（候选注册管理页数据源）：写描述=注册进路由候选，空白=注销
     */
    public List<ChatBiRouteItem> listPortalDesc() {
        List<SysPortal> portals = sysPortalService.select(sysPortalService.getQueryWrapper()
                .orderByAsc(SysPortal::getDisplayName));
        Map<String, String> descMap = chatBiTableDescService.getDescriptionMap();
        List<ChatBiRouteItem> items = new ArrayList<>(portals.size());
        for (SysPortal portal : portals) {
            items.add(buildItem(portal, descMap.get(portal.getName())));
        }
        return items;
    }

    /**
     * AI 生成看板描述草稿：汇总看板元数据（中文名 + 语义目录摘要）喂大模型产出一句话业务描述，
     * 只返回草稿不落库，由管理页人工修改后经 {@link #saveTableDesc} 保存
     */
    public String generateDesc(String tableId) {
        SysPortal portal = sysPortalService.getByName(tableId, null);
        Validator.assertNotNull(portal, ErrCodeSys.PA_DATA_NOT_EXIST, "看板视图");
        ChatLanguageModel model = modelProvider.getIfUnique();
        if (model == null) {
            throw new IllegalStateException("未找到唯一的 ChatLanguageModel Bean，请检查 llm 模块配置");
        }
        String answer = model.generate(buildGeneratePrompt(portal, chatBiSemanticService.getSemanticCatalog(tableId)));
        return answer == null ? "" : answer.trim();
    }

    /**
     * 生成描述提示词：把语义目录压成 指标卡片/维度/指标/筛选组/字段 名称清单（各自截断），
     * 要求 20~50 字一句话、无引号无前后缀，正文供路由模型判断看板相关性
     */
    private String buildGeneratePrompt(SysPortal portal, ChatBiSemanticCatalog catalog) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是 ERP 智能问数系统的看板注册助手。下面是一个数据看板的元数据，")
                .append("请写一句中文业务描述（20~50字，不要引号、不要任何前后缀），")
                .append("概括该看板承载的业务域与分析维度，供路由大模型判断用户问题与该看板的相关性。\n\n");
        prompt.append("看板中文名：").append(portal.getDisplayName()).append('\n');
        prompt.append("看板编码：").append(portal.getName()).append('\n');

        Set<String> cardTitles = new LinkedHashSet<>();
        Set<String> dimensions = new LinkedHashSet<>();
        Set<String> metrics = new LinkedHashSet<>();
        for (SemanticIndicator indicator : safeList(catalog.getIndicators())) {
            collectNames(indicator.getTitle(), cardTitles);
            collectNames(indicator.getDimensions(), dimensions);
            collectNames(indicator.getMetrics(), metrics);
        }
        appendNames(prompt, "指标卡片", cardTitles, 20);
        appendNames(prompt, "分析维度", dimensions, 20);
        appendNames(prompt, "统计指标", metrics, 30);

        Set<String> groupTitles = new LinkedHashSet<>();
        Set<String> groupItems = new LinkedHashSet<>();
        for (SemanticIndicatorGroup group : safeList(catalog.getIndicatorGroups())) {
            collectNames(group.getTitle(), groupTitles);
            for (SemanticIndicatorGroup.Item item : safeList(group.getItems())) {
                collectNames(item.getTitle(), groupItems);
            }
        }
        appendNames(prompt, "筛选组", groupTitles, 10);
        appendNames(prompt, "筛选项", groupItems, 30);

        Set<String> fieldLabels = new LinkedHashSet<>();
        for (SemanticField field : safeList(catalog.getFields())) {
            collectNames(field.getLabel(), fieldLabels);
        }
        appendNames(prompt, "筛选字段", fieldLabels, 30);

        prompt.append("\n直接输出这一句话描述，不要解释。");
        return prompt.toString();
    }

    /**
     * 保存看板业务描述（描述空白归一化为 null 即清除=注销候选），供路由目录引用
     */
    public void saveTableDesc(ChatBiTableDescReq req) {
        SysPortal portal = sysPortalService.getByName(req.getTableId(), null);
        Validator.assertNotNull(portal, ErrCodeSys.PA_DATA_NOT_EXIST, "看板视图");
        String description = StringUtils.hasText(req.getDescription()) ? req.getDescription().trim() : null;
        chatBiTableDescService.saveDescription(req.getTableId(), description);
    }

    private ChatBiRouteItem buildItem(SysPortal portal, String description) {
        ChatBiRouteItem item = new ChatBiRouteItem();
        item.setTableId(portal.getName());
        item.setPortalName(portal.getName());
        item.setTitle(portal.getDisplayName());
        item.setDescription(description);
        return item;
    }

    private <T> List<T> safeList(List<T> list) {
        return list == null ? Collections.emptyList() : list;
    }

    private void collectNames(String name, Set<String> target) {
        if (StringUtils.hasText(name)) {
            target.add(name.trim());
        }
    }

    private void collectNames(List<String> names, Set<String> target) {
        if (names == null) {
            return;
        }
        for (String name : names) {
            collectNames(name, target);
        }
    }

    /**
     * 拼接一行“标题（总数）：a、b、c…（超限截断）”名称清单
     */
    private void appendNames(StringBuilder prompt, String title, Set<String> names, int limit) {
        if (names.isEmpty()) {
            return;
        }
        prompt.append(title).append('（').append(names.size()).append("）：");
        int count = 0;
        for (String name : names) {
            if (count >= limit) {
                prompt.append('…');
                break;
            }
            if (count > 0) {
                prompt.append('、');
            }
            prompt.append(name);
            count++;
        }
        prompt.append('\n');
    }

    /**
     * 一次路由选板：route 链同步执行（llm 结点非流式），结果经 output 结点写回 ctx 后在此取回；
     * 链上 extract 未命中时 tableId 为空，由前端引导用户手动选板，选板失败不算系统错误
     */
    public ChatBiRouteRes route(ChatBiRouteReq req) {
        ChatBiRouteRes res = new ChatBiRouteRes();
        if (getRouteCatalog().isEmpty()) {
            return res;
        }
        FlowContext context = new FlowContext(null);
        context.setVariable("question", req.getQuestion());
        // 访问人（人名优先回落工号，轨迹按人隔离记录；route 链在 HTTP 线程同步执行，此处可直接取当前登录人）
        context.setOperator(AccountContext.getDisplayName());
        // 模板【当前看板】段占位，无当前看板时显示（无）
        context.setVariable("currentTableId",
                StringUtils.hasText(req.getCurrentTableId()) ? req.getCurrentTableId() : "（无）");
        context.setHistory(req.getHistory());
        flowEngine.execute(ChatBiRouteFlowDefinition.FLOW_KEY, context);
        Object tableId = context.getOutput().get("tableId");
        if (tableId != null) {
            res.setTableId(String.valueOf(tableId));
            Object portalName = context.getOutput().get("portalName");
            res.setPortalName(portalName == null ? null : String.valueOf(portalName));
        }
        return res;
    }
}
