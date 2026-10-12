package com.bidr.agent.console.manifest;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: AgentManifestDraft
 * Description: 装配单草稿——贡献者逐栏补写的载体（M14 棒2）。三栏语义：
 * {@code modelTier} 本次会话答话来自哪个模型档；{@code skills} 注入了哪些技能包及其版本
 * （版本必填原因：技能包重传即版本递增，不留版本就无法复盘"当时按哪版契约产出"）；
 * {@code sections} 系统提示词由哪几段拼成（段名由业务定义，台账只存字符串）。
 * {@code extras} 收业务自有的标注键（featureId/spaceId 这类框架不认的维度）。
 *
 * @author Sharp
 * @since 2026-10-12
 */
@Data
public class AgentManifestDraft {

    /** 模型档（本次会话答话用的模型 id；取不到留 null，不编造） */
    private String modelTier;

    /** 注入的技能包清单（含版本与来源标注） */
    private List<AgentManifestSkillRef> skills = new ArrayList<>();

    /** 提示词段清单（按拼装顺序） */
    private List<String> sections = new ArrayList<>();

    /** 业务标注（框架不解读，原样落 JSON） */
    private Map<String, String> extras = new LinkedHashMap<>();
}
