package com.bidr.platform.vo.params;

import com.baomidou.mybatisplus.annotation.TableField;
import com.bidr.platform.config.portal.PortalEntity;
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
 *
 * @author Sharp
 * @since 2023/06/07 14:01
 */
@Data
public class SysConfigRes {

    @ApiModelProperty(value = "参数主键")
    @NotNull(message = "参数主键不能为空")
    private Integer configId;

    @ApiModelProperty(value = "参数名称")
    @Size(max = 100, message = "参数名称最大长度要小于 100")
    private String configName;

    @ApiModelProperty(value = "参数键名")
    @Size(max = 100, message = "参数键名最大长度要小于 100")
    private String configKey;

    @ApiModelProperty(value = "参数键值")
    @Size(max = 500, message = "参数键值最大长度要小于 500")
    private String configValue;


    @ApiModelProperty(value = "系统内置")
    private String configType;


    @ApiModelProperty(value = "创建者")
    private Long createBy;


    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;


    @ApiModelProperty(value = "更新者")
    private Long updateBy;


    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /**
     * 备注
     */
    @TableField(value = "remark")
    @ApiModelProperty(value = "备注")
    @Size(max = 500, message = "备注最大长度要小于 500")
    private String remark;
}
