package com.bidr.authorization.vo.department;

import com.bidr.kernel.vo.bind.BindListReq;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 部门用户批量绑定数据权限请求
 *
 * @author sharp
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BindDeptUserListDataScopeReq extends BindListReq {
    /**
     * 数据权限范围
     */
    private Integer dataScope;
}
