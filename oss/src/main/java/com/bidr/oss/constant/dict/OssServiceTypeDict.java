package com.bidr.oss.constant.dict;

import com.bidr.oss.service.BaseOssService;
import com.bidr.oss.service.impl.OssAliServiceImpl;
import com.bidr.oss.service.impl.OssLocalServiceImpl;
import com.bidr.oss.service.impl.OssMinioServiceImpl;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

/**
 * Title: OssServiceTypeDict
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/02/26 09:06
 */
@Getter
@RequiredArgsConstructor
public enum OssServiceTypeDict {
    /**
     *
     */
    LOCAL("0", "本机", OssServiceTypeDictInjector.ossLocalService),
    MINIO("1", "MINIO", OssServiceTypeDictInjector.ossMinioService),
    ALI("2", "阿里云", OssServiceTypeDictInjector.ossAliService);

    private final String value;
    private final String label;
    private final BaseOssService service;

    @Component
    private static class OssServiceTypeDictInjector {
        private static OssLocalServiceImpl ossLocalService;
        private static OssMinioServiceImpl ossMinioService;
        private static OssAliServiceImpl ossAliService;
        @Resource
        private OssLocalServiceImpl ossLocalServiceImpl;
        @Resource
        private OssMinioServiceImpl ossMinioServiceImpl;
        @Resource
        private OssAliServiceImpl ossAliServiceImpl;

        @PostConstruct
        private void postConstruct() {
            OssServiceTypeDictInjector.ossLocalService = ossLocalServiceImpl;
            OssServiceTypeDictInjector.ossMinioService = ossMinioServiceImpl;
            OssServiceTypeDictInjector.ossAliService = ossAliServiceImpl;
        }
    }
}
