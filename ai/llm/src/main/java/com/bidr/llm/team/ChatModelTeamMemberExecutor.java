package com.bidr.llm.team;

import com.bidr.kernel.exception.ServiceException;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: ChatModelTeamMemberExecutor
 * Description: 团队成员执行器的生产装配（T4.2 团队编排门面）——经既有 {@link ChatLanguageModel}
 * 模型抽象驱动单成员调用（机制断言 M-8 的生产侧）：member.prompt 组装 system（人设/职责），
 * input 组装 user（本次任务/移交内容），同步生成取文本产出。
 * <p>
 * 模型 Bean 经 ObjectProvider 可选解析：应用未接入模型配置时执行期报人话错误（团队编排台其余
 * 管理功能不受影响），与 ApprovalService.requireStore 同口径。单测不走本类——直接对
 * {@link TeamMemberExecutor} 传 lambda。
 * </p>
 *
 * @author Sharp
 * @since 2026/11/1
 */
@Slf4j
@Component
public class ChatModelTeamMemberExecutor implements TeamMemberExecutor {

    private final ObjectProvider<ChatLanguageModel> chatModel;

    public ChatModelTeamMemberExecutor(ObjectProvider<ChatLanguageModel> chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public String execute(TeamMember member, String input) {
        ChatLanguageModel model = requireModel();
        List<ChatMessage> messages = new ArrayList<>();
        if (StringUtils.hasText(member.getPrompt())) {
            messages.add(SystemMessage.from(member.getPrompt()));
        }
        messages.add(UserMessage.from(input == null ? "" : input));
        Response<AiMessage> response = model.generate(messages);
        String text = response.content().text();
        return text == null ? "" : text;
    }

    private ChatLanguageModel requireModel() {
        ChatLanguageModel model = chatModel.getIfAvailable();
        if (model == null) {
            throw new ServiceException("应用未接入模型配置（ChatLanguageModel），团队成员执行不可用");
        }
        return model;
    }
}
