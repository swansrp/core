package com.bidr.forge.service.widetable;

import java.util.List;

/**
 * 宽表业务上下文提供者接口
 * <p>
 * 由业务层（mpbe-manage）实现，框架层通过此接口获取企业/产品等业务上下文，
 * 实现框架与业务的解耦。
 *
 * @author sharp
 */
public interface WideTableBusinessContextProvider {

    /**
     * 根据 historyId 获取业务上下文
     *
     * @param historyId 填报历史 ID
     * @return 业务上下文，找不到返回 null
     */
    WideTableBusinessContext getContext(String historyId);

    /**
     * 查询指定表单下所有已提交（status in '1','3'）的记录，排除已收集的
     *
     * @param formId             表单 ID
     * @param excludeHistoryIds  需排除的 historyId 集合（已收集过的）
     * @return 业务上下文列表
     */
    List<WideTableBusinessContext> getSubmittedHistories(String formId, List<String> excludeHistoryIds);
}
