package com.bidr.authorization.bind.annotation;

import com.bidr.authorization.dao.entity.AcDept;
import com.bidr.kernel.config.response.BindRepo;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 通过 deptId 绑定 AcDept.name 到当前字段。
 * <p>
 * 本注解是 {@link BindRepo} 的快捷方式，内部通过元注解 + {@code @AliasFor} 桥接到 @BindRepo，
 * 由 {@code BindRepoHandler} 统一处理，无需单独的 Handler 类。
 * <p>
 * 简写示例：
 * <pre>
 * &#64;BindDept("deptId")
 * private String deptName;
 * </pre>
 * 等价于：
 * <pre>
 * &#64;BindRepo(entity = AcDept.class, matchField = "deptId", sourceField = "deptId")
 * private String deptName;
 * </pre>
 *
 * @author Sharp
 * @since 2026/07/09
 */
@BindRepo(entity = AcDept.class, matchField = "deptId", sourceField = "")
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface BindDept {

    /**
     * 源字段名称，该字段的值将作为 deptId 去查询 AcDept.name
     * <p>
     * 通过 {@link AliasFor} 桥接到 {@link BindRepo#sourceField()}
     *
     * @return 源字段名称
     */
    @AliasFor(annotation = BindRepo.class, attribute = "sourceField")
    String value();
}
