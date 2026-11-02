package com.bidr.identity.mirror.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * Title: IdmMirrorState
 * Description: 镜像同步状态表（T2.3d）——KV 形态的同步水位/状态锚点，目前唯一 key
 * {@code identity-snapshot}＝最近一次成功快照的 watermark（上游生成时刻 epoch millis）。
 * <p>下次同步前的对账面（上次拉到哪）与排障面（镜像是几点几分的数据）都读这里；
 * 失败趟不写——水位只标记<b>成功落库</b>的快照。</p>
 *
 * @author Sharp
 * @since 2026/11/03
 */
@ApiModel(description = "镜像同步状态表")
@Data
@TableName(value = "idm_mirror_state")
public class IdmMirrorState {

    /**
     * 自增主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "自增id")
    private Long id;

    /**
     * 状态键（唯一；identity-snapshot＝全量快照水位）
     */
    @TableField(value = "state_key")
    @ApiModelProperty(value = "状态键")
    private String stateKey;

    /**
     * 状态值（watermark 场景＝epoch millis 字符串）
     */
    @TableField(value = "state_value")
    @ApiModelProperty(value = "状态值")
    private String stateValue;

    /**
     * 更新时间（每次成功同步覆盖）
     */
    @TableField(value = "update_at")
    @ApiModelProperty(value = "更新时间")
    private Date updateAt;
}
