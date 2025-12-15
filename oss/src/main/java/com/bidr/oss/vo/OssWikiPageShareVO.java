package com.bidr.oss.vo;

import com.bidr.authorization.dao.entity.AcUser;
import com.diboot.core.binding.annotation.BindField;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

/**
 * Wiki页面VO
 *
 * @author sharp
 * @since 2025-12-12
 */
@ApiModel(description = "Wiki页面分享码")
@Data
public class OssWikiPageShareVO {

    /**
     * 分享码
     */
    @ApiModelProperty(value = "分享码")
    private String shareCode;

    /**
     * 过期时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ApiModelProperty(value = "过期时间")
    private Date expiredTime;

}
