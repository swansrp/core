package com.bidr.forge.bo;

import com.bidr.forge.dao.entity.SysDataset;
import com.bidr.forge.dao.entity.SysDatasetColumn;
import com.diboot.core.binding.annotation.BindEntityList;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Title: DatasetColumns
 *
 * @author ZhangFeihao
 * @since 2025/12/25 14:50
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DatasetColumns extends SysDataset {
    @BindEntityList(entity = SysDatasetColumn.class, condition = "this.id=dataset_id")
    private List<SysDatasetColumn> columns;
}
