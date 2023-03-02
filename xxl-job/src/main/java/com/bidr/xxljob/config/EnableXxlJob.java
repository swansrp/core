package com.bidr.xxljob.config;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * Title: EnableXxlJob
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/01/16 19:50
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import({XxlJobConfig.class})
public @interface EnableXxlJob {
}
