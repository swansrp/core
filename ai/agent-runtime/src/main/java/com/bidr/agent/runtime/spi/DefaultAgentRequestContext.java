package com.bidr.agent.runtime.spi;

import com.bidr.authorization.holder.AccountContext;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.validate.Validator;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Map;

/**
 * Title: DefaultAgentRequestContext
 * Description: 缺省业务扩展点实现：登录人取框架 {@link AccountContext}，Agent 编码必填，
 * 分组键/元数据按请求原样透传——**不含任何项目语义**。
 * 项目侧注册同类型 Bean 即覆盖（{@code @ConditionalOnMissingBean}）。
 *
 * @author sharp
 * @since 2026/9/22
 */
public class DefaultAgentRequestContext implements AgentRequestContext {

    @Override
    public String currentOperator() {
        return AccountContext.getOperator();
    }

    @Override
    public String resolveAgentCode(String requestedAgentCode) {
        Validator.assertNotBlank(requestedAgentCode, ErrCodeSys.PA_PARAM_NULL, "Agent 编码（agentCode）");
        return requestedAgentCode.trim();
    }

    @Override
    public String resolveGroupKey(String requestedGroupKey, String businessType, String businessId) {
        return StringUtils.hasText(requestedGroupKey) ? requestedGroupKey.trim() : null;
    }

    @Override
    public Map<String, Object> resolveMetadata(String businessType, String businessId) {
        return Collections.emptyMap();
    }
}