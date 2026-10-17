package com.bidr.wechat.bo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OpenIdBO {
    private String openId;
    private String sessionKey;
}
