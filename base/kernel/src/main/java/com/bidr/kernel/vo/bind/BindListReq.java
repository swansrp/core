package com.bidr.kernel.vo.bind;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Title: BindListReq
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/11 10:55
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BindListReq extends BindBaseReq {
    private List<Object> attachIdList;
}
