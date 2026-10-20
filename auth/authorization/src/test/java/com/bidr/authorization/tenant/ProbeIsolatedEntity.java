package com.bidr.authorization.tenant;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * Title: ProbeIsolatedEntity
 * Description: 租户行级隔离测试探针实体——实现 {@link TenantIsolated}，
 * 供 {@link TenantLineIsolationTest} 静态注册进 TableInfo 缓存构建隔离集。
 * 表名用 zzz_ 前缀防撞业务表。Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-10-19
 */
@TableName("zzz_probe_isolated")
public class ProbeIsolatedEntity implements TenantIsolated {

    @TableId
    private Long id;

    /** {@code fill} 声明是写侧补位的前提，缺它 {@link TenantLineIsolationConfiguration} 守卫即抛 */
    @TableField(value = "tenant_id", fill = FieldFill.INSERT)
    private Long tenantId;

    private String name;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
