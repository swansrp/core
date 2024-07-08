package com.bidr.admin.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Data;

/**
 * 后台管理表
 */
@ApiModel(description = "后台管理表")
@Data
@TableName(value = "erp.sys_portal")
public class SysPortal {
    /**
     * id
     */
    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "id")
    @NotNull(message = "id不能为null")
    private Long id;

    /**
     * 对应角色id
     */
    @TableField(value = "role_id")
    @ApiModelProperty(value = "对应角色id")
    @NotNull(message = "对应角色id不能为null")
    private Long roleId;

    /**
     * 英文名
     */
    @TableField(value = "`name`")
    @ApiModelProperty(value = "英文名")
    @Size(max = 50, message = "英文名最大长度要小于 50")
    @NotBlank(message = "英文名不能为空")
    private String name;

    /**
     * 中文名
     */
    @TableField(value = "display_name")
    @ApiModelProperty(value = "中文名")
    @Size(max = 50, message = "中文名最大长度要小于 50")
    @NotBlank(message = "中文名不能为空")
    private String displayName;

    /**
     * api地址
     */
    @TableField(value = "url")
    @ApiModelProperty(value = "api地址")
    @Size(max = 50, message = "api地址最大长度要小于 50")
    @NotBlank(message = "api地址不能为空")
    private String url;

    /**
     * 接口bean
     */
    @TableField(value = "bean")
    @ApiModelProperty(value = "接口bean")
    @Size(max = 200, message = "接口bean最大长度要小于 200")
    @NotBlank(message = "接口bean不能为空")
    private String bean;

    /**
     * 表格大小PORTAL_TABLE_SIZE_DICT
     */
    @TableField(value = "`size`")
    @ApiModelProperty(value = "表格大小PORTAL_TABLE_SIZE_DICT")
    @Size(max = 50, message = "表格大小PORTAL_TABLE_SIZE_DICT最大长度要小于 50")
    @NotBlank(message = "表格大小PORTAL_TABLE_SIZE_DICT不能为空")
    private String size;

    /**
     * 只读
     */
    @TableField(value = "read_only")
    @ApiModelProperty(value = "只读")
    @Size(max = 1, message = "只读最大长度要小于 1")
    @NotBlank(message = "只读不能为空")
    private String readOnly;

    /**
     * 总结栏
     */
    @TableField(value = "summary")
    @ApiModelProperty(value = "总结栏")
    @Size(max = 1, message = "总结栏最大长度要小于 1")
    @NotBlank(message = "总结栏不能为空")
    private String summary;

    /**
     * 行id字段名
     */
    @TableField(value = "id_column")
    @ApiModelProperty(value = "行id字段名")
    @Size(max = 50, message = "行id字段名最大长度要小于 50")
    private String idColumn;

    /**
     * 父id字段名
     */
    @TableField(value = "pid_column")
    @ApiModelProperty(value = "父id字段名")
    @Size(max = 50, message = "父id字段名最大长度要小于 50")
    private String pidColumn;

    /**
     * 树形结构下是否支持拖拽修改
     */
    @TableField(value = "tree_drag")
    @ApiModelProperty(value = "树形结构下是否支持拖拽修改")
    @Size(max = 1, message = "树形结构下是否支持拖拽修改最大长度要小于 1")
    @NotBlank(message = "树形结构下是否支持拖拽修改不能为空")
    private String treeDrag;

    /**
     * 名称字段名
     */
    @TableField(value = "name_column")
    @ApiModelProperty(value = "名称字段名")
    @Size(max = 50, message = "名称字段名最大长度要小于 50")
    private String nameColumn;

    /**
     * 排序字段名
     */
    @TableField(value = "order_column")
    @ApiModelProperty(value = "排序字段名")
    @Size(max = 50, message = "排序字段名最大长度要小于 50")
    private String orderColumn;

    /**
     * 表格拖拽改变顺序
     */
    @TableField(value = "table_drag")
    @ApiModelProperty(value = "表格拖拽改变顺序")
    @Size(max = 1, message = "表格拖拽改变顺序最大长度要小于 1")
    @NotBlank(message = "表格拖拽改变顺序不能为空")
    private String tableDrag;

    /**
     * 新增弹框宽度
     */
    @TableField(value = "add_width")
    @ApiModelProperty(value = "新增弹框宽度")
    @NotNull(message = "新增弹框宽度不能为null")
    private Integer addWidth;

    /**
     * 编辑弹框宽度
     */
    @TableField(value = "edit_width")
    @ApiModelProperty(value = "编辑弹框宽度")
    @NotNull(message = "编辑弹框宽度不能为null")
    private Integer editWidth;

    /**
     * 详情弹框宽度
     */
    @TableField(value = "detail_width")
    @ApiModelProperty(value = "详情弹框宽度")
    @NotNull(message = "详情弹框宽度不能为null")
    private Integer detailWidth;

    /**
     * 弹框每行显示个数
     */
    @TableField(value = "description_count")
    @ApiModelProperty(value = "弹框每行显示个数")
    @NotNull(message = "弹框每行显示个数不能为null")
    private Integer descriptionCount;

    /**
     * 支持导出
     */
    @TableField(value = "export_able")
    @ApiModelProperty(value = "支持导出")
    @Size(max = 1, message = "支持导出最大长度要小于 1")
    @NotBlank(message = "支持导出不能为空")
    private String exportAble;

    /**
     * 支持导入
     */
    @TableField(value = "import_able")
    @ApiModelProperty(value = "支持导入")
    @Size(max = 1, message = "支持导入最大长度要小于 1")
    @NotBlank(message = "支持导入不能为空")
    private String importAble;

    /**
     * 默认搜索字段
     */
    @TableField(value = "default_condition")
    @ApiModelProperty(value = "默认搜索字段")
    private String defaultCondition;

    /**
     * 默认排序字段
     */
    @TableField(value = "default_sort")
    @ApiModelProperty(value = "默认排序字段")
    private String defaultSort;
}