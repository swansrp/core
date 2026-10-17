package com.bidr.platform.validate.dict;

import com.bidr.kernel.constant.dict.Dict;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.*;

/**
 * Title: DictValid
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/09/06 09:57
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {DictValidator.class})
@Documented
public @interface DictValid {

    String message() default "输入的参数不合法";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /**
     * 枚举类
     *
     * @return 枚举类
     */
    Class<? extends Dict>[] value() default {};
}
