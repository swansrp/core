package com.bidr.authorization.client.session;

import com.bidr.authorization.service.token.TokenService;
import com.bidr.identity.mirror.dao.mapper.IdmTenantMirrorMapper;
import com.bidr.identity.mirror.dao.mapper.IdmUserMirrorMapper;
import com.bidr.identity.mirror.dao.mapper.IdmUserTenantMirrorMapper;
import com.bidr.identity.mirror.dao.repository.IdmTenantMirrorService;
import com.bidr.identity.mirror.dao.repository.IdmUserMirrorService;
import com.bidr.identity.mirror.dao.repository.IdmUserTenantMirrorService;
import com.bidr.kernel.mybatis.service.TableSyncService;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;

import static org.mockito.Mockito.mock;

/**
 * {@link SubsystemSessionAutoConfiguration} 装配/退位腿。
 *
 * <p>钉三件事：① <b>主系统形态</b>——classpath 无 {@code identity-mirror}（optional 依赖不传递）
 * ⇒ 自动装配退位、不注册会话 bean（主系统不建 idm_* 表、不参与子系统闸）；② 镜像类在但
 * 镜像 repo bean／{@link TokenService} 缺失 ⇒ 同样退位（不把半装配的活 bean 塞进宿主）；
 * ③ 子系统形态（三件镜像 repo＋TokenService 在位）⇒ 正常注册，且 {@code AuthzCheckApi} 缺席
 * 不影响装配（零租户回判在运行期 fail-closed）。</p>
 *
 * <p>🔴 退位靠 {@code @ConditionalOnClass(name=...)} 字符串名判定：本测试用
 * {@link FilteredClassLoader} 隐藏镜像类模拟主系统，若条件写成 Class 字面量，此腿会
 * {@code NoClassDefFoundError} 而非静默退位——这正是要钉住的。</p>
 *
 * @author Sharp
 * @since 2026-11-04
 */
public class SubsystemSessionAutoConfigurationTest {

    private static final String BEAN_NAME = "subsystemSessionService";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SubsystemSessionAutoConfiguration.class));

    /**
     * 三件镜像 repo 的 mock bean——repo 基类带 {@code @Resource} 注入点（事务管理器/表同步/baseMapper），
     * 上下文里补齐这些依赖，mock bean 才不会被装配期反注入打穿。
     */
    private ApplicationContextRunner withMirrorBeans(ApplicationContextRunner contextRunner) {
        return contextRunner
                .withBean("idmUserMirrorService", IdmUserMirrorService.class,
                        () -> mock(IdmUserMirrorService.class))
                .withBean("idmUserTenantMirrorService", IdmUserTenantMirrorService.class,
                        () -> mock(IdmUserTenantMirrorService.class))
                .withBean("idmTenantMirrorService", IdmTenantMirrorService.class,
                        () -> mock(IdmTenantMirrorService.class))
                .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
                .withBean(TableSyncService.class, () -> mock(TableSyncService.class))
                .withBean("idmUserMirrorMapper", IdmUserMirrorMapper.class,
                        () -> mock(IdmUserMirrorMapper.class))
                .withBean("idmUserTenantMirrorMapper", IdmUserTenantMirrorMapper.class,
                        () -> mock(IdmUserTenantMirrorMapper.class))
                .withBean("idmTenantMirrorMapper", IdmTenantMirrorMapper.class,
                        () -> mock(IdmTenantMirrorMapper.class));
    }

    @Test
    public void 镜像模块不在classpath_自动装配退位() {
        runner.withClassLoader(new FilteredClassLoader(IdmUserMirrorService.class))
                .run(context -> {
                    Assert.assertFalse("主系统无镜像模块，会话编排必须退位（不建 idm_* 表、不注册 bean）",
                            context.containsBean(BEAN_NAME));
                    Assert.assertFalse(context.containsBeanDefinition(BEAN_NAME));
                    Assert.assertTrue(context.getBeansOfType(SubsystemSessionService.class).isEmpty());
                });
    }

    @Test
    public void 镜像类在但repo_bean未注册_不注册会话编排() {
        runner.withBean(TokenService.class, () -> mock(TokenService.class))
                .run(context -> {
                    Assert.assertNotNull("上下文必须正常起（退位而非启动失败）", context);
                    Assert.assertFalse("镜像 repo bean 不在位时会话编排不得注册",
                            context.containsBean(BEAN_NAME));
                });
    }

    @Test
    public void 镜像repo在位但TokenService缺席_不注册会话编排() {
        withMirrorBeans(runner)
                .run(context -> Assert.assertFalse("缺 TokenService 时会话编排不得注册（否则签发链必炸）",
                        context.containsBean(BEAN_NAME)));
    }

    @Test
    public void 子系统镜像三件加TokenService在位_注册且AuthzCheckApi缺席不阻断() {
        withMirrorBeans(runner)
                .withBean(TokenService.class, () -> mock(TokenService.class))
                .run(context -> {
                    Assert.assertTrue("子系统形态必须注册会话编排", context.containsBean(BEAN_NAME));
                    SubsystemSessionService service = context.getBean(SubsystemSessionService.class);
                    Assert.assertNotNull(service);
                    // AuthzCheckApi 未装配＝零租户一律拒（fail-closed），不阻断装配本身
                    Assert.assertFalse(service.isPlatformAdmin(88L));
                    Assert.assertEquals(1, context.getBeansOfType(SubsystemSessionService.class).size());
                });
    }
}
