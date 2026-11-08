package com.bidr.feishu.config;

import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ConfigurationCondition;

/**
 * Title: OnFeishuDirectForm
 * Description: 飞书直连形态装配条件：feishu.direct=true 才放行（过渡形态，终态宿主切桥接后
 * 本形态全跳）。每个直连 bean 另自挂 {@code @ConditionalOnProperty(prefix = "feishu", name = "app-id")}
 * （本模块全 bean 统一的总闸），本类只补"显式选直连"半边——与
 * {@link OnFeishuBridgeForm} 构成对 feishu.direct 的严格互斥，两形态同 FQN 同路由（/web/feishu/**）
 * 永不同时注册。
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-11-08
 */
public class OnFeishuDirectForm extends AllNestedConditions {

    public OnFeishuDirectForm() {
        super(ConfigurationCondition.ConfigurationPhase.REGISTER_BEAN);
    }

    /**
     * 直连腿：feishu.direct=true 才命中
     */
    @ConditionalOnProperty(name = "feishu.direct", havingValue = "true")
    static class DirectMode {
    }
}
