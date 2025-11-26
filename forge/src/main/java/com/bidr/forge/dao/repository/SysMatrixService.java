package com.bidr.forge.dao.repository;

import com.bidr.admin.dao.entity.SysPortal;
import com.bidr.forge.bo.MatrixColumns;
import com.bidr.forge.dao.entity.SysMatrix;
import com.bidr.forge.dao.entity.SysMatrixColumn;
import com.bidr.forge.dao.mapper.SysMatrixMapper;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import org.springframework.stereotype.Service;

/**
 * 矩阵配置Repository
 *
 * @author sharp
 * @since 2025-11-20
 */
@Service
public class SysMatrixService extends BaseSqlRepo<SysMatrixMapper, SysMatrix> {

    public MatrixColumns getMatrixColumnsByPortalName(String portalName) {
        MPJLambdaWrapper<SysMatrix> wrapper = getMPJLambdaWrapper();
        wrapper.selectCollection(SysMatrixColumn.class, MatrixColumns::getColumns);
        wrapper.leftJoin(SysMatrixColumn.class, SysMatrixColumn::getMatrixId, SysMatrix::getId);
        wrapper.leftJoin(SysPortal.class, SysPortal::getReferenceId, SysMatrix::getId);
        wrapper.eq(SysPortal::getName, portalName);
        wrapper.eq(SysMatrix::getValid, CommonConst.YES);
        wrapper.eq(SysMatrixColumn::getValid, CommonConst.YES);
        wrapper.orderByAsc(SysMatrixColumn::getSort);
        return super.selectJoinOne(MatrixColumns.class, wrapper);
    }

    public MatrixColumns getMatrixColumns(String tableName) {
        MPJLambdaWrapper<SysMatrix> wrapper = getMPJLambdaWrapper();
        wrapper.selectCollection(SysMatrixColumn.class, MatrixColumns::getColumns);
        wrapper.leftJoin(SysMatrixColumn.class, SysMatrixColumn::getMatrixId, SysMatrix::getId);
        wrapper.eq(SysMatrix::getTableName, tableName);
        wrapper.eq(SysMatrix::getValid, CommonConst.YES);
        wrapper.eq(SysMatrixColumn::getValid, CommonConst.YES);
        wrapper.orderByAsc(SysMatrixColumn::getSort);
        return super.selectJoinOne(MatrixColumns.class, wrapper);
    }

    public MatrixColumns getMatrixColumns(Long id) {
        MPJLambdaWrapper<SysMatrix> wrapper = getMPJLambdaWrapper();
        wrapper.selectCollection(SysMatrixColumn.class, MatrixColumns::getColumns);
        wrapper.leftJoin(SysMatrixColumn.class, SysMatrixColumn::getMatrixId, SysMatrix::getId);
        wrapper.eq(SysMatrix::getId, id);
        wrapper.eq(SysMatrix::getValid, CommonConst.YES);
        wrapper.eq(SysMatrixColumn::getValid, CommonConst.YES);
        wrapper.orderByAsc(SysMatrixColumn::getSort);
        return super.selectJoinOne(MatrixColumns.class, wrapper);
    }
}
