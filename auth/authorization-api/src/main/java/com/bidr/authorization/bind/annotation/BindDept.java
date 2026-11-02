package com.bidr.authorization.bind.annotation;

import com.bidr.kernel.config.response.BindRepo;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

/**
 * 通过 deptId 绑定部门名称到当前字段。
 * <p>
 * 本注解是 {@link BindRepo} 的快捷方式，内部通过元注解 + {@code @AliasFor} 桥接到 @BindRepo，
 * 由 {@code BindRepoHandler} 统一处理，无需单独的 Handler 类。
 * <p>
 * {@code entity()} 声明为占位实体 {@link DeptRef}（T2.3c 起）：本注解不直接耦合部门实体类，
 * 运行时经 {@code BindRepoResolver} SPI 兜底解析——authorization server 默认解析为
 * {@code AcDept}，子系统由 identity-mirror 解析为身份镜像（T2.3d）。
 * 既有全库用法零变化（同 FQN 搬迁自 authorization server）。
 * <p>
 * 简写示例：
 * <pre>
 * &#64;BindDept("deptId")
 * private String deptName;
 * </pre>
 * 等价于：
 * <pre>
 * &#64;BindRepo(entity = DeptRef.class, matchField = "deptId", sourceField = "deptId")
 * private String deptName;
 * </pre>
 *
 * @author Sharp
 * @since 2026/07/09
 */
@BindRepo(entity = DeptRef.class, matchField = "deptId", sourceField = "")
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface BindDept {

    /**
     * 源字段名称，该字段的值将作为 deptId 去查询部门实体（占位解析后）的 name
     * <p>
     * 通过 {@link AliasFor} 桥接到 {@link BindRepo#sourceField()}
     *
     * @return 源字段名称
     */
    @AliasFor(annotation = BindRepo.class, attribute = "sourceField")
    String value();
}
