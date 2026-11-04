package com.bidr.identity.mirror.service;

import com.bidr.identity.mirror.IdentityMirrorProperties;
import org.junit.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.Assert.assertNull;

/**
 * Title: IdentitySnapshotClientAssemblyTest
 * Description: 装配冒烟腿（review 第 2 轮 N-C-1 防回归）——{@code @Component} 类在宿主容器
 * （BaseApplication 组件扫描形态）必须能走构造决议创建 bean。修复轮 1 曾把隐式单构造换成
 * 显式双构造且无 {@code @Autowired}：Spring 5.3 构造器决议返回 null ⇒ 引 jar 的宿主启动即炸
 * （NoSuchMethodException: &lt;init&gt;()），而 mirror 全部单测不过 Spring 上下文故全绿假象。
 * <p>
 * {@link ApplicationContextRunner} 挂真 {@code IdentitySnapshotClient} 类＝真构造决议通道：
 * 双构造无注解形态下本测试红（BeanCreationException），生产构造标 {@code @Autowired} 后绿。
 * 判据＝启动零失败（getStartupFailure()==null），bean 可取由启动成功蕴含。
 *
 * @author main-thread (修复轮2, 2026-11-03)
 * @since 2026-11-03
 */
public class IdentitySnapshotClientAssemblyTest {

    @Test
    public void 组件扫描形态_生产构造装配_启动零失败() {
        new ApplicationContextRunner()
                .withUserConfiguration(IdentitySnapshotClient.class)
                .withBean(IdentityMirrorProperties.class)
                .run(context -> assertNull("宿主容器启动失败: "
                                + context.getStartupFailure(),
                        context.getStartupFailure()));
    }
}
