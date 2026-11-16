package com.bidr.llm.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;

/**
 * Title: AgentSessionStoreBackendSelector
 * Description: agent 会话存储后端二选一选择器（{@link ImportSelector}）。
 * <p>
 * 🔴 <b>为什么用 selector 而不是同类内嵌 + {@code @ConditionalOnClass}</b>：原结构是
 * 「外层类挂内存兜底 {@code @Bean} + 内嵌类挂 {@code @ConditionalOnClass(RedisService)} 的 Redis {@code @Bean}」，
 * 两形态靠方法级 {@code @ConditionalOnMissingBean} 竞争定胜负——<b>胜负取决于 bean 定义的注册顺序</b>，
 * 而注册顺序随装配通道（imports / 组件扫描 / 纯单测容器）漂移：同一段代码，
 * ApplicationContextRunner 与纯组件扫描容器都是 Redis 赢，完整应用（epc pre 生产）却是内存赢——
 * 完整启动的 CONDITIONS EVALUATION REPORT 实锤 {@code @ConditionalOnClass} 从未失手
 * （"found required class 'com.bidr.platform.redis.service.RedisService'"），
 * 输的是注册序：外层内存 bean 先注册、内嵌 Redis bean 后到被条件干掉，日志「类路径无 core/redis」是误导文案。
 * selector 把「二选一」从<b>注册期竞争</b>提前到<b> parse 期选择</b>：只有一个后端配置类会被 import，
 * 零竞争、顺序免疫，双通道（组件扫描/imports）行为一致。
 * </p>
 * <p>
 * 类路径判断用字符串（{@code ClassUtils.isPresent}），与 {@code @ConditionalOnClass(name=...)}
 * 同款语义、同款类加载隔离——selector 本体不引用 RedisService 类型，无 redis 依赖的应用装载本链零风险。
 * </p>
 *
 * @author Sharp
 * @since 2026/11/17
 */
public class AgentSessionStoreBackendSelector implements ImportSelector, BeanFactoryAware {

    private static final String REDIS_SERVICE = "com.bidr.platform.redis.service.RedisService";

    private BeanFactory beanFactory;

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    @Override
    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
        // 类加载器与 @ConditionalOnClass 评估同源（context 的 beanClassLoader）：测试容器换 loader、
        // fat jar 的 LaunchedURLClassLoader 下行为都一致；不能用 selector 自身 loader——
        // 它永远是被 parent 实化的，过滤/嵌套类路径对它不可见
        ClassLoader classLoader = beanFactory instanceof ConfigurableBeanFactory
                ? ((ConfigurableBeanFactory) beanFactory).getBeanClassLoader()
                : getClass().getClassLoader();
        boolean present = ClassUtils.isPresent(REDIS_SERVICE, classLoader);
        if (present) {
            return new String[] {AgentSessionStoreRedisConfiguration.class.getName()};
        }
        return new String[] {AgentSessionStoreInMemoryConfiguration.class.getName()};
    }
}
