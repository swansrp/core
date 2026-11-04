package com.bidr.authorization.bind;

import com.bidr.authorization.bind.annotation.DeptRef;
import com.bidr.authorization.bind.annotation.UserRef;
import com.bidr.authorization.dao.entity.AcDept;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.repository.AcDeptService;
import com.bidr.authorization.dao.repository.AcUserService;
import com.bidr.kernel.config.response.BindRepoResolver;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * authorization server 侧的默认 {@link BindRepoResolver}：占位实体 → 本域真实实体（T2.3c）。
 * <p>
 * 解析表（精确匹配优先链的 SPI 兜底段，见 {@link BindRepoResolver} 类注释）：
 * <ul>
 *     <li>{@link UserRef} → {@link AcUserService}（即 repoIndex 中 AcUser 的同一个 Bean）＋ {@link AcUser}</li>
 *     <li>{@link DeptRef} → {@link AcDeptService} ＋ {@link AcDept}</li>
 *     <li>其余 → {@link Optional#empty()}（留待其他解析器，如 T2.3d 的 identity-mirror）</li>
 * </ul>
 * 选型：直接构造注入 {@link AcUserService}/{@link AcDeptService}（它们本身就是容器内
 * AcUser/AcDept 的 BaseSqlRepo Bean，与 BindRepoHandler.repoIndex 索引到的是同一批实例）——
 * 不二次扫描容器、不复用 handler 内部索引，是最省路的同源取法。
 * <p>
 * 子系统侧的 identity-mirror 解析器（UserRef → idm_user_mirror）见 T2.3d，与本类互不感知：
 * 运行容器内有谁，占位实体就解析成谁。
 *
 * @author Sharp
 * @since 2026/11/03
 */
@Component
public class DefaultBindRepoResolver implements BindRepoResolver {

    private final AcUserService acUserService;

    private final AcDeptService acDeptService;

    public DefaultBindRepoResolver(AcUserService acUserService, AcDeptService acDeptService) {
        this.acUserService = acUserService;
        this.acDeptService = acDeptService;
    }

    @Override
    public Optional<BindTarget> resolve(Class<?> entityClass) {
        if (entityClass == UserRef.class) {
            return Optional.of(new BindTarget(acUserService, AcUser.class));
        }
        if (entityClass == DeptRef.class) {
            return Optional.of(new BindTarget(acDeptService, AcDept.class));
        }
        return Optional.empty();
    }
}
