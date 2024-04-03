package com.bidr.wechat.po.platform.menu;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Title: DeleteConditionalMenuReq
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/6/17 16:46
 */
@Data
public class DeleteConditionalMenuReq {
    @JsonProperty("menuid")
    private String menuId;
}
