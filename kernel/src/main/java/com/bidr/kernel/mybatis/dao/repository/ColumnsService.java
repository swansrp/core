package com.bidr.kernel.mybatis.dao.repository;

import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.kernel.mybatis.dao.entity.Columns;
import com.bidr.kernel.mybatis.dao.mapper.ColumnsMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Title: ColumnsService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2022/09/26 23:28
 */
@Service
public class ColumnsService extends BaseSqlRepo<ColumnsMapper, Columns> {

    public List<Columns> getColumns(String sqlDbName, String sqlTableName) {
        LambdaQueryWrapper<Columns> wrapper = super.getQueryWrapper().eq(Columns::getTableSchema, sqlDbName)
                .eq(Columns::getTableName, sqlTableName);
        return super.select(wrapper);
    }
}
