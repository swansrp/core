package com.bidr.authorization.bind.annotation;

import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.kernel.config.response.BindRepo;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 通过 customerNumber 绑定 AcUser.name 到当前字段。
 * <p>
 * 本注解是 {@link BindRepo} 的快捷方式，内部通过元注解 + {@code @AliasFor} 桥接到 @BindRepo，
 * 由 {@code BindRepoHandler} 统一处理，无需单独的 Handler 类。
 * <p>
 * 简写示例：
 * <pre>
 * &#64;BindUser("firstFilledBy")
 * private String firstFilledByName;
 * </pre>
 * 等价于：
 * <pre>
 * &#64;BindRepo(entity = AcUser.class, matchField = "customerNumber", sourceField = "firstFilledBy")
 * private String firstFilledByName;
 * </pre>
 *
 * @author Sharp
 * @since 2026/07/09
 */
@BindRepo(entity = AcUser.class, matchField = "customerNumber", sourceField = "")
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface BindUser {

    /**
     * 源字段名称，该字段的值将作为 customerNumber 去查询 AcUser.name
     * <p>
     * 通过 {@link AliasFor} 桥接到 {@link BindRepo#sourceField()}
     *
     * @return 源字段名称
     */
    @AliasFor(annotation = BindRepo.class, attribute = "sourceField")
    String value();
}
