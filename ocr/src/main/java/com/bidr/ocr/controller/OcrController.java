package com.bidr.ocr.controller;

import ai.djl.modality.cv.output.DetectedObjects;
import com.bidr.ocr.constant.OcrType;
import com.bidr.ocr.service.OcrService;
import com.bidr.ocr.vo.DetectObjectRes;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Title: OcrController
 * Description: Copyright: Copyright (c) 2022 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2024/4/11 11:19
 */
@Api(tags = "系统基础 - OCR")
@RestController("OcrController")
@RequestMapping(value = "/web/ocr")
@RequiredArgsConstructor
public class OcrController {
    private final OcrService ocrService;

    @SneakyThrows
    @ApiOperation("ocr识别")
    @RequestMapping(value = "", method = RequestMethod.POST)
    public List<DetectObjectRes> ocr(@RequestPart MultipartFile image,
                                     OcrType type) {
        DetectedObjects result = ocrService.ocr(image.getInputStream(), type);
        return result.items().stream().map(DetectObjectRes::new).collect(Collectors.toList());
    }
}
