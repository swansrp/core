package com.bidr.kernel.vo.bind;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * Title: BindReq
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/05/06 10:47
 */
@Data
public class BindReq {
    private Object attachId;
    @NotNull(message = "未提供实体id")
    private Object entityId;
}
