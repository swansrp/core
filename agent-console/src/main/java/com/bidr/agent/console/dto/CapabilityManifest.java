package com.bidr.agent.console.dto;

import com.bidr.agent.console.capability.CapabilityPackageDescriptor;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Title: CapabilityManifest
 * Description: 能力包清单响应体——包级明细 + 扁平工具名全集（工具名升序，比对与快照才稳定）。
 *
 * @author Sharp
 * @since 2026-10-12
 */
@Data
public class CapabilityManifest {

    /** 包明细（按登记顺序） */
    private List<CapabilityPackageDescriptor> packages = new ArrayList<>();

    /** 全清单工具名并集（升序） */
    private Set<String> toolNames = new TreeSet<>();
}
