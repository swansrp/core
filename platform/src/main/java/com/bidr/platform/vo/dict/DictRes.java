package com.bidr.platform.vo.dict;

import com.bidr.kernel.vo.common.KeyValueResVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Title: DictRes
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/8/22 9:19
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DictRes extends KeyValueResVO {
    private String show;
}
