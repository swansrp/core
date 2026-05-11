package com.bidr.td.sync;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * MybatisPlus 拦截器骨架 - 用于拦截 TD 标签变更操作，同步至 TDengine 子表。
 * 后续可完善具体拦截逻辑。
 */
@Component
@ConditionalOnProperty(name = "td.sync.enabled", havingValue = "true", matchIfMissing = false)
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})
})
public class TagSyncInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(TagSyncInterceptor.class);

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        // 暂为骨架，后续实现具体拦截逻辑
        log.debug("TagSyncInterceptor intercept: {}", invocation.getMethod().getName());
        return invocation.proceed();
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // 初始化配置
    }
}
