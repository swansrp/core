package com.bidr.insight.constant.param;

import com.bidr.kernel.constant.param.MetaParam;
import com.bidr.kernel.constant.param.Param;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Title: ChatBiParam
 * Description: 智能问数系统参数——@MetaParam 由 SysConfigCacheService 启动扫描自动补进 sys_config，
 * 管理页修改后经 ParamService.refresh() 广播生效，无需重启。
 *
 * @author Sharp
 * @since 2026/8/15
 */

@Getter
@MetaParam
@AllArgsConstructor
public enum ChatBiParam implements Param {

    /**
     * 执行轨迹在 Redis 中的保留天数（按访问人隔离，超期自动过期）
     */
    TRACE_RETENTION_DAYS("问数轨迹保存天数(天)", "10", "流程执行轨迹在 Redis 中的保留天数，超期自动清理"),

    /**
     * 问答对话在 Redis 中的保留天数（按访问人隔离，超期自动过期）
     */
    CONVERSATION_RETENTION_DAYS("问数对话保存天数(天)", "10", "问答对话在 Redis 中的保留天数，超期自动清理");

    private final String title;
    private final String defaultValue;
    private final String remark;
}
