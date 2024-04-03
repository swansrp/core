package com.bidr.platform.utils.file;

import lombok.Data;

/**
 * Title: SftpConfig
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/8/17 10:43
 */
@Data
public class SftpConfig {
    private String hostname;
    private Integer port;
    private String username;
    private String password;
    private Integer timeout;
    private String remoteRootPath;
    private String fileSuffix;
}
