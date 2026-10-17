/**
 * Project Name:SpringBootCommon
 * File Name:BeanUtil.java
 * Package Name:com.srct.service.utils
 * Date:2018年4月26日上午11:17:45
 * Copyright (c) 2018, ruopeng.sha All Rights Reserved.
 */
package com.bidr.kernel.utils;

import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.validate.Validator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.AnnotatedGenericBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.*;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.context.ContextLoader;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.ServletRequestHandledEvent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 工具类-spring bean
 *
 * @author Sharp
 */
@Slf4j
@Configuration
public class BeanUtil implements ApplicationContextAware, ApplicationListener<ApplicationEvent> {

    private static final ScopeMetadataResolver scopeMetadataResolver = new AnnotationScopeMetadataResolver();

    private static final BeanNameGenerator beanNameGenerator = new AnnotationBeanNameGenerator();
    private static final String[] BLOCK_DISPLAY_REQUEST_URL = {"/actuator", "/web/log"};
    private static WebApplicationContext ctx = null;

    /**
     * 根据bean Class获取
     *
     * @param clazz bean类型
     * @return bean 对象
     */
    public static <T> T getBean(Class<T> clazz) {
        if (ctx == null) {
            log.debug("ApplicationContext未初始化，通过ContextLoader获取!");
            ctx = ContextLoader.getCurrentWebApplicationContext();
        }
        if (ctx == null) {
            log.info("Call BeanUtils too early");
            return null;
        }
        try {
            return ctx.getBean(clazz);
        } catch (NoSuchBeanDefinitionException e) {
            return null;
        }
    }

    public static <T> T getBean(String beanName, Class<T> clazz) {
        if (ctx == null) {
            log.info("Call BeanUtils too early");
            return null;
        }
        try {
            return (T) ctx.getBean(beanName);
        } catch (NoSuchBeanDefinitionException e) {
            return null;
        }
    }

    public static String registerBean(ApplicationContext applicationContext, String name, Class<?> beanClass) {
        DefaultListableBeanFactory registry = (DefaultListableBeanFactory) applicationContext.getAutowireCapableBeanFactory();
        return registerBean(name, beanClass, registry);
    }

    private static String registerBean(String name, Class<?> beanClass, DefaultListableBeanFactory registry) {
        AnnotatedGenericBeanDefinition abd = new AnnotatedGenericBeanDefinition(beanClass);
        ScopeMetadata scopeMetadata = scopeMetadataResolver.resolveScopeMetadata(abd);
        abd.setScope(scopeMetadata.getScopeName());
        // 可以自动生成name
        String beanName = (StringUtils.isNotBlank(name) ? name : beanNameGenerator.generateBeanName(abd, registry));
        AnnotationConfigUtils.processCommonDefinitionAnnotations(abd);
        BeanDefinitionHolder definitionHolder = new BeanDefinitionHolder(abd, beanName);
        BeanDefinitionReaderUtils.registerBeanDefinition(definitionHolder, registry);
        return beanName;
    }

    public static String registerBean(String name, Class<?> beanClass) {
        DefaultListableBeanFactory registry = (DefaultListableBeanFactory) ctx.getAutowireCapableBeanFactory();
        return registerBean(name, beanClass, registry);
    }

    public static String getProperty(String name) {
        return ctx.getEnvironment().getProperty(name);
    }

    public static <T> T getProperty(String name, Class<T> clazz) {
        return ctx.getEnvironment().getProperty(name, clazz);
    }

    public static String[] getBeanNamesForType(Class<?> clazz) {
        return ctx.getBeanNamesForType(clazz);
    }

    /**
     * 获取当前环境
     */
    public static String getActiveProfile() {
        return ctx.getEnvironment().getActiveProfiles()[0];
    }

    public static void setContext(ApplicationContext applicationContext) {
        log.info("setApplicationContext");
        ctx = (WebApplicationContext) applicationContext;
    }

    @SuppressWarnings("unchecked")
    public static <T> T invoke(String beanName, String methodName, Class<T> clazz, Object... parameterArray) {
        if (FuncUtil.isNotEmpty(parameterArray)) {
            List<Class<?>> classList = new ArrayList<>();
            for (Object param : parameterArray) {
                classList.add(param.getClass());
            }
            validateBeanFunction(beanName, methodName, classList.toArray(new Class<?>[0]));
            return (T) ReflectionUtil.invoke(BeanUtil.getBean(beanName), methodName, parameterArray);
        } else {
            validateBeanFunction(beanName, methodName);
            return (T) ReflectionUtil.invoke(BeanUtil.getBean(beanName), methodName);
        }
    }

    public static void validateBeanFunction(String beanName, String methodName, Class<?>... parameterArray) {
        Validator.assertNotBlank(beanName, ErrCodeSys.PA_DATA_NOT_EXIST, "beanName");
        Validator.assertNotBlank(methodName, ErrCodeSys.PA_DATA_NOT_EXIST, "methodName");
        Object service = null;
        try {
            service = getBean(StringUtils.uncapitalize(beanName));
        } catch (Exception e) {
            Validator.assertException(ErrCodeSys.PA_DATA_NOT_EXIST, "service");
        }
        validateBeanFunction(service, methodName, parameterArray);
    }

    /**
     * 根据bean名称获取
     *
     * @param name bean名称
     * @return bean对象
     */
    public static Object getBean(String name) {
        if (ctx == null) {
            log.info("Call BeanUtils too early");
            return null;
        }
        try {
            return ctx.getBean(name);
        } catch (BeansException e) {
            log.error("", e);
            return null;
        }

    }

    public static void validateBeanFunction(Object serviceBean, String methodName, Class<?>... parameterArray) {
        Validator.assertNotNull(serviceBean, ErrCodeSys.PA_DATA_NOT_EXIST, "service");
        Method method = ReflectionUtils.findMethod(serviceBean.getClass(), methodName, parameterArray);
        Validator.assertNotNull(method, ErrCodeSys.PA_DATA_NOT_EXIST, "method");
    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof ServletRequestHandledEvent) {
            for (String url : BLOCK_DISPLAY_REQUEST_URL) {
                if (((ServletRequestHandledEvent) event).getRequestUrl().contains(url)) {
                    return;
                }
            }
        }
        log.info("onApplicationEvent {}", event);
    }

    @Override
    public synchronized void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        log.info("setApplicationContext");
        ctx = (WebApplicationContext) applicationContext;
    }

    /*
     * 注： (1)相同属性名，且类型不匹配时候的处理，ok，但是未满足的属性不拷贝；
     * (2)get和set方法不匹配的处理，创建拷贝的时候报错，无法拷贝任何属性(当且仅当sourceClass的get方法超过set方法时出现)
     * (3)BeanCopier 初始化例子：BeanCopier copier = BeanCopier.create(Source.class,
     * Target.class, useConverter=true)
     * 第三个参数userConverter,是否开启Convert,默认BeanCopier只会做同名，同类型属性的copier,否则就会报错.
     * copier = BeanCopier.create(source.getClass(), target.getClass(), false);
     * copier.copy(source, target, null); (4)修复beanCopier对set方法强限制的约束
     * 改写net.sf.cglib.beans.BeanCopier.Generator.generateClass(ClassVisitor)方法
     * 将133行的 MethodInfo write =
     * ReflectUtils.getMethodInfo(setter.getWriteMethod()); 预先存一个names2放入 109
     * Map names2 = new HashMap(); 110 for (int i = 0; i < getters.length; ++i)
     * { 111 names2.put(setters[i].getName(), getters[i]); }
     * 调用这行代码前判断查询下，如果没有改writeMethod则忽略掉该字段的操作，这样就可以避免异常的发生。
     */
}
