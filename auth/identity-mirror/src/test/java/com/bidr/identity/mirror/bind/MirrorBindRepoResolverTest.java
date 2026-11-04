package com.bidr.identity.mirror.bind;

import com.bidr.authorization.bind.annotation.DeptRef;
import com.bidr.authorization.bind.annotation.UserRef;
import com.bidr.identity.mirror.dao.entity.IdmUserMirror;
import com.bidr.identity.mirror.dao.repository.IdmUserMirrorService;
import com.bidr.kernel.config.response.BindRepoResolver;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Optional;

/**
 * Title: MirrorBindRepoResolverTest
 * Description: 镜像解析器映射单测（T2.3d，纯 JUnit4＋Mockito）——UserRef → IdmUserMirror
 * 的 repo＋有效实体两行映射断言，及其余占位/任意类 empty（互斥共存语义：server 侧
 * DeptRef 有解析、mirror 侧没有——两解析器各管各的容器）。
 *
 * @author Sharp
 * @since 2026/11/03
 */
public class MirrorBindRepoResolverTest {

    @Test
    public void resolvesUserRefToUserMirror() {
        IdmUserMirrorService repo = Mockito.mock(IdmUserMirrorService.class);
        MirrorBindRepoResolver resolver = new MirrorBindRepoResolver(repo);

        Optional<BindRepoResolver.BindTarget> hit = resolver.resolve(UserRef.class);

        Assert.assertTrue("UserRef 必须命中镜像解析", hit.isPresent());
        Assert.assertSame("查询通道＝IdmUserMirrorService（镜像 repo bean）", repo, hit.get().getRepo());
        Assert.assertEquals("有效实体类＝IdmUserMirror（列解析/valid 判定按它走）",
                IdmUserMirror.class, hit.get().getEffectiveEntityClass());
    }

    @Test
    public void otherClassesResolveEmpty() {
        MirrorBindRepoResolver resolver = new MirrorBindRepoResolver(Mockito.mock(IdmUserMirrorService.class));

        Assert.assertFalse("DeptRef 不归镜像解析器管（server 侧 DefaultBindRepoResolver 的领地）",
                resolver.resolve(DeptRef.class).isPresent());
        Assert.assertFalse("任意未声明类一律 empty", resolver.resolve(Object.class).isPresent());
    }
}
