package com.bidr.platform.vo.upload;

import com.bidr.platform.constant.upload.UploadProgressStep;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Title: PortalUploadProgressRes
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/02/16 16:37
 */
@Data
@NoArgsConstructor
public class PortalUploadProgressRes {
    private UploadProgressStep step;
    private Integer total;
    private Integer loaded;
    private List<String> comments;
    /** 过程日志（可选）：异步任务逐条追加，前端进度窗滚动展示，老调用方不感知 */
    private List<String> logs;
    /** 执行实例标识（可选）：长任务心跳机制用，标识当前任务归属实例；老记录无此字段 */
    private String ownerInstance;
    /** 心跳时间戳（可选，epoch 毫秒）：执行实例周期性刷新；查询侧发现运行中心跳超时即判定任务失联 */
    private Long heartbeat;

    public PortalUploadProgressRes(UploadProgressStep step, Integer total, Integer loaded, List<String> comments) {
        this.step = step;
        this.total = total;
        this.loaded = loaded;
        this.comments = comments;
    }

}
