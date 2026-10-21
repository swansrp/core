package com.bidr.blob.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bidr.authorization.mybatis.anno.AccountContextFill;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * 内容块（跨空间去重，不带空间维度；M16 棒 1 自 inkhub-file 下沉 core/store/blob）
 *
 * @author sharp
 */
@ApiModel(description = "内容块（跨空间去重，不带空间维度）")
@Data
@AccountContextFill
@TableName(value = "sa_blob")
public class SaBlob {

    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "id")
    private Long id;

    @TableField(value = "sha256")
    @ApiModelProperty(value = "内容哈希")
    private String sha256;

    @TableField(value = "storage_key")
    @ApiModelProperty(value = "对象存储key（raw/{哈希路径}）")
    private String storageKey;

    @TableField(value = "file_size")
    @ApiModelProperty(value = "字节数")
    private Long fileSize;

    @TableField(value = "mime_type")
    @ApiModelProperty(value = "MIME类型")
    private String mimeType;

    @TableField(value = "ref_count")
    @ApiModelProperty(value = "引用计数（业务版本行数）")
    private Integer refCount;

    @TableField(value = "kind")
    @ApiModelProperty(value = "内容种类（D7 甲口径：original=原件锚/render=可交付件/image=裸图无引用源；存量派生品行不回填）")
    private String kind;

    @TableField(value = "storage_type")
    @ApiModelProperty(value = "存储后端快照（选型时点值：Minio/Ali/Volc/Local）")
    private String storageType;

    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    @TableField(value = "create_at", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建时间")
    private Date createAt;

    @TableField(value = "update_by", fill = FieldFill.INSERT_UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    @TableField(value = "update_at", fill = FieldFill.INSERT_UPDATE)
    @ApiModelProperty(value = "更新时间")
    private Date updateAt;

    @TableField(value = "`valid`")
    @ApiModelProperty(value = "有效性")
    private String valid;

}
