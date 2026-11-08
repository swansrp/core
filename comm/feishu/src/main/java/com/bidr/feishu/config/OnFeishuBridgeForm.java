package com.bidr.feishu.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.NoneNestedConditions;
import org.springframework.context.annotation.ConfigurationCondition;

/**
 * Title: OnFeishuBridgeForm
 * Description: 飞书桥接形态装配条件（与本模块直连形态互斥的判别腿）：
 * feishu.direct 未配置或不为 true 时放行（缺省即桥接形态）。每个桥接 bean 另自挂
 * {@code @ConditionalOnProperty(prefix = "feishu", name = "app-id")}（本模块全 bean 统一的总闸），
 * 本类只补"非直连"半边——两形态同 FQN 同路由（/web/feishu/**），合并单 jar 后靠该键严格互斥：
 * 配 feishu.direct=true ⇒ 直连 bean 全上、桥接 bean 全跳；不配 ⇒ 桥接形态。
 * 实现＝NoneNestedConditions（嵌套任一命中则整体不匹配）：嵌套腿 DirectMode 命中即非桥接。
 * 注意嵌套腿必须直接标注 @ConditionalOnProperty——AllNestedConditions/NoneNestedConditions
 * 只按注解发现嵌套腿，"继承条件类但无注解"的腿会被静默忽略成空腿（空 NoneNestedConditions
 * 恒真，探针测试抓出过）。
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-11-08
 */
public class OnFeishuBridgeForm extends NoneNestedConditions {

    public OnFeishuBridgeForm() {
        super(ConfigurationCondition.ConfigurationPhase.REGISTER_BEAN);
    }

    /**
     * 直连腿：feishu.direct=true 即判"非桥接"（整体不匹配）
     */
    @ConditionalOnProperty(name = "feishu.direct", havingValue = "true")
    static class DirectMode {
    }
}
