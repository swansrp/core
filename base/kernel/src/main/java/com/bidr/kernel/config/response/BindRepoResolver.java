package com.bidr.kernel.config.response;

import com.bidr.kernel.mybatis.repository.BaseSqlRepo;

import java.util.Optional;

/**
 * {@link BindRepo} 实体解析 SPI —— 占位实体到真实 Repo 的兜底解析器。
 * <p>
 * 解析语义（{@link BindRepoHandler} 内固化为两段链，<b>精确匹配优先、SPI 兜底</b>）：
 * <ol>
 *     <li><b>精确匹配优先</b>：注解 {@code entity()} 指向的类若已被容器内某个
 *         {@link BaseSqlRepo} Bean 以 entityClass 索引（repoIndex），直接命中，SPI 不参与；</li>
 *     <li><b>SPI 兜底</b>：repoIndex miss 时（典型＝{@code entity()} 为占位标记类，如
 *         authorization-api 的 {@code UserRef}/{@code DeptRef}），遍历容器内全部
 *         {@code BindRepoResolver} Bean，取第一个返回非 empty 的结果；</li>
 *     <li>仍无解析 ⇒ 维持既有行为：log.warn + 跳过绑定（不抛异常）。</li>
 * </ol>
 * <p>
 * 解析结果的 {@link BindTarget#getEffectiveEntityClass() 有效实体类}才是列名解析
 * （@TableField/@TableId → 列名）、{@code valid} 条件追加、condition 中间实体判定的依据——
 * <b>不是</b>注解原样声明的占位类。
 * <p>
 * 出处：T2.3c（@BindUser/@BindDept 与 AcUser/AcDept 解绑——authorization 侧默认解析为
 * AcUser/AcDept）／T2.3d（子系统侧 identity-mirror 解析为身份镜像 idm_user_mirror）。
 * 一个占位实体允许存在多个解析器 Bean（不同模块各自注册，运行容器内按注册序取首个命中）。
 *
 * @author Sharp
 * @since 2026/11/03
 */
public interface BindRepoResolver {

    /**
     * 解析占位实体类（或任何 repoIndex 未覆盖的实体类）到真实 Repo + 有效实体类。
     *
     * @param entityClass {@code @BindRepo.entity()} 声明的类（可能是占位标记类）
     * @return 命中时返回 {@link BindTarget}；不认识该类时返回 {@link Optional#empty()}
     */
    Optional<BindTarget> resolve(Class<?> entityClass);

    /**
     * 解析结果载体：真实 Repo + 有效实体类。
     * <p>
     * 两个成员均不可为 null（实现类自查，缺任一则该解析结果被 {@link BindRepoHandler} 忽略）。
     */
    class BindTarget {

        /**
         * 真实 Repo（占位类的落地查询通道）
         */
        private final BaseSqlRepo<?, ?> repo;

        /**
         * 有效实体类：列名解析 / valid 判定 / nameIndex 一律以此为准，而非注解原样声明的占位类
         */
        private final Class<?> effectiveEntityClass;

        public BindTarget(BaseSqlRepo<?, ?> repo, Class<?> effectiveEntityClass) {
            this.repo = repo;
            this.effectiveEntityClass = effectiveEntityClass;
        }

        public BaseSqlRepo<?, ?> getRepo() {
            return repo;
        }

        public Class<?> getEffectiveEntityClass() {
            return effectiveEntityClass;
        }
    }
}
