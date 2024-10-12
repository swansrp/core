package com.bidr.platform.service.excel;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.util.ListUtils;
import com.bidr.kernel.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: NoModelDataListener
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2024/10/10 11:41
 */
@Slf4j
public class NoModelDataListener<T> extends AnalysisEventListener<Map<Integer, String>> {
    private final EasyExcelHandler<T> handler;
    private List<T> cachedDataList = ListUtils.newArrayList();
    private final Map<String, Object> handleContext;

    public NoModelDataListener(EasyExcelHandler<T> handler) {
        this.handler = handler;
        this.handleContext = new HashMap<>();
    }

    @Override
    public void invoke(Map<Integer, String> data, AnalysisContext context) {
        T entity = handler.parse(data, handleContext);
        log.trace("解析到一条数据:{}", JsonUtil.toJson(data));
        if (handler.validate(entity, cachedDataList, handleContext)) {
            cachedDataList.add(entity);
        }
        if (handler.save(entity, cachedDataList, handleContext)) {
            saveData();
            cachedDataList = ListUtils.newArrayList();
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        saveData();
        log.trace("所有数据解析完成！");
        handleContext.clear();
    }

    /**
     * 加上存储数据库
     */
    private void saveData() {
        log.trace("{}条数据，开始存储数据库！", cachedDataList.size());
        handler.save(cachedDataList, handleContext);
        log.trace("存储数据库成功！");
    }
}
