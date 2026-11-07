package com.bidr.authorization.vo.tenant;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Title: TenantUserLookupVO
 * Description: 租户加成员前按人精确查的回显（照 SpaceAdmin {@code EmployeeLookupVO} 最小面家规：
 * <b>类型即闸</b>——只有核对所需四字段，password/部门/邮箱/状态等一概没有；
 * 查人端点的发现面为零：说得出准确的工号/手机号/姓名才能确认到这一个人）。
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-11-07
 */
@ApiModel(description = "租户加成员按工号/手机号/姓名精确查人回显（最小面：id＋工号＋姓名＋手机号）")
@Data
public class TenantUserLookupVO {

    @ApiModelProperty(value = "用户id（核对后确认添加即以它调 /my/member/add，add 契约零变化）")
    private Long userId;

    @ApiModelProperty(value = "工号")
    private String customerNumber;

    @ApiModelProperty(value = "姓名")
    private String name;

    @ApiModelProperty(value = "手机号")
    private String phoneNumber;
}
