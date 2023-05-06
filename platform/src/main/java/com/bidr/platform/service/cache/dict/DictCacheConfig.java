package com.bidr.platform.service.cache.dict;

import com.bidr.platform.constant.dict.IDynamicDict;
import lombok.Data;

/**
 * Title: DictCacheConfig
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/04/26 11:03
 */
@Data
public class DictCacheConfig {
    private String readOnly;
    private Boolean dynamic = false;
    private String dictName;
    private String dictTitle;
    private Class<?> dictClazz;
    private IDynamicDict dynamicDict;
    private Integer expired;
}
