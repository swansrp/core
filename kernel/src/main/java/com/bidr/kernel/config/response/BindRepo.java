package com.bidr.kernel.config.response;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 通用字段绑定注解 —— 通过实体类自动查找 BaseSqlRepo，批量查询并回填目标字段。
 * <p>
 * 无需为每种实体编写单独的 Handler 类，一个 {@link BindRepoHandler} 统一处理所有 @BindRepo 注解。
 * <p>
 * 使用示例：
 * <pre>
 * // 通过 customerNumber 查 AcUser.name
 * &#64;BindRepo(entity = AcUser.class, matchField = "customerNumber", sourceField = "firstFilledBy")
 * private String firstFilledByName;
 *
 * // 通过 deptId 查 AcDept.name
 * &#64;BindRepo(entity = AcDept.class, matchField = "deptId", sourceField = "deptId")
 * private String deptName;
 *
 * // 通过 roleId 查 AcRole.roleName（extractField 不是 name 时需指定）
 * &#64;BindRepo(entity = AcRole.class, matchField = "roleId", extractField = "roleName", sourceField = "roleId")
 * private String roleName;
 * </pre>
 *
 * @author Sharp
 * @since 2026/07/09
 */
@Target({ElementType.FIELD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface BindRepo {

    /**
     * 实体类，用于自动查找对应的 BaseSqlRepo Bean
     *
     * @return 实体 Class
     */
    Class<?> entity();

    /**
     * 匹配字段的 Java 属性名，用于构建 WHERE IN 查询条件
     * <p>
     * 如 "customerNumber"、"deptId"，自动解析为数据库列名
     *
     * @return Java 属性名
     */
    String matchField();

    /**
     * 提取字段的 Java 属性名，即需要回填到当前字段的值
     * <p>
     * 默认 "name"，大多数场景适用
     *
     * @return Java 属性名
     */
    String extractField() default "name";

    /**
     * 源字段名称 —— VO 上用于匹配的字段
     * <p>
     * 如 @BindRepo(sourceField = "firstFilledBy") 表示从 VO 的 firstFilledBy 字段取值作为查询 key
     *
     * @return 源字段名称
     */
    String sourceField();
}
