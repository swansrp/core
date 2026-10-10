package com.bidr.kernel.config.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.task.TaskSchedulerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Title: TaskSchedulerPoolConfig
 * Description: 定时任务线程池的框架兜底：Spring Boot 的
 * {@code spring.task.scheduling.pool.size} 默认值是 <b>1</b>，也就是全应用的所有
 * {@code @Scheduled} 共用一条线程。一条线程意味着任何一个跑得久的任务（清表、批量回收、
 * 对账这类）会把同点位排队的其它定时任务一起拖住——表现是"另一个任务这轮没跑"，
 * 而不是任何报错，事后极难归因。框架层直接把并发度抬起来，业务侧不需要各自记得配。
 * <p>
 * 只做兜底不做覆盖：显式配了该属性时本 customizer 整条不生效，配多少仍由部署方说了算
 * （customizer 在 {@code TaskSchedulerBuilder} 应用完属性之后才跑，无条件 set 会把用户值吃掉）。
 *
 * @author Sharp
 * @since 2026/10/10
 */
@Slf4j
@Configuration
public class TaskSchedulerPoolConfig {

    /**
     * 兜底并发度。取值只需覆盖"夜间几个批处理任务撞在一起"这一场景，
     * 定时任务本身是低频 IO，不需要更大；真正的高频执行仍应走线程池/跑批框架而非这里。
     */
    private static final int FALLBACK_POOL_SIZE = 4;

    @Bean
    public TaskSchedulerCustomizer taskSchedulerPoolFallback(
            @Value("${spring.task.scheduling.pool.size:#{null}}") Integer configuredPoolSize) {
        return scheduler -> {
            if (configuredPoolSize != null) {
                return;
            }
            scheduler.setPoolSize(FALLBACK_POOL_SIZE);
            log.info("定时任务线程池按框架兜底设为 {}（未显式配置 spring.task.scheduling.pool.size，"
                    + "Boot 默认只有 1 条线程）", FALLBACK_POOL_SIZE);
        };
    }
}
