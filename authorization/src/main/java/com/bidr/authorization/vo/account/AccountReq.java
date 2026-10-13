package com.bidr.authorization.vo.account;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * Title: AccountReq
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/23 15:14
 */
@Data
@ApiModel(description = "人事信息查询请求")
public class AccountReq {
    private List<String> deptIdList;
    private String name;

    @ApiModelProperty(value = "是否宽匹配：true＝姓名/登录名/昵称/手机号/邮箱/工号 任一模糊命中；"
            + "false 或不传＝保持原口径（按姓名/登录名匹配，多值逗号分隔时精确匹配）")
    private Boolean matchAllFields;
}
