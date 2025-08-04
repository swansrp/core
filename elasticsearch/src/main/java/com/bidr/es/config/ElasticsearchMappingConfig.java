package com.bidr.es.config;

import co.elastic.clients.elasticsearch._types.analysis.Analyzer;
import co.elastic.clients.elasticsearch._types.analysis.CustomAnalyzer;
import co.elastic.clients.elasticsearch._types.analysis.TokenFilter;
import co.elastic.clients.elasticsearch._types.mapping.*;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import com.bidr.es.anno.EsField;
import com.bidr.es.anno.EsId;
import com.bidr.es.anno.EsIndex;
import com.bidr.kernel.utils.JsonUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import jakarta.json.Json;
import jakarta.json.stream.JsonGenerator;

import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.*;

/**
 * Title: ElasticsearchMappingConfig
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/8/4 11:08
 */

public class ElasticsearchMappingConfig {

    public static String toJson(Class<?> clazz) {
        EsIndex annotation = clazz.getAnnotation(EsIndex.class);
        CreateIndexRequest request = new CreateIndexRequest.Builder().index(annotation.name())
                .mappings(m -> m.properties(buildMapping(clazz))).settings(buildIndexSettings(clazz)).build();
        JacksonJsonpMapper mapper = new JacksonJsonpMapper();
        StringWriter writer = new StringWriter();
        JsonGenerator generator = mapper.jsonProvider().createGenerator(writer);

        JsonpMapper jsonpMapper = mapper;
        request.serialize(generator, jsonpMapper);
        generator.close();

        return writer.toString();
    }

    public static Map<String, Property> buildMapping(Class<?> clazz) {
        Map<String, Property> mapping = new LinkedHashMap<>();
        String allTokensField = clazz.getAnnotation(EsIndex.class).allTokenField();
        for (Field field : ReflectionUtil.getFields(clazz)) {
            Property property;
            if (field.isAnnotationPresent(EsId.class)) {
                property = new KeywordProperty.Builder().ignoreAbove(256).build()._toProperty();
            } else {
                EsField esField = field.getAnnotation(EsField.class);
                if (esField != null) {
                    property = buildPropertyFromAnnotation(field, allTokensField);
                } else {
                    property = buildPropertyFromFieldType(field, allTokensField);
                }
            }

            mapping.put(field.getName(), property);
        }
        return mapping;
    }

    public static IndexSettings buildIndexSettings(Class<?> clazz) {
        EsIndex annotation = clazz.getAnnotation(EsIndex.class);

        // analyzers
        Map<String, Analyzer> analyzer = new HashMap<>(3);
        // pinyin_analyzer
        analyzer.put("pinyin_analyzer", Analyzer.of(a -> a.custom(
                CustomAnalyzer.of(c -> c.tokenizer("ik_max_word").filter(Arrays.asList("py", "lowercase"))))));
        // ik_smart_stconvert
        analyzer.put("ik_smart_stconvert", Analyzer.of(a -> a.custom(
                CustomAnalyzer.of(c -> c.tokenizer("ik_smart").filter(Collections.singletonList("stconvert"))))));
        // suggest analyzer
        analyzer.put("suggest", Analyzer.of(a -> a.custom(CustomAnalyzer.of(
                c -> c.tokenizer("ik_max_word").filter(Arrays.asList("lowercase", "asciifolding"))))));

        // filters
        Map<String, TokenFilter> filter = new HashMap<>();

        // 拼音 filter
        Map<String, Object> pinyinFilter = new HashMap<>();
        pinyinFilter.put("type", "pinyin");
        pinyinFilter.put("keep_full_pinyin", "true");
        pinyinFilter.put("keep_joined_full_pinyin", "true");
        pinyinFilter.put("keep_original", "true");
        pinyinFilter.put("limit_first_letter_length", "16");
        pinyinFilter.put("remove_duplicated_term", "true");

        filter.put("py", TokenFilter._DESERIALIZER.deserialize(
                Json.createParser(new StringReader(JsonUtil.toJson(pinyinFilter))), new JacksonJsonpMapper()));

        // 简繁转换 filter
        Map<String, Object> stconvertFilter = new HashMap<>();
        stconvertFilter.put("type", "stconvert");
        stconvertFilter.put("keep_both", "false");
        filter.put("stconvert", TokenFilter._DESERIALIZER.deserialize(
                Json.createParser(new StringReader(JsonUtil.toJson(stconvertFilter))), new JacksonJsonpMapper()));

        return IndexSettings.of(
                builder -> builder.numberOfReplicas(annotation.replicas()).numberOfShards(annotation.shards())
                        .analysis(analysis -> analysis.analyzer(analyzer).filter(filter)));
    }

    private static Property buildPropertyFromAnnotation(Field field, String allTokensField) {
        Map<String, Property> multiFields = new HashMap<>(4);
        EsField anno = field.getAnnotation(EsField.class);
        // keyword 子字段
        if (anno.keyword()) {
            multiFields.put("keyword",
                    new KeywordProperty.Builder().ignoreAbove(anno.ignoreAbove()).docValues(anno.docValues()).build()
                            ._toProperty());
        }
        // IK 分词
        if (anno.useIk()) {
            multiFields.put(anno.ikFieldSuffix(),
                    new TextProperty.Builder().analyzer(anno.ikAnalyzer()).build()._toProperty());
        }
        // 拼音分词
        if (anno.usePinyin()) {
            multiFields.put(anno.pinyinFieldSuffix(),
                    new TextProperty.Builder().analyzer(anno.pinyinAnalyzer()).build()._toProperty());
        }
        // 简繁体分词
        if (anno.useStConvert()) {
            multiFields.put(anno.stConvertFieldSuffix(),
                    new TextProperty.Builder().analyzer(anno.stConvertAnalyzer()).build()._toProperty());
        }
        // hanLP分词
        if (anno.useHanLP()) {
            multiFields.put(anno.hanlpFieldSuffix(), new TextProperty.Builder().build()._toProperty());
        }
        EsFieldType type = anno.type();
        switch (type) {
            case TEXT: {
                TextProperty.Builder tb = new TextProperty.Builder().fields(multiFields).copyTo(allTokensField);
                tb.index(anno.index());
                return tb.build()._toProperty();
            }
            case KEYWORD:
                return new KeywordProperty.Builder().ignoreAbove(anno.ignoreAbove()).docValues(anno.docValues()).build()
                        ._toProperty();
            case INTEGER:
                return new IntegerNumberProperty.Builder().build()._toProperty();
            case FLOAT:
                return new FloatNumberProperty.Builder().build()._toProperty();
            case SCALED_FLOAT: {
                return new ScaledFloatNumberProperty.Builder().scalingFactor((double) anno.scalingFactor()).build()
                        ._toProperty();
            }
            case DATE:
                return new DateProperty.Builder().format("yyyy-MM-dd HH:mm:ss||yyyy-MM-dd||epoch_millis").build()
                        ._toProperty();
            case BOOLEAN:
                return new BooleanProperty.Builder().build()._toProperty();
            case COMPLETION:
                return new CompletionProperty.Builder().analyzer("suggest").searchAnalyzer("suggest").build()
                        ._toProperty();
            default:
                return new TextProperty.Builder().build()._toProperty();
        }
    }

    private static Property buildPropertyFromFieldType(Field field, String allTokensField) {
        Class<?> type = field.getType();
        if (type == String.class) {
            Map<String, Property> multiFields = new HashMap<>(2);
            // IK 分词
            multiFields.put("ik",
                    new TextProperty.Builder().analyzer("ik_max_word").copyTo(allTokensField).build()._toProperty());
            // 拼音分词
            multiFields.put("pinyin",
                    new TextProperty.Builder().analyzer("pinyin_analyzer").copyTo(allTokensField).build()
                            ._toProperty());
            return new TextProperty.Builder().fields(multiFields).build()._toProperty();
        } else if (type == Integer.class || type == int.class) {
            return new IntegerNumberProperty.Builder().build()._toProperty();
        } else if (type == Long.class || type == long.class) {
            return new LongNumberProperty.Builder().build()._toProperty();
        } else if (type == Double.class || type == double.class) {
            return new DoubleNumberProperty.Builder().build()._toProperty();
        } else if (type == Float.class || type == float.class) {
            return new FloatNumberProperty.Builder().build()._toProperty();
        } else if (type == BigDecimal.class) {
            // BigDecimal 默认用 scaled_float, scaling_factor=100
            return new ScaledFloatNumberProperty.Builder().scalingFactor(100.0).build()._toProperty();
        } else if (type == Boolean.class || type == boolean.class) {
            return new BooleanProperty.Builder().build()._toProperty();
        } else if (type == Date.class) {
            return new DateProperty.Builder().format("yyyy-MM-dd HH:mm:ss||yyyy-MM-dd||epoch_millis").build()
                    ._toProperty();
        } else {
            // 默认text
            return new TextProperty.Builder().build()._toProperty();
        }
    }
}
