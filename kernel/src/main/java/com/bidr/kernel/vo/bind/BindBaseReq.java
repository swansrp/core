package com.bidr.kernel.vo.bind;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * Title: BindBaseReq
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/02/27 15:56
 */
@Data
public class BindBaseReq {
    @NotNull(message = "未提供实体id")
    private Object entityId;
}
