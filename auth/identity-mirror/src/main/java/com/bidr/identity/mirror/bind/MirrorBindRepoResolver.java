package com.bidr.identity.mirror.bind;

import com.bidr.authorization.bind.annotation.UserRef;
import com.bidr.identity.mirror.dao.entity.IdmUserMirror;
import com.bidr.identity.mirror.dao.repository.IdmUserMirrorService;
import com.bidr.kernel.config.response.BindRepoResolver;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Title: MirrorBindRepoResolver
 * Description: 子系统侧的 {@link BindRepoResolver} 镜像实现（T2.3d）——
 * {@link UserRef} → {@link IdmUserMirrorService}＋{@link IdmUserMirror}，其余 empty。
 * <p>🔴 子系统容器装配语义（与 server 侧 DefaultBindRepoResolver 互斥共存）：两类解析器
 * 不会出现在同一运行容器——InkHub 墨枢（provider）装 authorization 的 DefaultBindRepoResolver
 * （UserRef→上游用户实体），子系统（epc）引本模块后装本类（UserRef→身份镜像）。运行容器内
 * 有谁，{@code @BindUser} 的占位实体就解析成谁，二者互不感知（BindRepoResolver SPI 的
 * 多解析器并存语义见接口 javadoc）。</p>
 *
 * @author Sharp
 * @since 2026/11/03
 */
@Component
public class MirrorBindRepoResolver implements BindRepoResolver {

    private final IdmUserMirrorService userMirrorService;

    public MirrorBindRepoResolver(IdmUserMirrorService userMirrorService) {
        this.userMirrorService = userMirrorService;
    }

    @Override
    public Optional<BindTarget> resolve(Class<?> entityClass) {
        if (entityClass == UserRef.class) {
            return Optional.of(new BindTarget(userMirrorService, IdmUserMirror.class));
        }
        return Optional.empty();
    }
}
