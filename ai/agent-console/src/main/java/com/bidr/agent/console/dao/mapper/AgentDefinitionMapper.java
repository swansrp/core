package com.bidr.agent.console.dao.mapper;

import com.bidr.agent.console.dao.entity.AgentDefinition;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;

/**
 * Title: AgentDefinitionMapper
 * Description: agent 资产定义数据访问（M14 棒6）。
 *
 * <p>本表读写都不需要注解 SQL：按 {@code agent_key} 单行取、全表列清单，
 * 两条都走 {@code lambdaQuery}；写腿的 NULL 语义见 {@code AgentDefinitionService}。</p>
 *
 * @author Sharp
 * @since 2026-10-13
 */
public interface AgentDefinitionMapper extends MyBaseMapper<AgentDefinition> {
}
