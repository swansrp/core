package com.bidr.llm.flow;

/**
 * Title: LlmHistoryMessage
 * Description: 对话历史消息接口——llm 结点按它识别历史条目（消息级追加与模板内联两种用途），
 * 业务侧的对话 VO 直接实现即可零转换传入变量池。
 *
 * @author Sharp
 * @since 2026/8/16
 */
public interface LlmHistoryMessage {

    /**
     * 角色：user-用户提问 / assistant-助手回答
     */
    String getRole();

    /**
     * 消息正文
     */
    String getContent();
}
