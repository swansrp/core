package com.bidr.admin.service.excel.listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.read.metadata.holder.ReadSheetHolder;
import com.bidr.admin.service.excel.handler.PortalExcelParseHandlerInf;
import com.bidr.admin.service.excel.progress.PortalExcelUploadProgressInf;
import com.bidr.admin.vo.PortalWithColumnsRes;
import com.bidr.kernel.utils.FuncUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: BasePortalExcelListener
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/02/21 11:23
 */
@Slf4j
@SuppressWarnings("rawtypes, unchecked")
public abstract class BasePortalExcelListener<T> extends AnalysisEventListener<T> {
    protected final List dataList;
    protected final PortalWithColumnsRes portal;
    protected final PortalExcelUploadProgressInf uploadProgress;
    protected final Map<String, T> entityCache;
    protected final PortalExcelParseHandlerInf portalExcelParseHandlerInf;
    protected final Map<Object, Object> validateMap;
    protected final Integer BATCH_SIZE = 100;
    protected Integer maxLine = -1;

    public BasePortalExcelListener(PortalWithColumnsRes portal, PortalExcelParseHandlerInf portalExcelParseHandlerInf,
                                   PortalExcelUploadProgressInf uploadProgress) {
        this.dataList = new ArrayList<>();
        this.validateMap = new HashMap<>();
        this.entityCache = new HashMap<>();
        this.portal = portal;
        this.portalExcelParseHandlerInf = portalExcelParseHandlerInf;
        this.uploadProgress = uploadProgress;
    }

    @Override
    public void onException(Exception exception, AnalysisContext context) throws Exception {
        int row = context.readRowHolder().getRowIndex() + 1;
        uploadProgress.uploadProgressException(
                "上传数据[" + portal.getDisplayName() + "]+第" + row + "行数据出错,请检查");
        super.onException(exception, context);
    }

    @Override
    public void invoke(T data, AnalysisContext analysisContext) {
        if (maxLine == -1) {
            ReadSheetHolder sheetHolder = analysisContext.readSheetHolder();
            maxLine = sheetHolder.getApproximateTotalRowNumber() - sheetHolder.getHeadRowNumber();
            uploadProgress.startValidateRecord(maxLine);
        }
        Object entity = parse(portal, data, entityCache);
        prepare(entity);
        validate(entity, dataList, validateMap);
        dataList.add(entity);
        uploadProgress.addUploadProgress(dataList.size());
    }

    /**
     * 将excel读取的数据进行对象转换
     *
     * @param portal      转换配置名称
     * @param data        读取的数据
     * @param entityCache 相关实体值缓存
     * @return 对象
     */
    protected abstract Object parse(PortalWithColumnsRes portal, T data, Map<String, T> entityCache);

    /**
     * 预处理数据
     *
     * @param entity 数据
     */
    protected abstract void prepare(Object entity);

    /**
     * 对excel读取的数据进行有效性检测
     *
     * @param entity      数据
     * @param cachedList  已经读取的数据
     * @param validateMap 有效性检测缓存
     */
    protected abstract void validate(Object entity, List<Object> cachedList, Map<Object, Object> validateMap);

    @Override
    public void doAfterAllAnalysed(AnalysisContext analysisContext) {
        if (FuncUtil.isNotEmpty(dataList)) {
            uploadProgress.startSaveRecord();
            int pageSize = Math.min(dataList.size(), BATCH_SIZE);
            int result = 0;
            List<Object> cachedList = new ArrayList<>();
            try {
                for (Object entity : dataList) {
                    cachedList.add(entity);
                    result++;
                    if (cachedList.size() == pageSize) {
                        handle(cachedList);
                        afterHandle(cachedList);
                        cachedList.clear();
                        uploadProgress.addUploadProgress(result);
                    }
                }
                if (FuncUtil.isNotEmpty(cachedList)) {
                    handle(cachedList);
                    afterHandle(cachedList);
                }
                uploadProgress.uploadProgressFinish();
            } catch (Exception e) {
                log.error("处理excel数据失败", e);
                uploadProgress.uploadProgressException(e.getMessage());
            }
        } else {
            uploadProgress.uploadProgressFinish();
        }
    }

    /**
     * 对excel读取的数据进行数据库处理
     *
     * @param cachedList 待存数据
     */
    protected abstract void handle(List<Object> cachedList);

    /**
     * 数据存入数据库后处理
     *
     * @param cachedList 待存数据
     */
    protected abstract void afterHandle(List<Object> cachedList);
}
