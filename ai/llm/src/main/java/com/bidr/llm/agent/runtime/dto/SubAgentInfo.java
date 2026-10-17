package com.bidr.llm.agent.runtime.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 上游可委派的子 agent 类型（只读发现结果）。
 * <p>
 * 注意边界：这是**发现**用的只读视图——子 agent 的注册/启停/改配属于各家 runtime
 * 自己的管理面（OpenHands=profile API 与文件目录；agent-system=它自己的机制），
 * 框架 SPI 不定义注册协议。供管理面展示"这家上游有哪些可委派类型"。
 *
 * @author sharp
 * @since 2026/9/24
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubAgentInfo {

    /** 类型名（委派时引用它；如 OpenHands 的 TaskAction.subagent_type） */
    private String name;

    /** 一句话说明（何时该派给它） */
    private String description;

    /** 模型（inherit=随父） */
    private String model;

    /** 可用工具名清单（可空） */
    private List<String> tools;
}
