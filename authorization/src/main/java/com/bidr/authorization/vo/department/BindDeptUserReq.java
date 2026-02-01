package com.bidr.authorization.vo.department;

import lombok.Data;

import java.util.List;

/**
 * 部门用户绑定查询请求
 *
 * @author sharp
 */
@Data
public class BindDeptUserReq {
    /**
     * 部门ID
     */
    private String deptId;

    /**
     * 部门ID列表
     */
    private List<String> deptIdList;

    /**
     * 用户姓名
     */
    private String name;

    /**
     * 数据权限范围
     */
    private Integer dataScope;
}
