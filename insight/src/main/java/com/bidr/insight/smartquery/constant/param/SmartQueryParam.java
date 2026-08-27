package com.bidr.insight.smartquery.constant.param;

import com.bidr.kernel.constant.param.MetaParam;
import com.bidr.kernel.constant.param.Param;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Title: SmartQueryParam
 * Description: 语义层资产管理系统参数——@MetaParam 由 SysConfigCacheService 启动扫描自动补进 sys_config，
 * 管理页修改后经 ParamService.refresh() 广播生效，无需重启。
 *
 * @author Sharp
 * @since 2026/8/25
 */

@Getter
@MetaParam
@AllArgsConstructor
public enum SmartQueryParam implements Param {

    /**
     * 资产生成思考强度：自主生成/流水线生成/AI 补全共用（思考 token 上限），
     * 通用配置不随 Agent 行；0/非正/非法=最强不限制
     */
    GENERATE_THINKING_BUDGET("资产生成思考强度", "0",
            "资产生成链路（自主生成/流水线生成/AI 补全）的思考 token 上限；0 或非正=最强不限制，正值截断模型思考长尾"),

    /**
     * 评审思考强度：AI 评审会话专用（只读复核链路），0/非正/非法=最强不限制
     */
    REVIEW_THINKING_BUDGET("评审思考强度", "0",
            "AI 评审会话的思考 token 上限；0 或非正=最强不限制，正值截断模型思考长尾");

    private final String title;
    private final String defaultValue;
    private final String remark;
}
