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
 * 提供两种绑定方式，{@link #condition()} 非空时优先于 matchField/sourceField：
 * <ol>
 *     <li><b>简单/复合字段绑定</b>：{@link #matchField()} + {@link #sourceField()}（可选
 *         {@link #matchField2()} + {@link #sourceField2()} 组成复合键），适用于源与目标直接等值关联的场景。</li>
 *     <li><b>condition 关联表达式绑定</b>：以类 SQL 的 join 表达式描述「源 VO → 0..N 个中间实体 → 目标 entity」
 *         的任意字段数、任意级关联链，泛化取代早期的中间表硬编码。</li>
 * </ol>
 *
 * <h3>condition 语法规范</h3>
 * <pre>
 * condition := joinExpr ( " AND " joinExpr )*     // AND 大小写不敏感，两侧允许空格
 * joinExpr  := ref "=" ref                        // 仅支持等值关联
 * ref       := "this." prop                       // 源 VO 的 Java 属性
 *            | SimpleName "." prop                // 中间实体的 Java 属性（实体简单类名）
 *            | prop                               // 目标 entity（{@link #entity()}）的属性（裸写）
 * </pre>
 * <ul>
 *     <li>标识一律使用 Java 属性名，Handler 内部自动解析为数据库列名（兼容反引号列、schema 前缀表名）。</li>
 *     <li>中间实体用<b>简单类名</b>（如 {@code CsqResultAnswer}），启动时扫描 {@code BaseSqlRepo} 建立索引。</li>
 *     <li>join 链从 {@code this} 出发，经 0..N 个中间实体，终于目标 entity。</li>
 *     <li>目标字段按<b>形态分派</b>（对应 diboot 四种绑定注解）：
 *         <ul>
 *             <li>List 且元素为实体 → 装载<b>全部匹配实体</b>（= @BindEntityList，一对多扇出保留）</li>
 *             <li>List 且元素为基本类型 → 装载 <b>extractField 值列表</b>（= @BindFieldList）</li>
 *             <li>非 List 且字段类型为实体 → 装载<b>首个完整实体</b>（= @BindEntity）</li>
 *             <li>其余非 List → 取首个目标实体的 {@link #extractField()} 值（= @BindField）</li>
 *         </ul></li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>
 * // 简单绑定：通过 customerNumber 查 AcUser.name
 * &#64;BindRepo(entity = AcUser.class, matchField = "customerNumber", sourceField = "firstFilledBy")
 * private String firstFilledByName;
 *
 * // 复合绑定：按 (deptId, userId) 联合匹配
 * &#64;BindRepo(entity = AcUserDept.class, matchField = "deptId", matchField2 = "userId",
 *            sourceField = "deptId", sourceField2 = "userId", extractField = "role")
 * private String role;
 *
 * // condition 单中间表一对多（this.id → CsqResultAnswer → CsqAnswerHistory）
 * &#64;BindRepo(entity = CsqAnswerHistory.class,
 *     condition = "this.id = CsqResultAnswer.csqResultId AND CsqResultAnswer.csqAnswerHistoryId = id")
 * private List&lt;CsqAnswerHistory&gt; answers;
 *
 * // condition 复合源键 + 单中间表（this.title/categoryId → CsqTemplate → CsqAnswerHistory）
 * &#64;BindRepo(entity = CsqAnswerHistory.class,
 *     condition = "this.title = CsqTemplate.name AND this.categoryId = CsqTemplate.categoryId AND CsqTemplate.id = wjxId")
 * private List&lt;CsqAnswerHistory&gt; answers;
 *
 * // condition 多级 join（this → B → C → 目标 D）
 * &#64;BindRepo(entity = D.class, condition = "this.x = B.a AND B.b = C.c AND C.d = col")
 * private List&lt;D&gt; items;
 *
 * // 装载首个完整实体（= @BindEntity，字段类型即实体类型）
 * &#64;BindRepo(entity = CsqTemplate.class, condition = "this.title = name AND this.categoryId = categoryId")
 * private CsqTemplate template;
 *
 * // 装载字段值列表（= @BindFieldList，List 元素为基本类型 + extractField）
 * &#64;BindRepo(entity = AcUser.class, matchField = "deptId", sourceField = "deptId", extractField = "id")
 * private List&lt;Long&gt; userIds;
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
     * 如 "customerNumber"、"deptId"，自动解析为数据库列名。
     * 使用 {@link #condition()} 时可省略（默认空）。
     *
     * @return Java 属性名
     */
    String matchField() default "";

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
     * 如 @BindRepo(sourceField = "firstFilledBy") 表示从 VO 的 firstFilledBy 字段取值作为查询 key。
     * 使用 {@link #condition()} 时可省略（默认空）。
     *
     * @return 源字段名称
     */
    String sourceField() default "";

    /**
     * 第二匹配字段（可选）—— 实体上与 matchField 联合定位的属性名，如复合唯一键场景
     * <p>
     * 如 @BindRepo(entity = AcUserDept.class, matchField = "deptId", matchField2 = "userId",
     * sourceField = "deptId", sourceField2 = "userId") 表示按 (deptId, userId) 联合匹配 AcUserDept
     *
     * @return 实体第二匹配属性名，空表示不启用
     */
    String matchField2() default "";

    /**
     * 第二源字段名称（可选）—— VO 上与 sourceField 联合取值的属性名
     *
     * @return VO 第二源属性名，空表示不启用
     */
    String sourceField2() default "";

    /**
     * condition 关联表达式（可选）—— 以类 SQL 的 join 表达式描述「源 VO → 0..N 个中间实体 → 目标 entity」的关联链。
     * <p>
     * 非空时优先于 matchField/sourceField 生效，支持任意字段数、任意级中间表关联。语法与示例见类级 javadoc。
     *
     * @return condition 表达式，空表示走 matchField/sourceField 简单/复合绑定
     */
    String condition() default "";
}
