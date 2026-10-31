package com.bidr.llm.flow;

import lombok.Data;

/**
 * Title: FlowVersionSaveReq
 * Description: 流程版本保存请求——编排页"另存版本"的入参；graphJson 为原始 DAG JSON 串
 * （原样落库，落库前经引擎结构校验），version 缺省 = 最新版本 + 1，传已有版本号 = 覆盖该版本。
 * flowKey 必须是已注册的封闭集成员（{@link FlowManagerService} 校验）。
 *
 * @author Sharp
 * @since 2026/11/1
 */
@Data
public class FlowVersionSaveReq {

    /**
     * 流程标识（已注册的 flowKey）
     */
    private String flowKey;

    /**
     * 版本名称（空则回落注册显示名）
     */
    private String name;

    /**
     * DAG 定义 JSON 串（nodes+edges，落库前经引擎结构校验）
     */
    private String graphJson;

    /**
     * 目标版本号（缺省 = 最新版本 + 1；传已有版本 = 覆盖）
     */
    private Integer version;
}
