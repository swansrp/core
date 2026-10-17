package com.bidr.insight.chatbi.controller;

import com.bidr.insight.chatbi.service.ChatBiSensitiveService;
import com.bidr.insight.chatbi.vo.ChatBiSensitiveColumnRes;
import com.bidr.insight.chatbi.vo.ChatBiSensitiveSaveReq;
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
 * Title: ChatBiSensitiveController
 * Description: 智能问数敏感列配置端点——看板列敏感标记与配对替换列的查询/整板保存
 * （敏感治理前置链路，即改即生效），自 ChatBiController 按职责拆分，URL 前缀共用不变（前端零改动）。
 *
 * @author Sharp
 * @since 2026/8/20
 */
@Api(tags = "智能问数 - 敏感列配置")
@RequiredArgsConstructor
@RestController
@RequestMapping(value = "/web/insight/chatbi")
public class ChatBiSensitiveController {

    private final ChatBiSensitiveService chatBiSensitiveService;

    @ApiOperation(value = "敏感列配置页列清单（看板全量有效列 + 敏感标记与配对列回显）")
    @RequestMapping(value = "/sensitive/columns", method = RequestMethod.GET)
    public List<ChatBiSensitiveColumnRes> listSensitiveColumns(String tableId) {
        Validator.assertNotNull(tableId, ErrCodeSys.PA_PARAM_NULL, "表格编码");
        return chatBiSensitiveService.listColumnsWithFlag(tableId);
    }

    @ApiOperation(value = "保存敏感列配置（整板覆盖：勾选列+配对替换列，空清单即清空恢复）")
    @RequestMapping(value = "/sensitive/save", method = RequestMethod.POST)
    public void saveSensitiveColumns(@Validated @RequestBody ChatBiSensitiveSaveReq req) {
        chatBiSensitiveService.saveSensitiveColumns(req);
    }
}
