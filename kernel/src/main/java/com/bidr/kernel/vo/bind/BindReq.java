package com.bidr.kernel.vo.bind;

import lombok.Data;

import java.util.List;

/**
 * Title: BindReq
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/05/06 10:47
 */
@Data
public class BindReq {
    private List<?> masterIds;
    private Object slaveId;
}
