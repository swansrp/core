package com.bidr.platform.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.platform.dao.entity.SysConfig;
import com.bidr.platform.dao.mapper.SysConfigDao;
import com.bidr.platform.vo.params.QuerySysConfigReq;
import org.springframework.stereotype.Service;

import java.util.Collection;
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
                        SysConfig::getConfigGroup, SysConfig::getRemark);
        return super.select(wrapper);
    }

    /**
     * 按 config_key 批量回填分组，只更新分组为空的存量行（幂等，可每次启动重跑）
     */
    public void fillEmptyGroup(Collection<String> configKeys, String configGroup) {
        update(null, Wrappers.<SysConfig>lambdaUpdate()
                .in(SysConfig::getConfigKey, configKeys)
                .and(w -> w.isNull(SysConfig::getConfigGroup).or().eq(SysConfig::getConfigGroup, ""))
                .set(SysConfig::getConfigGroup, configGroup));
    }

    /**
     * 按 config_key 同步参数名称/备注文案（null 表示该列不动；永不触碰 config_value 运行值）
     */
    public void refreshTitleRemark(String configKey, String configName, String remark) {
        update(null, Wrappers.<SysConfig>lambdaUpdate()
                .eq(SysConfig::getConfigKey, configKey)
                .set(configName != null, SysConfig::getConfigName, configName)
                .set(remark != null, SysConfig::getRemark, remark));
    }

    public Page<SysConfig> querySysConfig(QuerySysConfigReq req) {
        LambdaQueryWrapper<SysConfig> wrapper = super.getQueryWrapper()
                .like(FuncUtil.isNotEmpty(req.getConfigKey()), SysConfig::getConfigKey, req.getConfigKey());
        return super.select(wrapper, req);
    }
}





