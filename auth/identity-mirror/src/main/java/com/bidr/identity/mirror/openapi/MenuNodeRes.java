package com.bidr.identity.mirror.openapi;

import lombok.Data;

import java.util.List;

/**
 * Title: MenuNodeRes
 * Description: 子系统菜单树<b>消费侧自有 DTO</b>（方案B 棒4 §2「core 菜单客户端」行）——
 * 线格式与墨枢 provider 侧 {@code MenuTreeRes}（vo.menu 包，@JsonIgnore 剔除管理字段后的线上面）
 * 对齐、<b>字段名一致</b>；本模块禁依赖 authorization(server)，类型面自持（镜像纪律：拿数据
 * 不拿 provider 类型）。
 *
 * <p>消费侧<b>最小字段集</b>（前端路由聚合所需九件）：menuType/path/component/title/icon/
 * isFrame/isCache/key/children。线上多出的字段（menuId/pid/grandId/ancestors/clientType/
 * query/appCode 等）由 {@code OpenApiDataClient} 的宽容 Jackson
 * （FAIL_ON_UNKNOWN_PROPERTIES=false）静默忽略——provider 加字段不炸消费侧，消费侧也只
 * 认自己声明过的面。类型对齐墨枢线上面：menuType=Integer、key=Long、isFrame/isCache=String
 * （"0"/"1" 字典值）、其余 String。</p>
 *
 * @author coder (棒4, 方案B 开放口三层沉淀 2026-11-07)
 * @since 2026/11/07
 */
@Data
public class MenuNodeRes {

    /** 菜单类型（字典：目录/菜单/按钮等，线上 Integer） */
    private Integer menuType;

    /** 路由地址 */
    private String path;

    /** 组件路径 */
    private String component;

    /** 菜单名称 */
    private String title;

    /** 菜单图标 */
    private String icon;

    /** 是否为外链（"0"是 "1"否，线上 String 字典值） */
    private String isFrame;

    /** 是否缓存（"0"缓存 "1"不缓存，线上 String 字典值） */
    private String isCache;

    /** 与菜单ID一致的 key（前端路由聚合用，线上 Long） */
    private Long key;

    /** 子节点（递归同形） */
    private List<MenuNodeRes> children;
}
