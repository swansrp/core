package com.bidr.kernel.vo.bind;

import lombok.Data;

import java.util.List;

/**
 * Title: BindListReq
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/11 10:55
 */
@Data
public class BindListReq {
    private List<Object> attachIdList;
    private Object entityId;
}
