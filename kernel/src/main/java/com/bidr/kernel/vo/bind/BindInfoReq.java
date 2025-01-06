package com.bidr.kernel.vo.bind;

import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotNull;

/**
 * Title: BindReq
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/05/06 10:47
 */
@Data
public class BindInfoReq {
    @NotNull(message = "未提供绑定实体")
    private Object attachId;
    private Object data;
}
