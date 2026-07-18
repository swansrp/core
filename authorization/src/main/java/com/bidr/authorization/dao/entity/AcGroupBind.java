package com.bidr.authorization.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.github.jeffreyning.mybatisplus.anno.MppMultiId;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 用户组通用绑定关系表
 * <p>
 * 通过 bind_type 区分不同的绑定场景（如 hr_dept、area、lead_dept），
 * attach_value 存储被绑定的目标值（字典项 value 或业务 id），
 * extra_data 以 JSON 字符串存储绑定属性（如 {"readOnly":"1"}）。
 * <p>
 * 新功能无需建专用表/接口，前端配置 bindType 即可走通用接口。
 *
 * @author sharp
 * @since 2026/07/18
 */
@ApiModel(description = "用户组通用绑定关系表")
@Data
@TableName(value = "ac_group_bind")
public class AcGroupBind {
    /**
     * 组id
     */
    @MppMultiId
    @TableField(value = "group_id")
    @ApiModelProperty(value = "组id")
    private Long groupId;

    /**
     * 绑定类型（前端约定，如 hr_dept/area/lead_dept）
     */
    @MppMultiId
    @TableField(value = "bind_type")
    @ApiModelProperty(value = "绑定类型")
    private String bindType;

    /**
     * 绑定目标值（字典项value或业务id）
     */
    @MppMultiId
    @TableField(value = "attach_value")
    @ApiModelProperty(value = "绑定目标值")
    private String attachValue;

    /**
     * 绑定属性JSON（如 {"readOnly":"1"}）
     */
    @TableField(value = "extra_data")
    @ApiModelProperty(value = "绑定属性JSON")
    private String extraData;
}
