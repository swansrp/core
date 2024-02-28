package com.bidr.kernel.vo.bind;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Title: BindReq
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/05/06 10:47
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BindReq extends BindBaseReq {
    private Object attachId;
}
