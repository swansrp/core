package com.bidr.insight.chatbi.flow.executor;

import com.bidr.admin.dao.entity.SysPortal;
import com.bidr.admin.dao.repository.SysPortalService;
import com.bidr.insight.chatbi.dao.repository.ChatBiTableDescService;
import com.bidr.insight.chatbi.vo.ChatBiRouteItem;
import com.bidr.llm.flow.FlowContext;
import com.bidr.llm.flow.FlowGraph;
import com.bidr.llm.flow.FlowNodeMeta;
import com.bidr.llm.flow.executor.FlowNodeExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: RouteCatalogNodeExecutor
 * Description: route_catalog 结点——构建看板路由候选目录（候选注册制：insight_chatbi_table_desc
 * 写了业务描述的看板才是候选，描述即路由判断依据）：
 * 结构化清单写入 routeCatalogItems（extract 结点 tableId 匹配用），
 * 编号清单文本写入 routeCatalog（llm 模板 {@code {{routeCatalog}}} 引用）。
 * <p>
 * 目录组装逻辑与 ChatBiRouterService.getRouteCatalog 同源（此处置入避免引擎与路由服务循环依赖）。
 * </p>
 *
 * @author Sharp
 * @since 2026/8/15
 */
@Component
@RequiredArgsConstructor
public class RouteCatalogNodeExecutor implements FlowNodeExecutor {

    private final SysPortalService sysPortalService;

    private final ChatBiTableDescService chatBiTableDescService;

    @Override
    public String type() {
        return "route_catalog";
    }

    /**
     * 工作台元数据：无配置项（候选目录由"看板描述"注册制维护，非结点参数）
     */
    @Override
    public FlowNodeMeta nodeMeta() {
        return FlowNodeMeta.of(type(), type(), "看板路由候选目录写入 routeCatalog");
    }

    @Override
    public boolean execute(FlowGraph.FlowNode node, FlowContext context) {
        // 候选白名单：写了业务描述的看板（getDescriptionMap 仅返回描述非空记录）
        Map<String, String> descMap = chatBiTableDescService.getDescriptionMap();
        Map<String, SysPortal> portalMap = new HashMap<>();
        for (SysPortal portal : sysPortalService.select(sysPortalService.getQueryWrapper())) {
            portalMap.put(portal.getName(), portal);
        }
        List<ChatBiRouteItem> catalog = new ArrayList<>(descMap.size());
        for (Map.Entry<String, String> entry : descMap.entrySet()) {
            SysPortal portal = portalMap.get(entry.getKey());
            if (portal == null) {
                // 看板已删除的悬挂注册跳过
                continue;
            }
            ChatBiRouteItem item = new ChatBiRouteItem();
            item.setTableId(portal.getName());
            item.setPortalName(portal.getName());
            item.setTitle(portal.getDisplayName());
            item.setDescription(entry.getValue());
            catalog.add(item);
        }
        catalog.sort(Comparator.comparing(ChatBiRouteItem::getTitle,
                Comparator.nullsLast(Comparator.naturalOrder())));
        context.setVariable("routeCatalogItems", catalog);
        context.setVariable("routeCatalog", renderCatalog(catalog));
        return true;
    }

    /**
     * 轨迹摘要：候选看板条数
     */
    @Override
    public String traceSummary(FlowGraph.FlowNode node, FlowContext context, boolean suspended) {
        List<?> items = context.get("routeCatalogItems", List.class);
        return "看板目录 " + (items == null ? 0 : items.size()) + " 项";
    }

    /**
     * 编号清单文本："1. tableId=xx，portalName=xx，名称：xx，描述：xx"（候选注册制下描述必有）
     */
    private String renderCatalog(List<ChatBiRouteItem> catalog) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < catalog.size(); i++) {
            ChatBiRouteItem item = catalog.get(i);
            text.append(i + 1).append(". tableId=").append(item.getTableId())
                    .append("，portalName=").append(item.getPortalName());
            if (StringUtils.hasText(item.getTitle())) {
                text.append("，名称：").append(item.getTitle());
            }
            if (StringUtils.hasText(item.getDescription())) {
                text.append("，描述：").append(item.getDescription());
            }
            text.append('\n');
        }
        return text.toString();
    }
}
