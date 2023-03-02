package com.bidr.kernel.mybatis.dao.repository;

import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.kernel.mybatis.dao.entity.Tables;
import com.bidr.kernel.mybatis.dao.mapper.TablesMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Title: TablesService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2022/09/26 23:28
 */
@Service
public class TablesService extends BaseSqlRepo<TablesMapper, Tables> {

    public List<Tables> getAllTables(String sqlDbName) {
        LambdaQueryWrapper<Tables> wrapper = super.getQueryWrapper().eq(Tables::getTableSchema, sqlDbName);
        return super.select(wrapper);
    }

}
