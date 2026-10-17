package com.bidr.insight.chatbi.controller;

import com.bidr.insight.chatbi.service.ChatBiRouterService;
import com.bidr.insight.chatbi.vo.ChatBiRouteItem;
import com.bidr.insight.chatbi.vo.ChatBiRouteReq;
import com.bidr.insight.chatbi.vo.ChatBiRouteRes;
import com.bidr.insight.chatbi.vo.ChatBiTableDescReq;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.validate.Validator;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Title: ChatBiRouteController
 * Description: 智能问数看板路由端点——按问题选板（候选注册制：写业务描述=注册）
 * 与候选注册管理（描述增删/AI 草稿生成），自 ChatBiController 按职责拆分，URL 前缀共用不变（前端零改动）。
 *
 * @author Sharp
 * @since 2026/8/20
 */
@Api(tags = "智能问数 - 看板路由")
@RequiredArgsConstructor
@RestController
@RequestMapping(value = "/web/insight/chatbi")
public class ChatBiRouteController {

    private final ChatBiRouterService chatBiRouterService;

    @ApiOperation(value = "获取路由候选目录（候选注册制：仅写了业务描述的看板）")
    @RequestMapping(value = "/route/catalog", method = RequestMethod.GET)
    public List<ChatBiRouteItem> getRouteCatalog() {
        return chatBiRouterService.getRouteCatalog();
    }

    @ApiOperation(value = "看板路由（按问题选出最相关看板）")
    @RequestMapping(value = "/route", method = RequestMethod.POST)
    public ChatBiRouteRes route(@Validated @RequestBody ChatBiRouteReq req) {
        return chatBiRouterService.route(req);
    }

    @ApiOperation(value = "保存看板业务描述（写描述=注册进候选，空白=注销）")
    @RequestMapping(value = "/route/desc", method = RequestMethod.POST)
    public void saveTableDesc(@Validated @RequestBody ChatBiTableDescReq req) {
        chatBiRouterService.saveTableDesc(req);
    }

    @ApiOperation(value = "全量看板与业务描述（候选注册管理页数据源）")
    @RequestMapping(value = "/route/desc/all", method = RequestMethod.GET)
    public List<ChatBiRouteItem> listPortalDesc() {
        return chatBiRouterService.listPortalDesc();
    }

    @ApiOperation(value = "AI 生成看板描述草稿（按语义目录汇总喂模型，不落库）")
    @RequestMapping(value = "/route/desc/generate", method = RequestMethod.GET)
    public String generateTableDesc(String tableId) {
        Validator.assertNotNull(tableId, ErrCodeSys.PA_PARAM_NULL, "表格编码");
        return chatBiRouterService.generateDesc(tableId);
    }
}
