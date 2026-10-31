package com.bidr.llm.team;

import java.util.List;

/**
 * Title: TeamDefinitionStore
 * Description: 团队编排定义持久化 SPI（T4.2 团队编排门面）——业务模块提供存储实现（如落关系表）；
 * 框架自带 {@link InMemoryTeamDefinitionStore}（类路径无 core/redis 时的默认装配，单实例适用）。
 * save 以 teamKey 为幂等键（无则插入有则覆盖）。
 *
 * @author Sharp
 * @since 2026/11/1
 */
public interface TeamDefinitionStore {

    /**
     * 保存团队定义（teamKey 为空时拒绝；同 teamKey 再存 = 覆盖）
     */
    void save(TeamDefinition definition);

    /**
     * 按 teamKey 读单条；不存在返回 null
     */
    TeamDefinition load(String teamKey);

    /**
     * 全量清单（按 teamKey 升序，稳定展示顺序）
     */
    List<TeamDefinition> list();

    /**
     * 删除团队定义；teamKey 不存在时静默跳过（幂等）
     */
    void delete(String teamKey);
}
