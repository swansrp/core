package com.bidr.feishu.dto.sync;

import lombok.Data;

/**
 * Title: FeishuSyncResult
 * Description: 飞书组织同步结果
 * Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/01/01 00:00
 */
@Data
public class FeishuSyncResult {

    /**
     * 同步部门数
     */
    private int deptCount;

    /**
     * 同步用户数
     */
    private int accountCount;
}
