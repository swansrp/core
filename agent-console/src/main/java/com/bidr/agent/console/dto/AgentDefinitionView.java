package com.bidr.agent.console.dto;

import com.bidr.agent.console.dao.entity.AgentDefinition;
import com.bidr.agent.console.selection.SelectionColumn;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Title: AgentDefinitionView
 * Description: agent 勾选的读写视图（M14 棒6）。
 *
 * <p>🔴 <b>五个勾选列都以"三态"形态出现</b>：字段值为 {@code null}＝该维<b>未启用勾选</b>（＝全部放行，
 * 存量 agent 的形态）；{@code []}＝显式勾了零个（＝登记过的全不给）；非空＝按并集取交集。
 * 这一区分是 JSON 能表达、而"统一给空数组"表达不了的，所以前端回传必须原样保留 null，
 * 不许在页面上把"没启用"渲染成"全不勾"——那是把放行改成关停。</p>
 *
 * <p>五列里<b>只有 {@code capabilityPackages} 参与运行期</b>（工具面收窄），其余四列本棒只登记与展示；
 * 端点响应里的 {@code runtimeColumn} 把这一事实直接告诉页面，免得"表里有列"被读成"那维已经能配"。</p>
 *
 * @author Sharp
 * @since 2026-10-13
 */
@ApiModel(description = "agent 勾选定义（一行一个 agent，五列各一份三态清单）")
@Data
public class AgentDefinitionView {

    /** 唯一参与运行期的列名（前端据此把其余四栏标成"仅登记"） */
    public static final String RUNTIME_COLUMN = "capabilityPackages";

    @ApiModelProperty(value = "agent 运行时注册键")
    private String agentKey;

    @ApiModelProperty(value = "展示名（来自运行期注册表；注册表里没有该 agent 时为空）")
    private String displayName;

    @ApiModelProperty(value = "agent 类别（FLOW/AUTONOMOUS/DYNAMIC；同上）")
    private String kind;

    @ApiModelProperty(value = "false＝当前运行期注册表里没有这个 agent（多为已退役 agent 留下的历史勾选行）")
    private boolean registered = true;

    @ApiModelProperty(value = "false＝库里没这行（＝从未勾选，三态第一态）")
    private boolean hasRow;

    @ApiModelProperty(value = "唯一参与运行期的列名（其余四列本棒只登记）")
    private String runtimeColumn = RUNTIME_COLUMN;

    @ApiModelProperty(value = "能力包勾选：null＝未启用＝全部放行；[]＝一个都不勾；非空＝取交集。🔴 参与运行期",
            example = "[\"plan\",\"ask-user\"]")
    private List<String> capabilityPackages;

    @ApiModelProperty(value = "系统技能包勾选（三态同上）。🔵 本棒只登记，注入正源仍是空间 bind")
    private List<String> systemSkills;

    @ApiModelProperty(value = "外部能力勾选（三态同上）。🔵 本棒只登记")
    private List<String> externalCapabilities;

    @ApiModelProperty(value = "模型用途档指定（三态同上）。🔵 本棒只登记，实答模型仍由 provider 现读 sys_config")
    private List<String> modelPurpose;

    @ApiModelProperty(value = "提示词段勾选（三态同上）。🔵 本棒只登记，拼装正源在业务侧")
    private List<String> promptSegments;

    @ApiModelProperty(value = "最后改勾选的人（平台管理员；无行则空）")
    private String updateBy;

    @ApiModelProperty(value = "最后改勾选的时刻")
    private Date updateAt;

    /** 库行 → 读视图（row 为 null＝无行，五列一律留 null＝未启用） */
    public static AgentDefinitionView of(String agentKey, AgentDefinition row) {
        AgentDefinitionView view = new AgentDefinitionView();
        view.setAgentKey(agentKey);
        view.setHasRow(row != null);
        if (row == null) {
            return view;
        }
        view.setCapabilityPackages(names(row.getCapabilityPackages(), "capability_packages"));
        view.setSystemSkills(names(row.getSystemSkills(), "system_skills"));
        view.setExternalCapabilities(names(row.getExternalCapabilities(), "external_capabilities"));
        view.setModelPurpose(names(row.getModelPurpose(), "model_purpose"));
        view.setPromptSegments(names(row.getPromptSegments(), "prompt_segments"));
        view.setUpdateBy(row.getUpdateBy());
        view.setUpdateAt(row.getUpdateAt());
        return view;
    }

    /**
     * 列原文 → 三态清单：UNSET 落 {@code null}、NONE 落空表、SELECTED 落名单。
     * 坏值走 {@link SelectionColumn} 的按窄方向，读视图这里也会落成空表（页面看得到"一个都没勾"，
     * 与库里坏 JSON 的实际运行效果一致，不粉饰）。
     */
    private static List<String> names(String rawJson, String column) {
        SelectionColumn parsed = SelectionColumn.parse(rawJson, "sys_agent_definition." + column);
        return parsed.isUnset() ? null : new ArrayList<>(parsed.getNames());
    }
}
