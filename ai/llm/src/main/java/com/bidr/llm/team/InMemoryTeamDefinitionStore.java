package com.bidr.llm.team;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Title: InMemoryTeamDefinitionStore
 * Description: 团队编排定义内存实现（类路径无 core/redis 时的默认装配，与业务自定义实现互斥；
 * 有 Redis/关系表的应用注册自己的 {@link TeamDefinitionStore} Bean 即自动让位）：
 * 单实例适用，进程重启团队定义清零（管理台重录即可，定义本身轻量）。
 * <p>
 * 读写与 InMemoryApprovalRecordStore 同手法：TreeMap 按 teamKey 有序（list 稳定升序），
 * save 即插入或覆盖，delete 不存在静默跳过。
 * </p>
 *
 * @author Sharp
 * @since 2026/11/1
 */
@Slf4j
@Component
@ConditionalOnMissingClass("com.bidr.platform.redis.service.RedisService")
public class InMemoryTeamDefinitionStore implements TeamDefinitionStore {

    /**
     * teamKey → 定义（TreeMap 保证 list 升序稳定）
     */
    private final Map<String, TeamDefinition> definitions = new TreeMap<>();

    @Override
    public void save(TeamDefinition definition) {
        if (definition == null || definition.getTeamKey() == null
                || definition.getTeamKey().trim().isEmpty()) {
            throw new IllegalArgumentException("团队标识不能为空");
        }
        definitions.put(definition.getTeamKey().trim(), definition);
    }

    @Override
    public TeamDefinition load(String teamKey) {
        return teamKey == null ? null : definitions.get(teamKey.trim());
    }

    @Override
    public List<TeamDefinition> list() {
        return new ArrayList<>(definitions.values());
    }

    @Override
    public void delete(String teamKey) {
        if (teamKey == null) {
            return;
        }
        definitions.remove(teamKey.trim());
    }
}
