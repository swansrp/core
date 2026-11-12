package com.bidr.oss.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * wiki 腿 JOIN 载体，只读映射 ac_user 两列，正源 AcUser 在 authorization(server)，本件使其不再必要。
 * 列映射对齐 AcUser（`name` 反引号 / customer_number），MPJLambdaWrapper 生成 SQL 与原引用等价（M-15）。
 * 仅用于查询 JOIN，不做写操作，故不挂 @AccountContextFill 与 fill 属性。
 *
 * @author sharp
 * @since 2026-11-13
 */
@ApiModel(description = "用户信息只读映射（wiki JOIN 载体）")
@Data
@TableName(value = "ac_user")
public class AcUserRef {

    @TableField(value = "customer_number")
    @ApiModelProperty(value = "用户编码")
    private String customerNumber;

    @TableField(value = "`name`")
    @ApiModelProperty(value = "用户姓名")
    private String name;
}
