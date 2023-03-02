package com.bidr.kernel.config.db;

import com.github.jeffreyning.mybatisplus.conf.EnableAutoFill;
import com.github.jeffreyning.mybatisplus.conf.EnableKeyGen;
import com.github.jeffreyning.mybatisplus.conf.EnableMPP;
import org.springframework.context.annotation.Configuration;

/**
 * Title: MppConfig
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2022/12/07 18:15
 */
@Configuration
@EnableMPP//启动mpp配置初始化
@EnableAutoFill//启动字段自动注入
@EnableKeyGen//启动主键自动生成
public class MppConfig {
}
