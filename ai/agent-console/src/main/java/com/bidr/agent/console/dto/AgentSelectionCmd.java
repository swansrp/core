package com.bidr.agent.console.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * Title: AgentSelectionCmd
 * Description: agent 勾选写入命令（M14 棒6，PUT 的请求体）。
 *
 * <p>🔴 <b>五栏都是"整组覆盖"的一部分，且 null 与 [] 是两个不同的值</b>：
 * {@code null}＝该维未启用勾选＝全部放行；{@code []}＝显式勾了零个＝该维登记过的全不给；非空＝取交集。
 * 请求体里<b>省略某一栏</b>（JSON 不带这个 key）与<b>显式给 null</b> 在服务端是同一个意思＝该维置回未启用，
 * 因为写腿一次覆盖五列——页面只想改能力包时必须把其余四列的现值原样回传，
 * 不能"不传就当没改"。这条口径由 {@code AgentDefinitionService#writeSelection} 的整组覆盖语义决定，
 * 端点文档里也写明了，免得前端按 REST 补丁的直觉来理解。</p>
 *
 * <p>不接收 {@code agentKey}：键只在 URL 路径上出现一次，请求体里再来一份就是两个真相源。</p>
 *
 * @author Sharp
 * @since 2026-10-13
 */
@ApiModel(description = "agent 勾选写入命令（五栏各一份三态清单，整组覆盖）")
@Data
public class AgentSelectionCmd {

    @ApiModelProperty(value = "能力包勾选：null＝取消启用＝全部放行；[]＝一个都不勾；非空＝取交集。"
            + "🔴 唯一参与运行期的一栏，包名必须已在能力包登记表里，否则整次写入被拒",
            example = "[\"plan\",\"ask-user\"]")
    private List<String> capabilityPackages;

    @ApiModelProperty(value = "系统技能包勾选（三态同上）。🔵 本棒只登记，注入正源仍是空间 bind，故不校验包名")
    private List<String> systemSkills;

    @ApiModelProperty(value = "外部能力勾选（三态同上）。🔵 本棒只登记（MCP 真实接入不在本期）")
    private List<String> externalCapabilities;

    @ApiModelProperty(value = "模型用途档指定（三态同上）。🔵 本棒只登记，实答模型仍由 provider 现读 sys_config")
    private List<String> modelPurpose;

    @ApiModelProperty(value = "提示词段勾选（三态同上）。🔵 本棒只登记，拼装正源在业务侧")
    private List<String> promptSegments;
}
