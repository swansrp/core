package com.bidr.insight.chatbi.flow;

import com.bidr.authorization.holder.AccountContext;
import com.bidr.llm.agent.OperatorResolver;
import org.springframework.stereotype.Component;

/**
 * Title: AccountOperatorResolver
 * Description: 访问人解析接入——llm 基础框架的 agent 会话与流程轨迹端点经
 * {@link OperatorResolver} SPI 取当前登录人（llm 不依赖 authorization），
 * insight 侧以平台 {@link AccountContext} 实现，未登录回落空（存储侧 anonymous）。
 *
 * @author Sharp
 * @since 2026/8/20
 */
@Component
public class AccountOperatorResolver implements OperatorResolver {

    @Override
    public String currentOperator() {
        return AccountContext.getDisplayName();
    }
}
