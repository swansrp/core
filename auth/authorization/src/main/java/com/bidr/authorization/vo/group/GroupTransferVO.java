package com.bidr.authorization.vo.group;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户组类型跨环境迁移（导出/导入共用）
 *
 * @author Sharp
 * @since 2026/10/12
 */
@ApiModel(description = "用户组类型迁移文件内容")
@Data
public class GroupTransferVO {

    @ApiModelProperty(value = "用户组类型id（varchar 主键，跨环境直接沿用）")
    @NotBlank(message = "用户组类型id不能为空")
    @Size(max = 50, message = "用户组类型id最大长度要小于 50")
    private String typeId;

    @ApiModelProperty(value = "用户组类型名称")
    @NotBlank(message = "用户组类型名称不能为空")
    @Size(max = 50, message = "用户组类型名称最大长度要小于 50")
    private String typeName;

    @ApiModelProperty(value = "用户组树，同级数组顺序即显示顺序")
    private List<Node> groups;

    /**
     * 用户组树节点（不含 id/pid，父子关系由嵌套结构表达）
     */
    @ApiModel(description = "用户组树节点")
    @Data
    public static class Node {

        @ApiModelProperty(value = "用户组名称")
        @NotBlank(message = "用户组名称不能为空")
        @Size(max = 50, message = "用户组名称最大长度要小于 50")
        private String name;

        @ApiModelProperty(value = "子用户组")
        private List<Node> children = new ArrayList<>();

        @ApiModelProperty(value = "该组绑定的用户（按工号携带，导入时翻译为本环境用户）")
        private List<UserBind> users = new ArrayList<>();
    }

    /**
     * 用户组绑定用户（跨环境用工号定位，user_id 各环境自增不可携带）
     */
    @ApiModel(description = "用户组绑定用户（工号携带）")
    @Data
    public static class UserBind {

        @ApiModelProperty(value = "工号（导入时的匹配键）")
        private String customerNumber;

        @ApiModelProperty(value = "用户名（仅备注，不参与匹配）")
        private String userName;

        @ApiModelProperty(value = "数据权限范围值")
        private Integer dataScope;

        @ApiModelProperty(value = "数据权限范围名称（值缺失/非法时按名称翻译）")
        private String dataScopeName;
    }
}
