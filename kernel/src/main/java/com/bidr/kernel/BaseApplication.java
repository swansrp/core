package com.bidr.kernel;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Title: BaseApplication
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/09 13:20
 */

@EnableRetry
@ServletComponentScan
@SpringBootApplication
@EnableTransactionManagement
@MapperScan({"com.bidr.**.dao.**.mapper"})
@ComponentScan(basePackages = {"com.bidr", "com.diboot"})
public abstract class BaseApplication {
}
