package com.bidr.kernel.mybatis.bo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Title: DynamicColumn
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/5/16 14:50
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DynamicColumn {
    String condition;
    String script;
    String prefix;
    String suffix;
}
