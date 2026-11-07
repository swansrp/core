package com.bidr.authorization.vo.subsystem;

import com.bidr.authorization.dao.entity.AcSubsystem;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Title: QuerySubsystemRes
 * Description: 应用管理 列表行（照 QueryPartnerRes 形态：实体平铺＋join 出的补充列）。
 *
 * @author Sharp
 * @since 2026-11-06
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QuerySubsystemRes extends AcSubsystem {

    @ApiModelProperty(value = "归属partner的appKey（经 partner_id 两步查出，展示用）")
    private String partnerAppKey;
}
