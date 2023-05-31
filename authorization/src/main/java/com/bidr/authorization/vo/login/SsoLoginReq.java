package com.bidr.authorization.vo.login;

import com.bidr.authorization.dto.openapi.SignDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Title: SsoLoginReq
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2021/7/14 14:22
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SsoLoginReq extends SignDTO {
    private String appId;
    private String loginId;
    private String userName;
}
