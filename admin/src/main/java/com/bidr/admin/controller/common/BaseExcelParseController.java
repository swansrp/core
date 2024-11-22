package com.bidr.admin.controller.common;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.bidr.admin.service.common.BaseExcelParseService;
import com.bidr.platform.vo.upload.PortalUploadProgressRes;
import com.bidr.kernel.utils.HttpUtil;
import io.swagger.annotations.ApiOperation;
import lombok.SneakyThrows;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;

/**
 * Title: BaseExcelParseController
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2024/11/20 8:39
 */

public abstract class BaseExcelParseController<ENTITY, EXCEL> {

    protected abstract BaseExcelParseService<ENTITY, EXCEL> getExcelParseService();


    @ApiOperation(value = "上传解析文件")
    @RequestMapping(value = "/upload", method = RequestMethod.POST)
    public void parseExcel(MultipartFile file, HttpServletRequest request) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String[] filename = originalFilename.split("\\.");
        File tempFile = File.createTempFile(filename[0], "." + filename[1]);
        file.transferTo(tempFile);
        getExcelParseService().parseFile(tempFile, HttpUtil.getParamMap(request));
        tempFile.deleteOnExit();
    }

    @ApiOperation("获取导入新增进度")
    @RequestMapping(value = "/upload/progress", method = RequestMethod.GET)
    public PortalUploadProgressRes parseExcelProgress() {
        return getExcelParseService().getUploadProgress();
    }


    @SneakyThrows
    @ApiOperation("模版导出")
    @RequestMapping(value = "/template/export", method = RequestMethod.GET)
    public void templateExport(HttpServletRequest request, HttpServletResponse response) {
        String contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        String fileName = StrUtil.format("数据导出-{}", DateUtil.date().toString("yyyyMMddHHmmss"));
        byte[] exportBytes;
        exportBytes = getExcelParseService().templateExport();
        fileName = fileName + ".xlsx";
        HttpUtil.export(request, response, contentType, "UTF-8", fileName, exportBytes);
    }
}
