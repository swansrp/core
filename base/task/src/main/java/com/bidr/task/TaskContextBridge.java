package com.bidr.task;

import com.bidr.authorization.bo.account.AccountInfo;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.task.dao.entity.SaAsyncTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.function.Supplier;

/**
 * 任务上下文桥（M16 棒 2）：阶段方法与终态观察者链的<b>成对</b>上下文包裹段——
 * 从 {@code create_by} 重建操作人（{@code AccountContext}，core 可直用），
 * 业务/租户上下文经 {@link AsyncTaskContextContributor} SPI 由业务侧挂入（R-1：core 不认识业务形态）。
 *
 * <p>🔴 卫生姿势＝存旧值→设新值→finally 恢复（禁裸清）：{@code AccountContext} 是每线程一格的
 * ThreadLocal、不认「是谁放的」，裸清在请求线程上会静默清掉登录态（后半截请求不炸、审计列悄悄写空）。
 * 结构律保证本桥只在跑批线程上跑（阶段方法只由 runner 执行、受理侧不执行任何阶段），
 * 防御分支仍按「旧值非空则不动」处理，任何入口形态都不误伤既有上下文。</p>
 *
 * <p>解绑排在整条链之后（阶段方法＋终态观察者共用同一份重建上下文，否则链里写受隔离保护的表会落空）。</p>
 *
 * @author sharp
 */
@Service
public class TaskContextBridge {

    private final List<AsyncTaskContextContributor> contributors;

    /** 生产构造：收集容器内全部贡献者（无则空链） */
    @Autowired(required = false)
    public TaskContextBridge(List<AsyncTaskContextContributor> contributors) {
        this.contributors = contributors == null ? Collections.emptyList() : contributors;
    }

    public TaskContextBridge() {
        this(Collections.emptyList());
    }

    /** 在重建的上下文里执行一段动作（阶段方法或终态观察者链），异常路径也保证成对恢复 */
    public <T> T execute(SaAsyncTask task, Supplier<T> action) {
        // 存旧值→设新值→finally 恢复（纪律 §4.2.4-② 的正确卫生姿势）：
        // 执行侧唯一入口＝跑批线程（无请求上下文），本桥可无条件重建；即便入口形态意外带上下文，
        // 出口也恢复原值而非裸清——AccountContext 每线程一格、不认「是谁放的」，裸清会静默删登录态
        AccountInfo previous = AccountContext.get();
        if (StringUtils.hasText(task.getCreateBy())) {
            AccountInfo operator = new AccountInfo();
            operator.setCustomerNumber(task.getCreateBy());
            AccountContext.set(operator);
        }
        Deque<Object[]> bound = new ArrayDeque<>();
        try {
            // 贡献者按序 bind（句柄透传给恢复侧）：中途失败则把已 bind 的先恢复再抛，不留半套上下文
            for (AsyncTaskContextContributor contributor : contributors) {
                bound.push(new Object[]{contributor, contributor.bind(task)});
            }
            return action.get();
        } finally {
            while (!bound.isEmpty()) {
                Object[] pair = bound.pop();
                AsyncTaskContextContributor contributor = (AsyncTaskContextContributor) pair[0];
                try {
                    contributor.unbind(task, pair[1]);
                } catch (Exception e) {
                    // 恢复失败不得向任务链抛（也不吞业务异常）：记日志继续恢复其余贡献者
                    org.slf4j.LoggerFactory.getLogger(TaskContextBridge.class)
                            .warn("任务上下文恢复失败: contributor={} {}", contributor.getClass().getName(), e.getMessage());
                }
            }
            // 恢复＝回到进来时的原样：原无上下文则 remove（重建值是我们设的），原有则原值放回
            if (StringUtils.hasText(task.getCreateBy())) {
                if (previous == null) {
                    AccountContext.remove();
                } else {
                    AccountContext.set(previous);
                }
            }
        }
    }

    /** {@link #execute(SaAsyncTask, Supplier)} 的无返回值便捷口 */
    public void execute(SaAsyncTask task, Runnable action) {
        execute(task, () -> {
            action.run();
            return null;
        });
    }
}
