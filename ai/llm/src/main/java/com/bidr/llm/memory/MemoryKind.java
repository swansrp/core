package com.bidr.llm.memory;

/**
 * Title: MemoryKind
 * Description: 跨会话记忆的四类条目（T4.3 记忆库）：
 * <ul>
 *     <li>{@link #FACT}——事实：用户陈述过的客观信息（项目名、成员名、部署环境等）；</li>
 *     <li>{@link #PREFERENCE}——偏好：表达过的倾向（主题风格、输出语言、详略程度等）；</li>
 *     <li>{@link #PROFILE}——画像：身份/角色/职责类的长期属性；</li>
 *     <li>{@link #SUMMARY}——摘要：对历史会话结论的浓缩（跨会话续接用）。</li>
 * </ul>
 * 检索时四类合并按 priority + createdAt 排序，kind 只作过滤维度，不参与排序。
 *
 * @author Sharp
 * @since 2026/11/1
 */
public enum MemoryKind {

    /** 事实：用户陈述过的客观信息 */
    FACT,

    /** 偏好：表达过的倾向（风格/语言/详略等） */
    PREFERENCE,

    /** 画像：身份/角色/职责类长期属性 */
    PROFILE,

    /** 摘要：历史会话结论的浓缩（跨会话续接） */
    SUMMARY
}
