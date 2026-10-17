package com.bidr.platform.controller;

import com.bidr.kernel.utils.HttpUtil;
import com.bidr.platform.utils.QrCodeUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;

/**
 * @author sharp
 * @since 2024-11-20 23:00
 */
@Slf4j
@Api(tags = "系统基础 - 二维码")
@RequiredArgsConstructor
@RestController
@RequestMapping(value = "/web/qrCode")
public class QrCodeController {

    @ApiOperation(value = "识别二维码")
    @RequestMapping(value = "/parse", method = RequestMethod.POST)
    public String parseQrCode(@RequestParam("file") @RequestPart MultipartFile file) throws IOException {
        File tempFile = HttpUtil.getTempFile(file);
        String result = QrCodeUtil.parseQrCode(tempFile);
        tempFile.deleteOnExit();
        return result;
    }

    @ApiOperation(value = "生成二维码")
    @RequestMapping(value = "/generate", method = RequestMethod.GET)
    public void generateQrCode(String content, HttpServletResponse response) throws Exception {
        ServletOutputStream stream = response.getOutputStream();
        response.setContentType("image/png");
        QrCodeUtil.generateQrCode(content, stream, null);
        stream.flush();
        stream.close();
    }

}