package com.bidr.td.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * TDengine 子表与业务设备映射关系
 */
@Data
@TableName("td_tag_mapping")
public class TdTagMapping {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String subTableName;
    private String bizId;
    private String stableName;
    private String tagJson;
    private String description;
    private String valid;
    private String createBy;
    private java.util.Date createAt;
    private String updateBy;
    private java.util.Date updateAt;
}
