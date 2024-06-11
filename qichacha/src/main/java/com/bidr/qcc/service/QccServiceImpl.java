package com.bidr.qcc.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.kernel.exception.ServiceException;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.JsonUtil;
import com.bidr.kernel.utils.Md5Util;
import com.bidr.platform.service.rest.RestService;
import com.bidr.qcc.contants.QiChaChaUrl;
import com.bidr.qcc.dto.QccReq;
import com.bidr.qcc.dto.QccRes;
import com.bidr.qcc.dto.credit.CreditCodeReq;
import com.bidr.qcc.dto.credit.CreditCodeRes;
import com.bidr.qcc.dto.enterprise.EnterpriseAdvancedReq;
import com.bidr.qcc.dto.enterprise.EnterpriseAdvancedRes;
import com.bidr.qcc.dto.enterprise.EnterpriseReq;
import com.bidr.qcc.dto.enterprise.EnterpriseRes;
import com.bidr.qcc.dto.name.NameSearchReq;
import com.bidr.qcc.dto.name.NameSearchRes;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.impl.client.HttpClients;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.List;

/**
 * Title: QccServiceImpl
 * Description: Copyright: Copyright (c) 2022 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2024/4/18 14:02
 */
@Service
@RequiredArgsConstructor
public class QccServiceImpl implements QccService {
    private final RestService restService;
    @Value("${qichacha.app-key}")
    private String appKey;
    @Value("${qichacha.app-secret}")
    private String appSecret;

    @Value("${qichacha.proxy.enable}")
    private boolean proxyEnable;
    @Value("${qichacha.proxy.host}")
    private String proxyHost;
    @Value("${qichacha.proxy.port}")
    private Integer proxyPort;

    @Resource
    private RestTemplate restTemplate;

    protected <T> T get(String url, QccReq req, Class<T> clazz) {
        HttpHeaders header = buildHttpHeaders(req);
        QccRes<T> res = restService.get(getRestTemplate(), url, header, req, QccRes.class, clazz);
        handleError(res);
        return res.getResult();
    }

    @NotNull
    private HttpHeaders buildHttpHeaders(QccReq req) {
        String timeSpan = Long.toString(System.currentTimeMillis() / 1000);
        String token = getToken(timeSpan);
        req.setKey(appKey);
        HttpHeaders header = new HttpHeaders();
        header.add("Token", token);
        header.add("Timespan", timeSpan);
        return header;
    }

    protected <T> Page<T> getPage(String url, QccReq req, Class<T> clazz) {
        HttpHeaders header = buildHttpHeaders(req);
        QccRes<List<T>> res = restService.get(getRestTemplate(), url, header, req, QccRes.class, List.class);
        handleError(res);
        Page<T> result = new Page<>(res.getPaging().getPageIndex(), res.getPaging().getPageSize(),
                res.getPaging().getTotalRecords());
        if (FuncUtil.isNotEmpty(res.getResult())) {
            result.setRecords(JsonUtil.readJson(res.getResult(), List.class, clazz));
        }
        return result;
    }

    private void handleError(QccRes<?> res) {
        if (!StringUtils.equals(res.getStatus(), "200")) {
            throw new ServiceException(res.getMessage());
        }
    }

    private String getToken(String timeSpan) {
        return Md5Util.MD5(appKey + timeSpan + appSecret).toUpperCase();
    }

    @Override
    @Cacheable(condition = "#result != null", cacheNames = "QI-CHA-CHA#604800", keyGenerator = "cacheKeyByParam")
    public NameSearchRes enterpriseSearch(NameSearchReq req) {
        return get(QiChaChaUrl.NAME_SEARCH_URL + "?searchName=" + req.getSearchName(), new QccReq(),
                NameSearchRes.class);
    }

    @Override
    @Cacheable(condition = "#result != null", cacheNames = "QI-CHA-CHA#604800", keyGenerator = "cacheKeyByParam")
    public EnterpriseRes getEnterpriseInfo(EnterpriseReq req) {
        String searchKey = req.getSearchKey();
        req.setSearchKey(null);
        return get(QiChaChaUrl.ENTERPRISE_INFO_URL + "?searchKey=" + searchKey, req, EnterpriseRes.class);
    }

    @Override
    @Cacheable(condition = "#result != null", cacheNames = "QI-CHA-CHA#604800", keyGenerator = "cacheKeyByParam")
    public EnterpriseAdvancedRes getEnterpriseAdvancedInfo(EnterpriseAdvancedReq req) {
        return get(QiChaChaUrl.ENTERPRISE_ADVANCED_INFO_URL + "?keyword=" + req.getKeyword(), new QccReq(),
                EnterpriseAdvancedRes.class);
    }

    @Override
    @Cacheable(condition = "#result != null", cacheNames = "QI-CHA-CHA#604800", keyGenerator = "cacheKeyByParam")
    public CreditCodeRes getCreditCode(CreditCodeReq req) {
        return get(QiChaChaUrl.CREDIT_INFO_URL + "?keyWord=" + req.getKeyWord(), new QccReq(), CreditCodeRes.class);
    }

    public RestTemplate getRestTemplate() {
        RestTemplate restTemp = restService.getNoProxyRestTemplate();
        if (proxyEnable) {
            HttpComponentsClientHttpRequestFactory httpRequestFactory = new HttpComponentsClientHttpRequestFactory();
            HttpHost proxy = new HttpHost(proxyHost, proxyPort);
            httpRequestFactory.setHttpClient(HttpClients.custom().setProxy(proxy).build());
            restTemp.setRequestFactory(httpRequestFactory);
            return restTemp;
        }
        return restTemp;
    }
}
