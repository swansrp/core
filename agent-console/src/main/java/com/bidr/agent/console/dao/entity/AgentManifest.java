package com.bidr.agent.console.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * Title: AgentManifest
 * Description: agent 会话装配单（M14 棒2）：一行一会话，记"这场会话手里有什么"。
 * 三栏 JSON（工具名/技能/段清单）由 {@code AgentManifestRecorder} 序列化后落库，
 * 表侧不做结构校验——它是复盘证据，不是运行期输入。
 *
 * @author Sharp
 * @since 2026-10-12
 */
@ApiModel(description = "agent 会话装配单（工具面/模型档/技能/提示词段）")
@Data
@TableName(value = "sys_agent_manifest")
public class AgentManifest {

    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "主键")
    private Long id;

    @TableField(value = "session_id")
    @ApiModelProperty(value = "框架会话标识（一行一会话的唯一键）")
    private String sessionId;

    @TableField(value = "agent_key")
    @ApiModelProperty(value = "agent 运行时注册键")
    private String agentKey;

    @TableField(value = "display_name")
    @ApiModelProperty(value = "agent 展示名（登记时的，列表行直读不回落注册表）")
    private String displayName;

    @TableField(value = "asked_by")
    @ApiModelProperty(value = "发起人（框架 OperatorResolver 口径；列名避开 operator 关键字）")
    private String askedBy;

    @TableField(value = "tool_names")
    @ApiModelProperty(value = "工具名清单（JSON 数组，＝发给模型的那份，含引擎追加的回捞工具）")
    private String toolNames;

    @TableField(value = "tool_count")
    @ApiModelProperty(value = "工具只数（冗余一列：列表页不解析 JSON 也能排序/对账）")
    private Integer toolCount;

    @TableField(value = "model_tier")
    @ApiModelProperty(value = "模型档（本次答话用的模型 id；NULL＝业务侧没交，不编造）")
    private String modelTier;

    @TableField(value = "skill_refs")
    @ApiModelProperty(value = "注入的技能包与版本（JSON 数组）")
    private String skillRefs;

    @TableField(value = "prompt_sections")
    @ApiModelProperty(value = "提示词段清单（JSON 数组，按拼装顺序）")
    private String promptSections;

    /** 行内最重的一列，默认不从读口下发（按 ?include=prompt 展开，见 AgentManifestController） */
    @TableField(value = "prompt_text")
    @ApiModelProperty(value = "提示词全文（模型首轮收到的 system 槽原文＝段清单那几段的真身；超长截尾）")
    private String promptText;

    @TableField(value = "conversation_id")
    @ApiModelProperty(value = "历史对话定位（正文按保留期过期、本值永久；NULL＝该会话没接对话层）")
    private String conversationId;

    @TableField(value = "extras")
    @ApiModelProperty(value = "业务标注（JSON 对象，featureId/spaceId 这类框架不认的维度）")
    private String extras;

    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建人（run 线程无登录上下文时为空，属正常）")
    private String createBy;

    @TableField(value = "create_at", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建时间（＝工具面定形时刻）")
    private Date createAt;

    @TableField(value = "update_at", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新时间（同会话重定形才动）")
    private Date updateAt;

    @TableField(value = "`valid`")
    @ApiModelProperty(value = "有效性（保留列随规范；装配单只插与覆盖，不删）")
    private String valid;
}
