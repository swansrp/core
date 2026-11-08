package com.bidr.feishu.event;

import com.bidr.kernel.event.BaseEvent;
import com.bidr.feishu.dto.sync.FeishuSyncResult;
import lombok.Getter;

/**
 * Title: FeishuSyncCompletedEvent
 * Description: 飞书组织同步完成事件（同步事务提交后发布，供下游模块预创建业务数据）
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author sharp
 * @since 2026/09/05 14:00
 */
@Getter
public class FeishuSyncCompletedEvent extends BaseEvent {

    /**
     * 同步结果（部门数/用户数）
     */
    private final FeishuSyncResult result;

    public FeishuSyncCompletedEvent(Object source, FeishuSyncResult result) {
        super(source);
        this.result = result;
    }
}
