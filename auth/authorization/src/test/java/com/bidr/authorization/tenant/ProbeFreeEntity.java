package com.bidr.authorization.tenant;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * Title: ProbeFreeEntity
 * Description: 不实现 {@link TenantIsolated} 的对照探针实体（不进隔离集）。
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-10-19
 */
@TableName("zzz_probe_free")
public class ProbeFreeEntity {

    @TableId
    private Long id;

    private String name;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
