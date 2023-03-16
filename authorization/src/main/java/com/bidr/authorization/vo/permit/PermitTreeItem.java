package com.bidr.authorization.vo.permit;

import com.bidr.authorization.dao.entity.AcPermit;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * Title: PermitTreeItem
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/03/13 15:06
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PermitTreeItem extends AcPermit {

    @JsonIgnore
    private Integer showOrder;

    @JsonIgnore
    private String show;

    @JsonIgnore
    private Date createAt;

    @JsonIgnore
    private Date updateAt;

    @JsonIgnore
    private String valid;

}
