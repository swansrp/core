package com.bidr.admin.controller.common;

import com.bidr.admin.service.common.BaseUploadParseService;
import com.bidr.platform.vo.upload.PortalUploadProgressRes;
import com.bidr.kernel.utils.HttpUtil;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;

/**
 * Title: BaseExcelParseController
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2024/11/20 8:39
 */

public abstract class BaseUploadParseController {

    protected abstract BaseUploadParseService getUploadParseService();


    @ApiOperation(value = "上传解析文件")
    @RequestMapping(value = "/upload", method = RequestMethod.POST)
    public void parseExcel(MultipartFile file, HttpServletRequest request) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String[] filename = originalFilename.split("\\.");
        File tempFile = File.createTempFile(filename[0], "." + filename[1]);
        file.transferTo(tempFile);
        getUploadParseService().startUploadProgress(getUploadParseService().getTotal());
        getUploadParseService().parseFile(tempFile, HttpUtil.getParamMap(request));
        tempFile.deleteOnExit();
    }

    @ApiOperation("获取导入新增进度")
    @RequestMapping(value = "/upload/progress", method = RequestMethod.GET)
    public PortalUploadProgressRes parseExcelProgress() {
        return getUploadParseService().getUploadProgress();
    }

}
