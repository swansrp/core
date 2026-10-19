package com.bidr.platform.vo.params;

import com.bidr.kernel.config.anno.portal.PortalDisableField;
import com.bidr.kernel.config.anno.portal.PortalIdField;
import com.bidr.kernel.config.anno.portal.PortalNameField;
import com.bidr.kernel.config.anno.portal.PortalTextAreaField;
import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Date;

/**
 * Title: SysConfigRes
 * Description: Copyright: Copyright (c) 2023
 * <p>
 * 🔴 字段声明顺序＋Portal 注解是参数配置页列配置的正源（同 UserRes），勿随手重排。
 *
 * @author Sharp
 * @since 2023-06-07 14:01
 */
@Data
public class SysConfigRes {

    @PortalIdField
    @PortalDisableField
    @ApiModelProperty(value = "参数主键")
    @NotNull(message = "参数主键不能为空")
    private Integer configId;

    @PortalNameField
    @ApiModelProperty(value = "参数名称")
    @Size(max = 100, message = "参数名称最大长度要小于 100")
    private String configName;

    @ApiModelProperty(value = "参数键名")
    @Size(max = 100, message = "参数键名最大长度要小于 100")
    private String configKey;

    @ApiModelProperty(value = "参数键值")
    @Size(max = 500, message = "参数键值最大长度要小于 500")
    private String configValue;

    @ApiModelProperty(value = "参数分组")
    @Size(max = 100, message = "参数分组最大长度要小于 100")
    private String configGroup;

    @ApiModelProperty(value = "系统内置")
    private String configType;

    /**
     * 备注
     */
    @TableField(value = "remark")
    @PortalTextAreaField
    @ApiModelProperty(value = "备注")
    @Size(max = 500, message = "备注最大长度要小于 500")
    private String remark;

    @PortalDisableField
    @ApiModelProperty(value = "创建者")
    private Long createBy;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    @PortalDisableField
    @ApiModelProperty(value = "更新者")
    private Long updateBy;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;
}
