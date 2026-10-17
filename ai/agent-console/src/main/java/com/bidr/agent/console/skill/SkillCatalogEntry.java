package com.bidr.agent.console.skill;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Title: SkillCatalogEntry
 * Description: {@link SkillCatalogPort#visible()} 的一条目录条目：只带"读得到谁、第几版、发没发布"三个事实。
 * 不带 description、更不带正文——提示词怎么措辞、草稿要不要标"作者测试"，都是消费方自己的事，
 * 契约层给事实就够了（同一套值在这里写一遍文案，等于把业务口径钉进框架）。
 *
 * @author Sharp
 * @since 2026-10-13
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SkillCatalogEntry {

    /** 技能包名（与 {@code ## Skills} 段、{@code read_skill} 入参同一个尺） */
    private String name;

    /** 包版本（重传递增；NULL＝实现方没取到，不填 0 冒充） */
    private Integer version;

    /** 是否已发布（false＝实现方给的测试旁路，如 InkHub 的"本人未发布草稿可自测"） */
    private boolean published;
}
