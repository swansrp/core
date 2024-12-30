package com.bidr.platform.redis.aop.publish;

import com.bidr.platform.config.aop.RedisPublish;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ReflectionUtils;

import javax.annotation.Resource;
import java.util.HashSet;
import java.util.Set;

/**
 * Title: RedisPublishManager
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2020/10/19 10:39
 */
@Slf4j
@Configuration
public class RedisPublishManager implements ApplicationContextAware {

    @Value("${my.project.name}")
    private String projectName;
    @Resource
    private ApplicationContext applicationContext;
    @Resource
    private RedisPublishConfig redisPublishConfig;
    private Set<String> serviceMethodSet = new HashSet<>();

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        String[] beanNames = applicationContext.getBeanNamesForType(Object.class);
        for (String beanName : beanNames) {
            final Class<?> clazz = applicationContext.getType(beanName);
            add(applicationContext, clazz);
        }
    }

    private void add(ApplicationContext applicationContext, final Class<?> clazz) {
        ReflectionUtils.doWithMethods(clazz, method -> {
            ReflectionUtils.makeAccessible(method);
            RedisPublish redisPublish = AnnotationUtils.findAnnotation(method, RedisPublish.class);

            if (redisPublish != null && serviceMethodSet.add(getDefaultTopic(clazz, method.getName()))) {
                String topic = redisPublish.topic();
                if (StringUtils.isBlank(topic)) {
                    topic = getDefaultTopic(clazz, method.getName());
                }
                Object delegate = applicationContext.getBean(clazz);
                if (method.getParameterTypes().length == 0) {
                    redisPublishConfig.registerPublish(topic, delegate, method);
                } else if (method.getParameterTypes().length == 1) {
                    redisPublishConfig.registerPublish(topic, delegate, method, method.getParameterTypes()[0]);
                } else {
                    log.error("目标方法参数个数不唯一");
                }
            }
        }, method -> null != AnnotationUtils.findAnnotation(method, RedisPublish.class));
    }

    public String getDefaultTopic(Class<?> clazz, String methodName) {
        String className = clazz.getName();
        int index = className.indexOf("$$");

        if (index > 0) {
            className = className.substring(0, index);
        }
        return projectName + "-" + className + "-" + methodName;
    }
}
