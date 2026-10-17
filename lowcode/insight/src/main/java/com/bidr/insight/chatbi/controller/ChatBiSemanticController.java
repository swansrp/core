package com.bidr.insight.chatbi.controller;

import com.bidr.insight.chatbi.service.ChatBiSemanticService;
import com.bidr.insight.chatbi.vo.ChatBiSemanticCatalog;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.validate.Validator;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Title: ChatBiSemanticController
 * Description: 智能问数语义目录端点——指标卡片清单 + 字段元数据（前端 chart-spec 合并语义层的数据源），
 * 自 ChatBiController 按职责拆分，URL 前缀共用不变（前端零改动）。
 *
 * @author Sharp
 * @since 2026/8/20
 */
@Api(tags = "智能问数 - 语义目录")
@RequiredArgsConstructor
@RestController
@RequestMapping(value = "/web/insight/chatbi")
public class ChatBiSemanticController {

    private final ChatBiSemanticService chatBiSemanticService;

    @ApiOperation(value = "获取语义目录")
    @RequestMapping(value = "/semantic", method = RequestMethod.GET)
    public ChatBiSemanticCatalog getSemanticCatalog(String tableId) {
        Validator.assertNotNull(tableId, ErrCodeSys.PA_PARAM_NULL, "表格编码");
        return chatBiSemanticService.getSemanticCatalog(tableId);
    }
}
