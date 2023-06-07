package com.bidr.platform.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.StringUtil;
import com.bidr.platform.dao.entity.SysConfig;
import com.bidr.platform.dao.mapper.SysConfigDao;
import com.bidr.platform.vo.params.QuerySysConfigReq;
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

    public Page<SysConfig> querySysConfig(QuerySysConfigReq req) {
        LambdaQueryWrapper<SysConfig> wrapper = super.getQueryWrapper()
                .eq(FuncUtil.isNotEmpty(req.getName()), SysConfig::getConfigId, req.getName());
        return super.select(wrapper, req);
    }
}





