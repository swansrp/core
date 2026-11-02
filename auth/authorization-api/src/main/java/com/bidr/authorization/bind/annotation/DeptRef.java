package com.bidr.authorization.bind.annotation;

/**
 * 部门引用占位实体 —— {@link BindDept @BindDept} 的 {@code @BindRepo.entity()} 占位。
 * <p>
 * 占位实体：本类不映射任何表，也不应有对应 BaseSqlRepo。携带 {@code @BindDept} 的模块运行时由
 * {@code com.bidr.kernel.config.response.BindRepoResolver} SPI 解析为真实实体——
 * authorization server 侧默认解析为 {@code AcDept}（T2.3c），子系统侧由 identity-mirror
 * 解析为身份镜像（T2.3d）。声明方因此不必依赖任何具体部门实体类。
 *
 * @author Sharp
 * @since 2026/11/03
 */
public final class DeptRef {

    private DeptRef() {
    }
}
