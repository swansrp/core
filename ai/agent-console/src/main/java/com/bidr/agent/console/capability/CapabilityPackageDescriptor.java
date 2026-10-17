package com.bidr.agent.console.capability;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: CapabilityPackageDescriptor
 * Description: 能力包清单条目（{@link AgentCapabilityPackageRegistry} 聚合出的只读视图）——
 * 形态与 {@code AgentDescriptor} 一致：POJO + meta，供清单端点直接序列化与管理页渲染。
 *
 * @author Sharp
 * @since 2026-10-12
 */
@Data
public class CapabilityPackageDescriptor {

    /** 分类名（勾选与装配单里存的就是它） */
    private String name;

    private String displayName;

    /** 来源模块标识 */
    private String module;

    private String description;

    /** 副作用档（枚举名，字典序稳定） */
    private List<String> tags = new ArrayList<>();

    /** 包内工具注册名 */
    private List<String> tools = new ArrayList<>();
}
