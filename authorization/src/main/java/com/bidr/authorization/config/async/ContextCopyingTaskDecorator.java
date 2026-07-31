package com.bidr.authorization.config.async;

import com.bidr.authorization.bo.account.AccountInfo;
import com.bidr.authorization.bo.token.TokenInfo;
import com.bidr.authorization.config.log.MdcConfig;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.holder.TokenHolder;
import com.bidr.kernel.config.db.DynamicTableNameHolder;
import com.bidr.kernel.utils.FuncUtil;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.lang.NonNull;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.HashMap;
import java.util.Map;

/**
 * 把当前线程的 MDC / RequestAttributes / AccountContext / TokenHolder / DynamicTableNameHolder
 * 五类上下文复制到工作线程，保证异步任务行为与 Web 请求线程一致。
 * <p>
 * 供各业务模块自定义线程池时复用：{@code executor.setTaskDecorator(new ContextCopyingTaskDecorator())}。
 *
 * @author Sharp
 * @since 2022/6/6 11:10
 */
public class ContextCopyingTaskDecorator implements TaskDecorator {

    @Override
    @NonNull
    public Runnable decorate(@NonNull Runnable runnable) {
        // 在「提交任务」的线程（Web 请求线程）上抓取 5 类上下文快照
        Map<String, String> copyOfContextMap = getMdcMap();
        RequestAttributes context = getMvcContext();
        AccountInfo accountInfo = AccountContext.get();
        TokenInfo tokenInfo = TokenHolder.get();
        Map<String, String> dynamicTableNameInfo = DynamicTableNameHolder.get();
        return () -> {
            try {
                // 在工作线程恢复上下文
                MdcConfig.forkLogInfo(copyOfContextMap);
                RequestContextHolder.setRequestAttributes(context);
                AccountContext.set(accountInfo);
                TokenHolder.set(tokenInfo);
                DynamicTableNameHolder.set(dynamicTableNameInfo);
                runnable.run();
            } finally {
                // 工作线程是池化复用的，必须清理，避免污染下一个任务
                destroyLocalThreadInfo();
                MdcConfig.destroyMdc();
            }
        };
    }

    /**
     * 清理本任务塞到工作线程的所有 ThreadLocal，防止线程复用时污染下一个任务。
     */
    private void destroyLocalThreadInfo() {
        RequestContextHolder.resetRequestAttributes();
        AccountContext.remove();
        TokenHolder.remove();
        DynamicTableNameHolder.remove();
    }

    /**
     * 抓取当前线程的 MDC 拷贝；为空时返回空 Map，避免下游 NPE。
     */
    private Map<String, String> getMdcMap() {
        Map<String, String> map = MDC.getCopyOfContextMap();
        if (FuncUtil.isEmpty(map)) {
            map = new HashMap<>(0);
        }
        return map;
    }

    /**
     * 抓取当前 Web 请求的 RequestAttributes；不在请求线程时返回 null（异步嵌套异步场景）。
     */
    private RequestAttributes getMvcContext() {
        RequestAttributes attributes = null;
        try {
            attributes = RequestContextHolder.currentRequestAttributes();
        } catch (IllegalStateException ignore) {
            // 非 Web 请求线程（如定时任务、消息消费者）触发的提交，留空即可
        }
        return attributes;
    }
}
