package com.bidr.oss.vo;

import com.baomidou.mybatisplus.annotation.TableField;
import com.diboot.core.binding.annotation.BindDict;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.Date;

/**
 * Title: OssRes
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/02/24 22:57
 */
@Data
public class OssRes {

    @ApiModelProperty(value = "id")

    private Long id;


    @ApiModelProperty(value = "文件名")

    private String name;


    @ApiModelProperty(value = "地址")
    private String uri;

    @ApiModelProperty(value = "文件大小")
    private Long size;

    //@BindDict(type = "OSS_TYPE_DICT")
    @ApiModelProperty(value = "文件存储类型OSS_TYPE_DICT")
    private String type;


    @ApiModelProperty(value = "创建时间")
    private Date createAt;

    /**
     * 更新时间
     */
    @TableField(value = "update_at")
    @ApiModelProperty(value = "更新时间")
    @NotNull(message = "更新时间不能为null")
    private Date updateAt;

}
