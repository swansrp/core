package com.bidr.es.dao.entity;

import com.bidr.es.anno.EsField;
import com.bidr.es.config.EsFieldType;

/**
 * Title: BaseElasticsearchEntity
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/8/1 9:32
 */

public abstract class BaseElasticsearchEntity {
    /**
     * 自动补全
     */
    @EsField(type = EsFieldType.COMPLETION)
    private String suggest;
}
