package com.bidr.platform.branding;

/**
 * Title: BrandingProvider
 * Description: 品牌 SPI——登录页与产品名的品牌元素供给契约。
 * 语义来自 view 侧登录页的硬编码品牌元素（产品名 / logo / 主题色实锤；
 * 副标题为契约新增项，见 {@link #loginSubtitle()}），
 * 接入方（pack-bidr / pack-plsintec）按公司品牌各出一份实现，内核读取后下发给前端渲染；
 * 全部取值允许为 null（内核逐字段回落默认品牌）。
 * Copyright: Copyright (c) 2025 Company: bidr
 *
 * @author Sharp
 * @since 2026-10-31
 */
public interface BrandingProvider {

    /**
     * 产品名（登录页标题 / 浏览器标题），如「墨枢」。
     */
    String productName();

    /**
     * logo 图片地址（登录页左上角，支持相对路径或完整 URL）。
     */
    String logoUrl();

    /**
     * 主题主色（CSS 色值，如 #2F54EB），用于登录页主按钮与强调色。
     */
    String themePrimary();

    /**
     * 登录页副标题文案（产品名下方的一句说明），允许返回 null（前端不渲染副标题）。
     */
    String loginSubtitle();
}
