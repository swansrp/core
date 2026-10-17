package com.bidr.agent.console.dao.repository;

import com.bidr.agent.console.dao.entity.AgentDefinition;
import com.bidr.agent.console.dao.mapper.AgentDefinitionMapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;

/**
 * Title: AgentDefinitionService
 * Description: agent 资产定义数据仓库（M14 棒6，仅读写方法，DDL 见 Schema）。
 *
 * <p>🔴 <b>写腿一律走 wrapper 显式 {@code set(...)}，不走 {@code updateById(entity)}</b>：
 * MyBatis-Plus 的字段策略会跳过实体里的 {@code null}，而本表最要紧的一次写恰恰就是
 * "把勾选列写回 NULL＝取消启用"。用实体更新会<b>静默命中不到那一栏</b>，
 * 界面显示已清空、库里还是旧集合——本项目为此踩过一次，这里整条腿都避开该形态。</p>
 *
 * <p>本表是框架平台表，<b>不参与空间行级隔离</b>：一个 agent_key 一行、全平台生效。</p>
 *
 * @author Sharp
 * @since 2026-10-13
 */
@Service
public class AgentDefinitionService extends BaseSqlRepo<AgentDefinitionMapper, AgentDefinition> {

    /** 清单行数上限（agent 数量级远不到这里，夹一道只是防误用成全表拉取） */
    private static final int MAX_ROWS = 500;

    /**
     * 按 agent_key 取定义行；无行返回 null（＝该 agent 没被勾选过＝三态的第一态）。
     */
    public AgentDefinition findByAgentKey(String agentKey) {
        if (!StringUtils.hasText(agentKey)) {
            return null;
        }
        return lambdaQuery()
                .eq(AgentDefinition::getAgentKey, agentKey.trim())
                .orderByAsc(AgentDefinition::getId)
                .last("limit 1")
                .one();
    }

    /** 全量定义行（管理页列表用；五列按 agent_key 升序，稳定便于对照） */
    public List<AgentDefinition> listAll() {
        return lambdaQuery()
                .orderByAsc(AgentDefinition::getAgentKey)
                .last("limit " + MAX_ROWS)
                .list();
    }

    /**
     * 写一个 agent 的五列勾选（存在则整组覆盖，不存在则建行）。
     *
     * <p><b>"整组覆盖"是有意的</b>：调用方给一次完整勾选状态，五列一起落。
     * 逐列补丁会让"这一列这次没传"在 NULL（未启用）与"沿用旧值"之间产生歧义，
     * 而这一列的三态本身就是本表的全部语义，不容歧义。</p>
     *
     * @return true＝写成了（建行或改行）；false＝agentKey 非法，什么都没动
     */
    public boolean writeSelection(AgentDefinition patch, String operator) {
        if (patch == null || !StringUtils.hasText(patch.getAgentKey())) {
            return false;
        }
        String agentKey = patch.getAgentKey().trim();
        AgentDefinition existing = findByAgentKey(agentKey);
        if (existing == null) {
            AgentDefinition row = new AgentDefinition();
            row.setAgentKey(agentKey);
            row.setCapabilityPackages(patch.getCapabilityPackages());
            row.setSystemSkills(patch.getSystemSkills());
            row.setExternalCapabilities(patch.getExternalCapabilities());
            row.setModelPurpose(patch.getModelPurpose());
            row.setPromptSegments(patch.getPromptSegments());
            row.setValid("1");
            return insert(row);
        }
        // 五列无条件 set（null 也写进去）——见类注释那条"取消启用"的判据
        return update(null, Wrappers.<AgentDefinition>lambdaUpdate()
                .eq(AgentDefinition::getAgentKey, agentKey)
                .set(AgentDefinition::getCapabilityPackages, patch.getCapabilityPackages())
                .set(AgentDefinition::getSystemSkills, patch.getSystemSkills())
                .set(AgentDefinition::getExternalCapabilities, patch.getExternalCapabilities())
                .set(AgentDefinition::getModelPurpose, patch.getModelPurpose())
                .set(AgentDefinition::getPromptSegments, patch.getPromptSegments())
                .set(AgentDefinition::getUpdateBy, StringUtils.hasText(operator) ? operator.trim() : null)
                .set(AgentDefinition::getUpdateAt, new Date()));
    }
}
