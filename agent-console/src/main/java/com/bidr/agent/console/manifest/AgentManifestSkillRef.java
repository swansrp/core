package com.bidr.agent.console.manifest;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Title: AgentManifestSkillRef
 * Description: 装配单里的一条技能引用（包名 + 版本 + 来源标注）。
 * 只记"注入了哪个包的哪一版"，不记正文——正文按 {@code read_skill} 通道现读，归档它等于把包抄进台账。
 *
 * @author Sharp
 * @since 2026-10-12
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentManifestSkillRef {

    /** 技能包名（提示词里出现的那个名字，与 {@code ## Skills} 段同源） */
    private String name;

    /** 包版本（重传递增；NULL＝贡献者没取到，不填 0 冒充） */
    private Integer version;

    /** 来源标注（如"本人草稿测试""指定技能内联"，业务自填，台账不校验取值） */
    private String note;
}
