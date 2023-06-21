package com.bidr.kernel.mybatis.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.Date;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Data;

 /**
 * Title: SaSequence
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/06/01 16:40
 */
/**
    * 队列表
    */
@ApiModel(description="队列表")
@Data
@TableName(value = "sa_sequence")
public class SaSequence {
    /**
     * 序列名称
     */
    @TableId(value = "seq_name", type = IdType.AUTO)
    @ApiModelProperty(value="序列名称")
    @Size(max = 128,message = "序列名称最大长度要小于 128")
    @NotBlank(message = "序列名称不能为空")
    private String seqName;

    /**
     * 所属平台
     */
    @TableField(value = "platform")
    @ApiModelProperty(value="所属平台")
    @Size(max = 50,message = "所属平台最大长度要小于 50")
    @NotBlank(message = "所属平台不能为空")
    private String platform;

    /**
     * 目前序列值
     */
    @TableField(value = "`value`")
    @ApiModelProperty(value="目前序列值")
    private Integer value;

    /**
     * 序列前缀
     */
    @TableField(value = "`prefix`")
    @ApiModelProperty(value="序列前缀")
    @Size(max = 10,message = "序列前缀最大长度要小于 10")
    @NotBlank(message = "序列前缀不能为空")
    private String prefix;

    /**
     * 序列后缀
     */
    @TableField(value = "suffix")
    @ApiModelProperty(value="序列后缀")
    @Size(max = 10,message = "序列后缀最大长度要小于 10")
    @NotBlank(message = "序列后缀不能为空")
    private String suffix;

    /**
     * 最小值
     */
    @TableField(value = "min_value")
    @ApiModelProperty(value="最小值")
    @NotNull(message = "最小值不能为null")
    private Integer minValue;

    /**
     * 最大值
     */
    @TableField(value = "max_value")
    @ApiModelProperty(value="最大值")
    @NotNull(message = "最大值不能为null")
    private Integer maxValue;

    /**
     * 每次取值的数量
     */
    @TableField(value = "step")
    @ApiModelProperty(value="每次取值的数量")
    @NotNull(message = "每次取值的数量不能为null")
    private Integer step;

    /**
     * 创建时间
     */
    @TableField(value = "create_at")
    @ApiModelProperty(value="创建时间")
    @NotNull(message = "创建时间不能为null")
    private Date createAt;

    /**
     * 创建人
     */
    @TableField(value = "create_by")
    @ApiModelProperty(value="创建人")
    @Size(max = 50,message = "创建人最大长度要小于 50")
    private String createBy;

    /**
     * 修改时间
     */
    @TableField(value = "update_at")
    @ApiModelProperty(value="修改时间")
    @NotNull(message = "修改时间不能为null")
    private Date updateAt;

    /**
     * 修改人
     */
    @TableField(value = "update_by")
    @ApiModelProperty(value="修改人")
    @Size(max = 50,message = "修改人最大长度要小于 50")
    private String updateBy;

    public static final String COL_SEQ_NAME = "seq_name";

    public static final String COL_PLATFORM = "platform";

    public static final String COL_VALUE = "value";

    public static final String COL_PREFIX = "prefix";

    public static final String COL_SUFFIX = "suffix";

    public static final String COL_MIN_VALUE = "min_value";

    public static final String COL_MAX_VALUE = "max_value";

    public static final String COL_STEP = "step";

    public static final String COL_CREATE_AT = "create_at";

    public static final String COL_CREATE_BY = "create_by";

    public static final String COL_UPDATE_AT = "update_at";

    public static final String COL_UPDATE_BY = "update_by";
}
