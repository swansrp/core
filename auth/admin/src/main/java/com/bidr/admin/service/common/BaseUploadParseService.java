package com.bidr.admin.service.common;

import com.bidr.admin.service.excel.progress.PortalExcelUploadProgressInf;
import com.bidr.authorization.service.token.TokenService;

import javax.annotation.Resource;
import java.io.File;
import java.util.Map;

public abstract class BaseUploadParseService implements PortalExcelUploadProgressInf {
    @Resource
    protected TokenService tokenService;

    public abstract Integer getTotal();

    public abstract void parseFile(File file, Map<String, Object> arg);

    @Override
    public TokenService getTokenService() {
        return tokenService;
    }


}
