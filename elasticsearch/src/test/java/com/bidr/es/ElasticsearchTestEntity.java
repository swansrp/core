package com.bidr.es;

import com.bidr.es.anno.EsField;
import com.bidr.es.anno.EsId;
import com.bidr.es.anno.EsIndex;
import com.bidr.es.config.EsFieldType;
import com.bidr.es.dao.entity.BaseElasticsearchEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Title: ElasticsearchTestEntity
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/8/3 11:44
 */
@Data
@EqualsAndHashCode(callSuper = true)
@EsIndex
public class ElasticsearchTestEntity extends BaseElasticsearchEntity {
    @EsId
    private String id;
    @EsField(useIk = true, usePinyin = true, useStConvert = true)
    private String name;
    @EsField
    private String standard;
    @EsField(usePinyin = true)
    private String pinyin;
    @EsField(useIk = true)
    private String ik;
    @EsField(useStConvert = true)
    private String stConvert;

    @EsField(type = EsFieldType.INTEGER)
    private Integer age;

}