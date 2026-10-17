package com.bidr.agent.console.manifest;

import com.bidr.llm.agent.ToolSurfaceFacts;

import java.util.Collections;
import java.util.List;

/**
 * Title: AgentManifestContributor
 * Description: 装配单业务语义贡献者（M14 棒2）。框架在工具面定形那一刻只交得出"会话身份 + 工具名清单"，
 * 剩下三件（哪档模型、注入了哪些技能与版本、提示词由哪几段拼成）全是业务侧知识，
 * 由实现本接口的业务 Bean 补进 {@link AgentManifestDraft}。
 *
 * <p><b>取值时机</b>：回调发生在 run 线程、{@code systemPrompt(...)} 之后（Java 实参左到右求值），
 * 与提示词渲染同线程同上下文 ⇒ 贡献者按同一批服务现读即得同一结果。若业务在渲染之后又改了配置，
 * 本表以定形时刻为准，不做事后回填。</p>
 *
 * <p><b>失败面</b>：贡献者抛错由台账侧统一吞掉并记 warn，缺哪一栏就留 NULL——
 * 装配单不许因为某一栏取不到而整行不落。</p>
 *
 * @author Sharp
 * @since 2026-10-12
 */
public interface AgentManifestContributor {

    /**
     * 本贡献者负责的 agent 注册键。
     *
     * @return 空清单＝对所有 agent 生效（通用贡献者，如模型档）；非空＝只对这些键生效
     */
    default List<String> agentKeys() {
        return Collections.emptyList();
    }

    /**
     * 补写装配单（就地改 draft；已有值可直接覆写，贡献者按 Spring 顺序依次执行）
     *
     * @param facts 框架侧事实（会话身份 + 工具名清单 + 原始 payload）
     * @param draft 待补的装配单草稿
     */
    void contribute(ToolSurfaceFacts facts, AgentManifestDraft draft);
}
