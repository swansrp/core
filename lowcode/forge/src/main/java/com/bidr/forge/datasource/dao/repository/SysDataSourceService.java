package com.bidr.forge.datasource.dao.repository;

import com.bidr.forge.datasource.dao.entity.SysDataSource;
import com.bidr.forge.datasource.dao.mapper.SysDataSourceDao;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Title: SysDataSourceService
 * Description: 数据源配置表仓储（bean 名须与实体名对应：
 * BaseAdminController 按 decapitalize(entitySimpleName) + "Service" 查找）
 *
 * @author Sharp
 * @since 2026/8/18
 */
@Service
public class SysDataSourceService extends BaseSqlRepo<SysDataSourceDao, SysDataSource> {

    /** 缓存加载：全量取出，内存按 dsName 索引 */
    public List<SysDataSource> getDataSourceCache() {
        return super.select(super.getQueryWrapper());
    }
}
