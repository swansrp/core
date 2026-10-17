package com.bidr.kernel.config.response;

import java.util.List;

/**
 * Title: DictBinder
 * Description: 字典绑定 SPI —— {@link BindDict} 注解的数据源桥接接口。
 * kernel 不依赖具体字典实现，由上层模块（platform）提供 Spring Bean；
 * RespConvert 处理 @BindDict 时经容器查找实现，找不到则跳过。
 * Copyright: Copyright (c) 2024 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2026/09/08
 */
public interface DictBinder {

    /**
     * 将 voList 中 getFieldName 字段的字典 value 翻译为 label，回填到 setFieldName 字段。
     * <p>
     * 支持逗号分隔的多值：每个值分别翻译后以逗号拼回。
     *
     * @param voList       VO 列表
     * @param setFieldName label 回填字段名
     * @param getFieldName value 取值字段名
     * @param type         字典类型编码
     */
    void bindItemLabel(List<?> voList, String setFieldName, String getFieldName, String type);
}
