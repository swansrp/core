package com.bidr.forge.bo;

import com.bidr.forge.dao.entity.SysMatrix;
import com.bidr.forge.dao.entity.SysMatrixColumn;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Title: MatrixColumns
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/11/22 20:19
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MatrixColumns extends SysMatrix {
    private List<SysMatrixColumn> columns;
}