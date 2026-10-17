package com.bidr.kernel.mybatis.bo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Title: SqlColumn
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/9/17 14:16
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SqlColumn {
    private String sql;
    private String alias;
}
