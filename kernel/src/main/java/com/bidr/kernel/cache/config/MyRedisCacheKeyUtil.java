package com.bidr.kernel.cache.config;

import com.bidr.kernel.utils.BeanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;

/**
 * Title: MyRedisCacheKeyUtil
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/8/12 10:55
 */
@Slf4j
public class MyRedisCacheKeyUtil {

    private static final String PROJECT_NAME = "my.project.name";
    private static final String DEFAULT_PROJECT_NAME = "Sharp";
    private static final String SEP = ":";

    public static StringBuilder buildCacheKey(String serviceName, String methodName) {
        Environment environment = (Environment) BeanUtil.getBean("environment");
        String projectName = environment.getProperty(PROJECT_NAME, DEFAULT_PROJECT_NAME);
        int endIndex = serviceName.indexOf("$$");
        if (endIndex != -1) {
            serviceName = serviceName.substring(0, endIndex);
        }
        return new StringBuilder().append(projectName).append(SEP).append(serviceName).append(SEP).append(methodName);

    }
}
