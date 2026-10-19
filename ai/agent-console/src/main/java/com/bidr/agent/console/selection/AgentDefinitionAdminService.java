package com.bidr.agent.console.selection;

import com.bidr.agent.console.capability.AgentCapabilityPackageRegistry;
import com.bidr.agent.console.dao.entity.AgentDefinition;
import com.bidr.agent.console.dao.repository.AgentDefinitionService;
import com.bidr.agent.console.dto.AgentDefinitionView;
import com.bidr.agent.console.dto.AgentSelectionCmd;
import com.bidr.kernel.exception.ServiceException;
import com.bidr.kernel.utils.BeanUtil;
import com.bidr.llm.agent.OperatorResolver;
import com.bidr.llm.agent.registry.AgentDescriptor;
import com.bidr.llm.agent.registry.AgentRegistryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * Title: AgentDefinitionAdminService
 * Description: agent 勾选的读写面（M14 棒6-E），平台管理员专属。
 *
 * <p><b>闸门在服务层不在控制器</b>：与 {@code SystemSkillAdminService} 同一口径——写面一旦有第二个入口
 * （后续 CLI／导入），控制器层的闸会漏，服务层的闸漏不掉。管理员判定反射取 permitService，
 * bean 取不到＝按非管理员（fail closed）。读腿同样过闸：工具面配置是平台运营信息，
 * 而"读得到却写不动"的页面只会让管理员误判自己的身份。</p>
 *
 * <p>🔴 <b>拒绝一律 {@link ServiceException}</b>（非零码），不用 NoticeException——
 * notice 在前端落的是"成功＋一段提示"，管理员会以为勾上了。</p>
 *
 * <p><b>agentKey 的存在性以运行期注册表为正源</b>：注册表里没有这个键、库里也没有这一行 ⇒ 拒绝新建。
 * 理由不是洁癖，而是勾选按 agentKey 匹配会话，一个拼错的键会落成一行的"看起来配好了、永远不会被读到"的配置——
 * 静默失效比报错难查得多。反过来<b>注册表里没有但库里已有行</b>（agent 退役后留下的历史行）允许改写：
 * 那行的回退动作恰恰是"把列清空"，而清空也是一次写。</p>
 *
 * <p><b>只有 {@code capability_packages} 校验包名</b>：它是唯一被运行期读走的一列，
 * 未注册的包名进了那一列，选择器按窄处理（该包的工具本次不给）——写面在这里就拒掉，
 * 等于把"事后从日志里查为什么少了三只工具"提前成"当场告诉管理员哪个包名不存在"。
 * 其余四列本棒只登记、没有消费方，也就没有可校验的登记表；等哪一维真上线再收紧，
 * 现在按不存在的清单拒人，只会让未来的实现受今天的数据结构牵制。
 * {@code system_skills} 现在确有台账了（{@code sys_skill_asset}，页面候选源＝
 * {@code SystemSkillAdminService#listReadableNames}），但<b>写面仍不校验</b>：这一列不参与运行期，
 * 拿"今天已发布的名字"去卡写入，会把"某个包下线后顺手改另一栏"的保存一起挡掉——
 * 收紧的时机是这一维真被读走那一拍，跟 {@code capability_packages} 同一条判据。</p>
 *
 * <p><b>写入后回读返回</b>：响应里的三态就是库里那一行的三态，页面不必再发一次 GET 自查。</p>
 *
 * @author Sharp
 * @since 2026-10-13
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "agent-console.selection", name = "enabled", havingValue = "true")
public class AgentDefinitionAdminService {

    /** agent_key 列宽（varchar(64)）：超宽会被截成"看着对、查不到"的键，勾选静默失效 */
    private static final int MAX_KEY_CHARS = 64;

    /** 单栏名单上限：真实勾选是个位数，这一道只挡"把整份工具清单灌进一列"的误用（列是 text，不设闸等于任由请求体撑爆它） */
    private static final int MAX_NAMES = 64;

    private static final int MAX_NAME_CHARS = 64;

    private final AgentDefinitionService definitions;

    private final AgentCapabilityPackageRegistry packages;

    private final AgentRegistryService agentRegistry;

    private final ObjectProvider<OperatorResolver> operatorResolver;

    /**
     * 清单：以注册表为主序（在跑的 agent 一律露出来，哪怕从没勾过），
     * 末尾补"库里有行但注册表查不到"的历史行（{@code registered=false}）。
     */
    public List<AgentDefinitionView> list() {
        requirePlatformAdmin("看");
        Map<String, AgentDefinition> rows = indexByAgentKey(definitions.listAll());
        Set<String> covered = new HashSet<>();
        List<AgentDefinitionView> views = new ArrayList<>();
        for (AgentDescriptor descriptor : agentRegistry.all()) {
            String key = descriptor == null ? null : descriptor.getAgentCode();
            if (!StringUtils.hasText(key) || !covered.add(key)) {
                continue;
            }
            views.add(view(key, rows.get(key), descriptor));
        }
        for (Map.Entry<String, AgentDefinition> entry : rows.entrySet()) {
            if (!covered.add(entry.getKey())) {
                continue;
            }
            log.info("勾选表里有注册表查不到的历史行（该 agent 已不在运行期注册表里，本列不参与任何会话）: agent={}",
                    entry.getKey());
            views.add(view(entry.getKey(), entry.getValue(), null));
        }
        return views;
    }

    /** 单个 agent 的勾选视图：注册表与勾选表都查不到＝拒绝（页面不该为拼错的键渲染一张空表） */
    public AgentDefinitionView get(String agentKey) {
        requirePlatformAdmin("看");
        String key = requireKey(agentKey);
        AgentDescriptor descriptor = agentRegistry.get(key);
        AgentDefinition row = definitions.findByAgentKey(key);
        if (descriptor == null && row == null) {
            throw new ServiceException("运行期注册表与勾选表里都没有这个 agent: " + key);
        }
        return view(key, row, descriptor);
    }

    /**
     * 整组覆盖五列勾选（五栏一次给齐，含 null＝该维置回未启用）。
     * 返回写后回读的视图，与 {@link #get(String)} 同一份三态口径。
     */
    public AgentDefinitionView save(String agentKey, AgentSelectionCmd cmd) {
        requirePlatformAdmin("改");
        String key = requireKey(agentKey);
        if (cmd == null) {
            throw new ServiceException("勾选请求体不能为空（整组覆盖＝五栏都要给，不给等于全置未启用）: " + key);
        }
        AgentDescriptor descriptor = agentRegistry.get(key);
        if (descriptor == null && definitions.findByAgentKey(key) == null) {
            throw new ServiceException("运行期注册表里没有这个 agent，拒绝新建勾选行（键名请以清单页为准）: " + key);
        }
        validateNames("capability_packages", cmd.getCapabilityPackages());
        validateNames("system_skills", cmd.getSystemSkills());
        validateNames("external_capabilities", cmd.getExternalCapabilities());
        validateNames("model_purpose", cmd.getModelPurpose());
        validateNames("prompt_segments", cmd.getPromptSegments());
        requireRegisteredPackages(cmd.getCapabilityPackages());

        AgentDefinition patch = new AgentDefinition();
        patch.setAgentKey(key);
        patch.setCapabilityPackages(SelectionColumn.toColumnValue(cmd.getCapabilityPackages()));
        patch.setSystemSkills(SelectionColumn.toColumnValue(cmd.getSystemSkills()));
        patch.setExternalCapabilities(SelectionColumn.toColumnValue(cmd.getExternalCapabilities()));
        patch.setModelPurpose(SelectionColumn.toColumnValue(cmd.getModelPurpose()));
        patch.setPromptSegments(SelectionColumn.toColumnValue(cmd.getPromptSegments()));
        String operator = currentOperator();
        definitions.writeSelection(patch, operator);
        log.info("agent 勾选整组覆盖落库: agent={} operator={} 能力包={} 系统技能={} 外部能力={} 模型用途={} 提示词段={}",
                key, operator, patch.getCapabilityPackages(), patch.getSystemSkills(),
                patch.getExternalCapabilities(), patch.getModelPurpose(), patch.getPromptSegments());
        return view(key, definitions.findByAgentKey(key), descriptor);
    }

    // ---------------------------------------------------------------- 内部

    private AgentDefinitionView view(String key, AgentDefinition row, AgentDescriptor descriptor) {
        AgentDefinitionView view = AgentDefinitionView.of(key, row);
        if (descriptor == null) {
            view.setRegistered(false);
            return view;
        }
        view.setDisplayName(descriptor.getDisplayName());
        view.setKind(descriptor.getKind() == null ? null : descriptor.getKind().name());
        return view;
    }

    private static Map<String, AgentDefinition> indexByAgentKey(List<AgentDefinition> rows) {
        Map<String, AgentDefinition> indexed = new LinkedHashMap<>();
        for (AgentDefinition row : rows) {
            if (row != null && StringUtils.hasText(row.getAgentKey())) {
                indexed.putIfAbsent(row.getAgentKey(), row);
            }
        }
        return indexed;
    }

    private static String requireKey(String agentKey) {
        if (!StringUtils.hasText(agentKey) || !agentKey.equals(agentKey.trim())
                || agentKey.length() > MAX_KEY_CHARS) {
            throw new ServiceException("agentKey 非法（须非空、无首尾空白、不超 " + MAX_KEY_CHARS + " 字符）: ["
                    + agentKey + "]");
        }
        return agentKey.trim();
    }

    private static void validateNames(String column, List<String> names) {
        if (names == null) {
            return;
        }
        if (names.size() > MAX_NAMES) {
            throw new ServiceException(column + " 单栏最多 " + MAX_NAMES + " 项，实到 " + names.size() + " 项");
        }
        for (String name : names) {
            if (!StringUtils.hasText(name) || name.trim().length() > MAX_NAME_CHARS) {
                throw new ServiceException(column + " 里有非法名字（须非空、不超 " + MAX_NAME_CHARS + " 字符）: ["
                        + name + "]");
            }
        }
    }

    /**
     * 能力包名必须都在登记表里。空白项在这之前已被 {@link #validateNames} 拒掉——
     * 若放到这里放过去，{@code SelectionColumn} 会把它丢弃，全空白的列表就落成形 {@code []}
     * ＝"登记过的工具一个都不给"，一次误用会变成静默关停。
     */
    private void requireRegisteredPackages(List<String> selected) {
        if (selected == null || selected.isEmpty()) {
            return;
        }
        List<String> unknown = new ArrayList<>();
        for (String name : selected) {
            if (packages.get(name.trim()) == null) {
                unknown.add(name);
            }
        }
        if (unknown.isEmpty()) {
            return;
        }
        throw new ServiceException("能力包未注册，勾不上（可用包名见 /web/api/agent/capability/packages）: " + unknown);
    }

    private String currentOperator() {
        OperatorResolver resolver = operatorResolver.getIfAvailable();
        if (resolver == null) {
            return null;
        }
        try {
            return resolver.currentOperator();
        } catch (Exception e) {
            log.warn("勾选写入取访问人失败（update_by 落空，不影响写入）: error={}", e.getMessage());
            return null;
        }
    }

    private void requirePlatformAdmin(String action) {
        if (!isPlatformAdmin()) {
            throw new ServiceException("agent 勾选配置只给平台管理员" + action);
        }
    }

    /**
     * 管理员判定走 authorization 的 permitService（反射取 bean，本模块对 authorization 的编译依赖
     * 只服务 {@code @BindUser} 的姓名翻译，权限判定不吃它；取不到＝按非管理员处理，
     * 同 {@code SystemSkillAdminService}／{@code AgentManifestController} 口径——
     * 宁可拒掉一次操作，不能让缺 bean 变成放开写面）。protected 是为单测放行留的缝。
     */
    protected boolean isPlatformAdmin() {
        try {
            return Boolean.TRUE.equals(BeanUtil.invoke("permitService", "isAdmin", Boolean.class));
        } catch (Exception e) {
            return false;
        }
    }
}
