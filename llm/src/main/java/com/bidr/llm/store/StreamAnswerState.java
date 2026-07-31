package com.bidr.llm.store;

import lombok.Data;

/**
 * 流式回答的当前状态快照。
 * <p>
 * 记录一次流式生成的最新全量内容与完成标记，由 {@link StreamAnswerStore} 存取。
 * 业务方如需附带额外结构（如图片列表），自行序列化后放入 {@link #extra} 字段。
 * </p>
 *
 * @author Sharp
 */
@Data
public class StreamAnswerState {

    /**
     * 当前已生成的全量内容（每次更新覆盖，非增量）
     */
    private String content;

    /**
     * 流是否已结束（正常完成/失败/截断收口均置 true）
     */
    private boolean finish;

    /**
     * 业务自定义扩展数据（JSON 字符串，框架不解析）
     */
    private String extra;
}
