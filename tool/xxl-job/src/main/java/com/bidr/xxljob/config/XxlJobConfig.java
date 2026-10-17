package com.bidr.xxljob.config;

import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.NetUtil;
import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;

/**
 * Title: XxlJobConfig
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/01/04 16:07
 */
@Slf4j
public class XxlJobConfig implements BeanPostProcessor {

    @Value("${xxl-job.admin.addresses}")
    private String adminAddresses;
    @Value("${xxl-job.executor.app-name}")
    private String appName;

    @Value("${xxl-job.executor.ip:}")
    private String ip;
    @Value("${xxl-job.executor.port}")
    private int port;
    @Value("${xxl-job.executor.log-path}")
    private String logPath;
    @Value("${xxl-job.access-token}")
    private String accessToken;
    @Value("${xxl-job.executor.log-retention-days}")
    private int logRetention;

    @Bean
    public XxlJobSpringExecutor xxlJobExecutor() {
        log.info(">>>>>>>>>>> xxl-job config init.");
        XxlJobSpringExecutor xxlJobSpringExecutor = new XxlJobSpringExecutor();
        xxlJobSpringExecutor.setAdminAddresses(adminAddresses);
        xxlJobSpringExecutor.setAppname(appName);
        if (FuncUtil.isEmpty(ip)) {
            ip = NetUtil.getLocalIp();
        }
        xxlJobSpringExecutor.setIp(ip);
        xxlJobSpringExecutor.setPort(port);
        xxlJobSpringExecutor.setAccessToken(accessToken);
        xxlJobSpringExecutor.setLogPath(logPath);
        xxlJobSpringExecutor.setLogRetentionDays(logRetention);
        return xxlJobSpringExecutor;
    }

}
