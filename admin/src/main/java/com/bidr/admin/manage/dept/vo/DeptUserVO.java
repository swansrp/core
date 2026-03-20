package com.bidr.admin.manage.dept.vo;


import com.alibaba.excel.annotation.ExcelProperty;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * @author Sharp
 * @since 2026/3/20 10:00
 */
@Data
public class DeptUserVO {
    @ExcelProperty("用户编码")
    @ApiModelProperty("用户编码")
    private String customerNumber;
    @ExcelProperty("用户名")
    @ApiModelProperty("用户名")
    private String userName;
    @ExcelProperty("部门名称")
    @ApiModelProperty("部门名称")
    private String deptName;
    @ExcelProperty("权限")
    @ApiModelProperty("权限")
    private String dataScope;
}
