package com.bidr.oss.controller;

import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.controller.BaseAdminController;
import com.bidr.kernel.utils.DictEnumUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.oss.constant.dict.OssServiceTypeDict;
import com.bidr.oss.constant.param.OssParam;
import com.bidr.oss.dao.entity.SaObjectStorage;
import com.bidr.oss.vo.OssRes;
import com.bidr.oss.vo.UploadRes;
import com.bidr.platform.config.portal.AdminPortal;
import com.bidr.platform.service.cache.SysConfigCacheService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;

/**
 * Title: OssController
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/02/24 22:55
 */
@Api(tags = "系统管理 - 对象存储管理")
@AdminPortal
@RestController
@RequiredArgsConstructor
@RequestMapping(path = {"/web/oss"})
public class OssController extends BaseAdminController<SaObjectStorage, OssRes> {

    private final SysConfigCacheService sysConfigCacheService;
    private final HttpServletRequest request;

    @RequestMapping(value = "", method = {RequestMethod.PUT, RequestMethod.POST})
    public UploadRes upload(@RequestPart("file") MultipartFile file, @RequestParam(value = "folder", required = false) String folder,
                            @RequestParam(value = "type", required = false) String type,
                            @RequestParam(value = "fileName", required = false) String fileName) {
        String ossServiceType = sysConfigCacheService.getParamValueAvail(OssParam.OSS_SERVER_TYPE);
        OssServiceTypeDict service = DictEnumUtil.getEnumByValue(ossServiceType, OssServiceTypeDict.class);
        Validator.assertNotNull(service, ErrCodeSys.SYS_CONFIG_NOT_EXIST, "对象存储类型");
        return service.getService().upload(request, file, folder, type, fileName);
    }

    /**
     * 获取在线预览服务地址（kkFileView）：系统参数 OSS_PREVIEW_URL 下发前端，
     * 不同客户部署地址不同；参数默认值/关闭态为 "0"，接口统一归一为空串下发，
     * 前端仅需约定“空串 = 未开启预览入口”即可隐藏预览操作。
     * 注意必须用 getSysConfigValue：getParamValueAvail 在参数值为空时合抛“数据不存在”，
     * 而空值正是“未开启预览”的合法状态
     */
    @ApiOperation("获取在线预览服务地址（空串表示未开启预览入口）")
    @GetMapping("/preview-url")
    public String previewUrl() {
        String url = sysConfigCacheService.getSysConfigValue(OssParam.OSS_PREVIEW_URL);
        return url == null || "0".equals(url) || url.isEmpty() ? "" : url;
    }


}
