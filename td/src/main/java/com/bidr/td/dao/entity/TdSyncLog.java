package com.bidr.td.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * TDengine 数据同步日志
 */
@Data
@TableName("td_sync_log")
public class TdSyncLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String bizId;
    private String stableName;
    private String subTableName;
    private String syncType;   // INSERT / UPDATE / DELETE
    private Integer syncStatus; // 0=待同步, 1=成功, 2=失败
    private String errorMsg;
    private Integer retryCount;
    private String createBy;
    private java.util.Date createAt;
    private java.util.Date updateAt;
}
