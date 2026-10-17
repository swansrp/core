package com.bidr.agent.runtime.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 平台统一响应信封：{code, msg, data}，code = 0 成功。
 *
 * @author sharp
 * @since 2026/9/22
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Envelope<T> {

    private Integer code;

    private String msg;

    private T data;

    public boolean isSuccess() {
        return code != null && code == 0;
    }
}
