package com.bidr.agent.console;

import com.bidr.agent.console.dto.AgentDefinitionView;
import com.bidr.agent.console.dto.AgentSelectionCmd;
import com.bidr.agent.console.selection.AgentDefinitionAdminService;
import com.bidr.kernel.config.response.RespConvert;
import com.bidr.kernel.exception.ServiceException;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Title: AgentDefinitionAdminController
 * Description: agent 勾选读写端点（M14 棒6-E，唯一改运行期工具面的写面）。
 *
 * <p><b>闸门在 {@link AgentDefinitionAdminService} 里</b>（平台管理员，反射取 permitService、
 * bean 取不到即拒绝），与 {@code SystemSkillAdminController} 同口径；拦截链仍走平台 {@code /web/api}
 * 的登录校验。拒绝走 {@link ServiceException}（非零码），不走 notice。</p>
 *
 * <p><b>开关挂 {@code agent-console.selection.enabled}（默认关）而不是 {@code agent-console.api.enabled}</b>：
 * 表、交集选择器、这张读写面三件套同生同死——表没建（开关关着）却开着端点，第一次调用就是一条 SQL 报错，
 * 那比"没有这个入口"更糟。与棒 5 的用量统计端点同一理由。</p>
 *
 * <p><b>写口是 POST 而不是 PUT</b>：本仓（框架＋业务）现存控制器一律只用 GET/POST，
 * 前端 request 封装与拦截链也只按这两类配；这里不为一处语义引入第三种动词形态。</p>
 *
 * @author Sharp
 * @since 2026-10-13
 */
@Api(tags = "系统管理 - agent 勾选定义（读写工具面收窄配置，仅平台管理员）")
@Slf4j
@RestController
@RequestMapping("/web/api/agent/definition")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "agent-console.selection", name = "enabled", havingValue = "true")
public class AgentDefinitionAdminController {

    private final AgentDefinitionAdminService adminService;

    @ApiOperation(value = "勾选清单（在跑的 agent 全列出，含从未勾过的）",
            notes = "主序＝运行期注册表（flow→autonomous→dynamic），末尾补\"库里有行但注册表查不到\"的历史行"
                    + "（registered=false，该 agent 已不在跑、勾选不参与任何会话）。"
                    + "五栏三态：null＝未启用＝全部放行／[]＝一个都不勾／非空＝取交集；"
                    + "只有 runtimeColumn 指到的 capabilityPackages 参与运行期，其余四栏本棒只登记")
    @GetMapping("/list")
    public List<AgentDefinitionView> list() {
        List<AgentDefinitionView> views = adminService.list();
        RespConvert.customBindConvert(views, AgentDefinitionView.class);
        return views;
    }

    @ApiOperation(value = "单个 agent 的勾选视图",
            notes = "注册表与勾选表都查不到该键＝拒绝（不给拼错的键渲染空表）。"
                    + "无行＝hasRow=false 且五栏全 null＝三态第一态（未启用）")
    @GetMapping("/{agentKey}")
    public AgentDefinitionView get(
            @ApiParam(value = "agent 运行时注册键（如 inkhub-doc-writer、flow:ask）", required = true)
            @PathVariable("agentKey") String agentKey) {
        AgentDefinitionView view = adminService.get(agentKey);
        RespConvert.customBindConvert(view, AgentDefinitionView.class);
        return view;
    }

    @ApiOperation(value = "整组覆盖五栏勾选（一次给齐；null＝该栏置回未启用＝全部放行）",
            notes = "🔴 <b>整组覆盖，不是补丁</b>：请求体里省略某一栏与显式给 null 同义＝把那一栏写回 NULL，"
                    + "只想改能力包时必须把其余四栏现值原样回传。"
                    + "能力包名须已在登记表里（未注册＝当场拒绝，可用名见 /web/api/agent/capability/packages）；"
                    + "其余四栏本棒不校验包名。agentKey 不在运行期注册表且库里无行＝拒绝新建。"
                    + "改完下一场会话即刻生效（选择器每场现读、不缓存），进行中的会话不受影响")
    @PostMapping("/{agentKey}/selection")
    public AgentDefinitionView save(
            @ApiParam(value = "agent 运行时注册键", required = true)
            @PathVariable("agentKey") String agentKey,
            @ApiParam(value = "五栏勾选清单（三态）", required = true)
            @RequestBody AgentSelectionCmd cmd) {
        AgentDefinitionView view = adminService.save(agentKey, cmd);
        RespConvert.customBindConvert(view, AgentDefinitionView.class);
        return view;
    }
}
