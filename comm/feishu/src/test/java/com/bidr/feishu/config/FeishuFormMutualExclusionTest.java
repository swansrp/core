package com.bidr.feishu.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Title: FeishuFormMutualExclusionTest
 * Description: 桥/直连两形态互斥探针（宿主同构＝BaseApplication 口径整包 ComponentScan）——
 * 四对同短名 bean 双真会让宿主起不来（ConflictingBeanDefinitionException）。
 * 全 bean lazy 化走定义级/类型级判定，不实例化（@Resource 依赖链不在探针范围）。
 * 历史事故：嵌套 Conditions（NoneNested/AllNested）在 ComponentScan 路径上评估失效
 * （本探针复现过 pan 预生产启动事故），已改直接标注
 * {@code @ConditionalOnProperty(app-id 总闸)+@ConditionalOnExpression(形态腿)}。
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author sharp
 * @since 2026/11/09
 */
class FeishuFormMutualExclusionTest {

    /**
     * 宿主同构扫描配置：BaseApplication 口径＝@ComponentScan(basePackages="com.bidr") 整包扫
     */
    @Configuration
    @ComponentScan(basePackages = "com.bidr.feishu.controller")
    public static class HostLikeScanCfg {
    }

    private static AnnotationConfigApplicationContext newCtx(String... props) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.register(HostLikeScanCfg.class);
        if (props.length > 0) {
            TestPropertyValues.of(props).applyTo(ctx.getEnvironment());
        }
        ctx.addBeanFactoryPostProcessor(bf -> {
            for (String name : bf.getBeanDefinitionNames()) {
                bf.getBeanDefinition(name).setLazyInit(true);
            }
        });
        return ctx;
    }

    @Test
    @DisplayName("缺省（feishu.direct 未配置）＝桥形态：桥 Sso/Sync 各一、direct 全跳、同名冲突不复现")
    void defaultFormRegistersBridgeOnly() {
        try (AnnotationConfigApplicationContext ctx = newCtx("feishu.app-id=cli_test")) {
            ctx.refresh();
            assertThat(ctx.getBeanNamesForType(com.bidr.feishu.controller.FeishuSsoController.class)).hasSize(1);
            assertThat(ctx.getBeanNamesForType(com.bidr.feishu.controller.FeishuSyncController.class)).hasSize(1);
            assertThat(ctx.getBeanNamesForType(com.bidr.feishu.controller.direct.FeishuSsoController.class)).isEmpty();
            assertThat(ctx.getBeanNamesForType(com.bidr.feishu.controller.direct.FeishuSyncController.class)).isEmpty();
        }
    }

    @Test
    @DisplayName("feishu.direct=true＝直连形态：direct Sso/Sync 各一、桥全跳")
    void directFormRegistersDirectOnly() {
        try (AnnotationConfigApplicationContext ctx = newCtx("feishu.app-id=cli_test", "feishu.direct=true")) {
            ctx.refresh();
            assertThat(ctx.getBeanNamesForType(com.bidr.feishu.controller.direct.FeishuSsoController.class)).hasSize(1);
            assertThat(ctx.getBeanNamesForType(com.bidr.feishu.controller.direct.FeishuSyncController.class)).hasSize(1);
            assertThat(ctx.getBeanNamesForType(com.bidr.feishu.controller.FeishuSsoController.class)).isEmpty();
            assertThat(ctx.getBeanNamesForType(com.bidr.feishu.controller.FeishuSyncController.class)).isEmpty();
        }
    }

    @Test
    @DisplayName("无 app-id＝总闸关：两形态全跳")
    void noAppIdRegistersNothing() {
        try (AnnotationConfigApplicationContext ctx = newCtx()) {
            ctx.refresh();
            assertThat(ctx.getBeanNamesForType(com.bidr.feishu.controller.FeishuSsoController.class)).isEmpty();
            assertThat(ctx.getBeanNamesForType(com.bidr.feishu.controller.FeishuSyncController.class)).isEmpty();
            assertThat(ctx.getBeanNamesForType(com.bidr.feishu.controller.direct.FeishuSsoController.class)).isEmpty();
            assertThat(ctx.getBeanNamesForType(com.bidr.feishu.controller.direct.FeishuSyncController.class)).isEmpty();
        }
    }
}
