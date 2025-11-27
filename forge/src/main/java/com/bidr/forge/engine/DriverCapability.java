package com.bidr.forge.engine;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 驱动能力声明
 *
 * @author Sharp
 * @since 2025-11-24
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DriverCapability {
    /**
     * 是否支持读取
     */
    private Boolean readable = true;

    /**
     * 是否支持写入（增删改）
     */
    private Boolean writable = false;

    /**
     * 是否支持树结构
     */
    private Boolean treeSupport = false;

    public static DriverCapability readOnly() {
        return new DriverCapability(true, false, false);
    }

    public static DriverCapability readWrite() {
        return new DriverCapability(true, true, false);
    }

    public static DriverCapability fullSupport() {
        return new DriverCapability(true, true, true);
    }
}
