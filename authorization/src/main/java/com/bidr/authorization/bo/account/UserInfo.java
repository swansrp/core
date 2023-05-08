package com.bidr.authorization.bo.account;

import com.bidr.authorization.bo.permit.PermitInfo;
import com.bidr.authorization.bo.role.RoleInfo;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: UserInfo
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/27 11:07
 */
@Data
public class UserInfo {
    @ApiModelProperty("用户编码")
    private String customerNumber;
    @ApiModelProperty("用户登录名")
    private String userName;
    @ApiModelProperty("姓名")
    private String name;
    @ApiModelProperty("手机号")
    private String phoneNumber;
    @ApiModelProperty("邮箱")
    private String email;
    @ApiModelProperty("角色列表")
    private Map<Long, RoleInfo> roleInfoMap = new HashMap<>(0);
    @ApiModelProperty("权限列表")
    private List<PermitInfo> permitInfoList;
    @ApiModelProperty("权限列表")
    private Map<Long, PermitInfo> permitPermsMap = new HashMap<>(0);
}
