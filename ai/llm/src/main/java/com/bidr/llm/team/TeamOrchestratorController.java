package com.bidr.llm.team;

import com.bidr.kernel.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: TeamOrchestratorController
 * Description: 团队编排台端点（T4.2 团队编排门面）——团队定义管理与三模式运行入口：
 * <ul>
 *     <li>POST save：保存团队定义（teamKey 幂等覆盖），校验团队标识与成员标识；</li>
 *     <li>GET  list：团队定义全量清单（编排台列表数据源）；</li>
 *     <li>GET  get/{teamKey}：单条定义回读（编辑/审计）；</li>
 *     <li>DELETE delete/{teamKey}：删除定义（不存在幂等跳过）；</li>
 *     <li>POST run/{teamKey}：按定义同步执行一次协作（body=输入文本，JSON 字符串字面量或
 *         {"input":...} 均可），返回 {@link TeamRunResult}（每成员输出 + 最终结论）。</li>
 * </ul>
 * 装配与鉴权口径同流程编排台端点（/web/api 拦截链）；运行轨迹随结果内联返回（每成员一步），
 * 不另设轨迹端点。定义持久化未接入（TeamDefinitionStore 缺席）时管理端点报人话错误。
 *
 * @author Sharp
 * @since 2026/11/1
 */
@Slf4j
@RestController
@RequestMapping("/web/api/team/orchestrator")
@RequiredArgsConstructor
public class TeamOrchestratorController {

    private final ObjectProvider<TeamDefinitionStore> storeProvider;

    private final TeamRunner teamRunner;

    /**
     * 保存团队定义（teamKey 幂等覆盖），返回 {"ok":true,"teamKey":...}
     */
    @PostMapping("/save")
    public Map<String, Object> save(@RequestBody TeamDefinition definition) {
        if (definition == null || !StringUtils.hasText(definition.getTeamKey())) {
            throw new IllegalArgumentException("团队标识不能为空");
        }
        definition.setTeamKey(definition.getTeamKey().trim());
        if (definition.getMode() == null) {
            definition.setMode(TeamMode.PIPELINE);
        }
        if (definition.getMembers() != null) {
            for (TeamMember member : definition.getMembers()) {
                if (member == null || !StringUtils.hasText(member.getKey())) {
                    throw new IllegalArgumentException("团队成员标识（key）不能为空: " + definition.getTeamKey());
                }
                member.setKey(member.getKey().trim());
            }
        }
        requireStore().save(definition);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("ok", Boolean.TRUE);
        res.put("teamKey", definition.getTeamKey());
        return res;
    }

    /**
     * 团队定义全量清单（按 teamKey 升序）
     */
    @GetMapping("/list")
    public List<TeamDefinition> list() {
        return requireStore().list();
    }

    /**
     * 单条团队定义回读（编辑/审计）；不存在报人话错误
     */
    @GetMapping("/get/{teamKey}")
    public TeamDefinition get(@PathVariable("teamKey") String teamKey) {
        TeamDefinition definition = requireStore().load(teamKey);
        if (definition == null) {
            throw new ServiceException("团队不存在: " + teamKey);
        }
        return definition;
    }

    /**
     * 删除团队定义（不存在幂等跳过），返回 {"ok":true}
     */
    @DeleteMapping("/delete/{teamKey}")
    public Map<String, Object> delete(@PathVariable("teamKey") String teamKey) {
        requireStore().delete(teamKey);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("ok", Boolean.TRUE);
        return res;
    }

    /**
     * 按定义同步执行一次团队协作：请求体即输入文本（JSON 字符串字面量，或 {"input":...} 对象）；
     * 返回 TeamRunResult（每成员输出 + 最终结论；中途失败时 error 非空且保留已执行步骤）
     */
    @PostMapping("/run/{teamKey}")
    public TeamRunResult run(@PathVariable("teamKey") String teamKey,
                             @RequestBody(required = false) Object body) {
        TeamDefinition definition = requireStore().load(teamKey);
        if (definition == null) {
            throw new ServiceException("团队不存在: " + teamKey);
        }
        String input = inputOf(body);
        if (!StringUtils.hasText(input)) {
            throw new IllegalArgumentException("运行输入不能为空");
        }
        return teamRunner.run(definition, input);
    }

    /**
     * 请求体 → 输入文本：JSON 字符串字面量（body=input 直传）取原值；JSON 对象取 input 字段
     * （两种形态都收，页面与脚本调用均直觉）
     */
    private static String inputOf(Object body) {
        if (body instanceof Map) {
            Object input = ((Map<?, ?>) body).get("input");
            return input == null ? null : String.valueOf(input);
        }
        return body instanceof String ? (String) body : String.valueOf(body);
    }

    private TeamDefinitionStore requireStore() {
        TeamDefinitionStore store = storeProvider.getIfAvailable();
        if (store == null) {
            throw new ServiceException("应用未接入团队定义持久化（TeamDefinitionStore），团队编排台不可用");
        }
        return store;
    }
}
