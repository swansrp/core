package com.bidr.forge.engine.builder;

import com.bidr.forge.engine.builder.base.SqlBuilderDeleteInf;
import com.bidr.forge.engine.builder.base.SqlBuilderInsertInf;
import com.bidr.forge.engine.builder.base.SqlBuilderQueryInf;
import com.bidr.forge.engine.builder.base.SqlBuilderUpdateInf;

/**
 * SQL构建器统合接口
 * 继承所有子接口，提供完整的SQL构建能力
 *
 * @author Sharp
 * @since 2025-11-24
 */
public interface SqlBuilder extends SqlBuilderQueryInf, SqlBuilderInsertInf, SqlBuilderUpdateInf, SqlBuilderDeleteInf {
    // 统合所有SQL构建能力
    // 具体实现由 MatrixSqlBuilder 和 DatasetSqlBuilder 提供
}
