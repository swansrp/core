package com.bidr.platform.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.platform.dao.entity.SysConfig;
import com.bidr.platform.dao.mapper.SysConfigDao;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Title: SysConfigService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2022/12/30 11:01
 */
@Service
public class SysConfigService extends BaseSqlRepo<SysConfigDao, SysConfig> {

    public List<SysConfig> getSysConfigCache() {
        LambdaQueryWrapper<SysConfig> wrapper = super.getQueryWrapper()
                .select(SysConfig::getConfigKey, SysConfig::getConfigName, SysConfig::getConfigValue,
                        SysConfig::getRemark);
        return super.select(wrapper);
    }
}





