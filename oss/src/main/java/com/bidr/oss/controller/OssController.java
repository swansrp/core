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


}
