package com.bidr.document;

/**
 * Title: VisionConfigProvider
 * Description: <b>视觉配置口</b>——内置视觉腿的模型配置解析端点（解耦「core 的转录能力」与
 * 「llm 的模型配置机制」）：显式传入优先，否则问本口；返回 null＝取不到（门面据此抛带操作
 * 指引的 {@link IllegalStateException}，🔴 不许改成静默出空 md）。
 * <p>
 * ai/llm 侧出默认适配器（包 {@code ModelConfigProvider}，purpose=VISION：数据库系统参数优先、
 * {@code llm.vision.*} yaml 回落）；本模块零 sys_config、不依赖 platform（R-9）。
 *
 * @author Sharp
 * @since 2026/10/20
 */
public interface VisionConfigProvider {

    /**
     * 解析多模态模型端点；取不到返回 null（不抛——抛错的指引归门面统一给）。
     */
    VisionEndpoint resolve();
}
