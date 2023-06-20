package com.bidr.sms.service.message.ali;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.*;
import com.aliyun.tea.TeaException;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.exception.ServiceException;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.platform.service.cache.SysConfigCacheService;
import com.bidr.sms.constant.dict.AliMessageTemplateConfirmStatusDict;
import com.bidr.sms.constant.err.SmsErrorCode;
import com.bidr.sms.constant.param.SmsParam;
import com.bidr.sms.dao.entity.SaSmsTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Title: AliSmsManageService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/28 14:15
 */
@Service
@RequiredArgsConstructor
public class AliSmsManageService {

    private final static String ALI_SUCCESS = "OK";
    private final SysConfigCacheService sysConfigCacheService;
    @Value("${ali-sms.endpoint}")
    private String endpoint;
    @Value("${ali-sms.access-key-id}")
    private String accessKeyId;
    @Value("${ali-sms.access-key-secret}")
    private String accessKeySecret;

    public AddSmsTemplateResponseBody applySmsTemplate(SaSmsTemplate saSmsTemplate) {
        try {
            Client client = createClient();
            AddSmsTemplateRequest addSmsTemplateRequest = buildAddSmsTemplateReq(saSmsTemplate);
            RuntimeOptions runtime = new RuntimeOptions();
            // 复制代码运行请自行打印 API 的返回值
            if (!sysConfigCacheService.getSysConfigBool(SmsParam.SMS_MOCK_MODE)) {
                AddSmsTemplateResponse response = client.addSmsTemplateWithOptions(addSmsTemplateRequest, runtime);
                Validator.assertEquals(response.getBody().getCode(), ALI_SUCCESS, ErrCodeSys.SYS_ERR_MSG,
                        response.getBody().getMessage());
                return response.getBody();
            } else {
                return buildAddSmsTemplateResponseBody();
            }

        } catch (TeaException error) {
            // 如有需要，请打印 error
            com.aliyun.teautil.Common.assertAsString(error.message);
        } catch (ServiceException e) {
            Validator.assertException(e);
        } catch (Exception _error) {
            TeaException error = new TeaException(_error.getMessage(), _error);
            // 如有需要，请打印 error
            com.aliyun.teautil.Common.assertAsString(error.message);
        }
        return Validator.assertException(SmsErrorCode.APPLY_FAILED, saSmsTemplate.getTemplateTitle());
    }

    private Client createClient() throws Exception {
        Config config = new Config().setAccessKeyId(accessKeyId).setAccessKeySecret(accessKeySecret);
        config.endpoint = endpoint;
        return new Client(config);
    }

    private AddSmsTemplateRequest buildAddSmsTemplateReq(SaSmsTemplate saSmsTemplate) {
        AddSmsTemplateRequest addSmsTemplateRequest = new AddSmsTemplateRequest();
        addSmsTemplateRequest.setTemplateName(saSmsTemplate.getTemplateTitle());
        addSmsTemplateRequest.setTemplateType(saSmsTemplate.getTemplateType());
        addSmsTemplateRequest.setTemplateContent(saSmsTemplate.getBody());
        if (FuncUtil.isNotEmpty(saSmsTemplate.getRemark())) {
            addSmsTemplateRequest.setRemark(saSmsTemplate.getRemark());
        } else {
            addSmsTemplateRequest.setRemark(saSmsTemplate.getTemplateTitle());
        }
        return addSmsTemplateRequest;
    }

    private AddSmsTemplateResponseBody buildAddSmsTemplateResponseBody() {
        AddSmsTemplateResponseBody body = new AddSmsTemplateResponseBody();
        body.setTemplateCode(UUID.randomUUID().toString());
        return body;
    }

    public ModifySmsTemplateResponseBody modifySmsSignRequest(SaSmsTemplate saSmsTemplate) {
        try {
            Client client = createClient();
            ModifySmsTemplateRequest modifySmsTemplateRequest = buildModifySmsTemplateReq(saSmsTemplate);
            RuntimeOptions runtime = new RuntimeOptions();
            // 复制代码运行请自行打印 API 的返回值
            if (!sysConfigCacheService.getSysConfigBool(SmsParam.SMS_MOCK_MODE)) {
                ModifySmsTemplateResponse response = client.modifySmsTemplateWithOptions(modifySmsTemplateRequest,
                        runtime);
                Validator.assertEquals(response.getBody().getCode(), ALI_SUCCESS, ErrCodeSys.SYS_ERR_MSG,
                        response.getBody().getMessage());
                return response.getBody();
            } else {
                return buildModifySmsTemplateResponseBody();
            }

        } catch (TeaException error) {
            // 如有需要，请打印 error
            com.aliyun.teautil.Common.assertAsString(error.message);
        } catch (ServiceException e) {
            Validator.assertException(e);
        } catch (Exception _error) {
            TeaException error = new TeaException(_error.getMessage(), _error);
            // 如有需要，请打印 error
            com.aliyun.teautil.Common.assertAsString(error.message);
        }
        return Validator.assertException(SmsErrorCode.APPLY_FAILED, saSmsTemplate.getTemplateTitle());
    }

    private ModifySmsTemplateRequest buildModifySmsTemplateReq(SaSmsTemplate saSmsTemplate) {
        ModifySmsTemplateRequest modifySmsTemplateRequest = new ModifySmsTemplateRequest();
        modifySmsTemplateRequest.setTemplateName(saSmsTemplate.getTemplateTitle());
        modifySmsTemplateRequest.setTemplateType(saSmsTemplate.getTemplateType());
        modifySmsTemplateRequest.setTemplateContent(saSmsTemplate.getBody());
        modifySmsTemplateRequest.setTemplateCode(saSmsTemplate.getTemplateCode());
        if (FuncUtil.isNotEmpty(saSmsTemplate.getRemark())) {
            modifySmsTemplateRequest.setRemark(saSmsTemplate.getRemark());
        } else {
            modifySmsTemplateRequest.setRemark(saSmsTemplate.getTemplateTitle());
        }
        return modifySmsTemplateRequest;
    }

    private ModifySmsTemplateResponseBody buildModifySmsTemplateResponseBody() {
        ModifySmsTemplateResponseBody body = new ModifySmsTemplateResponseBody();
        body.setTemplateCode(UUID.randomUUID().toString());
        return body;
    }

    public DeleteSmsTemplateResponseBody deleteSmsTemplate(SaSmsTemplate saSmsTemplate) {
        try {
            Client client = createClient();
            DeleteSmsTemplateRequest deleteSmsTemplateRequest = buildDeleteSmsTemplateReq(saSmsTemplate);
            RuntimeOptions runtime = new RuntimeOptions();
            // 复制代码运行请自行打印 API 的返回值
            if (!sysConfigCacheService.getSysConfigBool(SmsParam.SMS_MOCK_MODE)) {
                DeleteSmsTemplateResponse response = client.deleteSmsTemplateWithOptions(deleteSmsTemplateRequest,
                        runtime);
                Validator.assertEquals(response.getBody().getCode(), ALI_SUCCESS, ErrCodeSys.SYS_ERR_MSG,
                        response.getBody().getMessage());
                return response.getBody();
            } else {
                return buildDeleteSmsTemplateResponseBody();
            }

        } catch (TeaException error) {
            // 如有需要，请打印 error
            com.aliyun.teautil.Common.assertAsString(error.message);
        } catch (ServiceException e) {
            Validator.assertException(e);
        } catch (Exception _error) {
            TeaException error = new TeaException(_error.getMessage(), _error);
            // 如有需要，请打印 error
            com.aliyun.teautil.Common.assertAsString(error.message);
        }
        return Validator.assertException(SmsErrorCode.APPLY_FAILED, saSmsTemplate.getTemplateTitle());
    }

    private DeleteSmsTemplateRequest buildDeleteSmsTemplateReq(SaSmsTemplate saSmsTemplate) {
        DeleteSmsTemplateRequest request = new DeleteSmsTemplateRequest();
        request.setTemplateCode(saSmsTemplate.getTemplateCode());
        return request;
    }

    private DeleteSmsTemplateResponseBody buildDeleteSmsTemplateResponseBody() {
        DeleteSmsTemplateResponseBody body = new DeleteSmsTemplateResponseBody();
        body.setTemplateCode(UUID.randomUUID().toString());
        return body;
    }

    public QuerySmsTemplateResponseBody getSmsTemplateStatus(SaSmsTemplate saSmsTemplate) {
        String templateCode = saSmsTemplate.getTemplateCode();
        try {
            Client client = createClient();
            QuerySmsTemplateRequest querySmsTemplateRequest = new QuerySmsTemplateRequest().setTemplateCode(
                    templateCode);
            RuntimeOptions runtime = new RuntimeOptions();
            // 复制代码运行请自行打印 API 的返回值
            if (!sysConfigCacheService.getSysConfigBool(SmsParam.SMS_MOCK_MODE)) {
                return client.querySmsTemplateWithOptions(querySmsTemplateRequest, runtime).getBody();
            } else {
                return buildQuerySmsTemplateResponseBody();
            }
        } catch (TeaException error) {
            // 如有需要，请打印 error
            com.aliyun.teautil.Common.assertAsString(error.message);
        } catch (ServiceException e) {
            Validator.assertException(e);
        } catch (Exception _error) {
            TeaException error = new TeaException(_error.getMessage(), _error);
            // 如有需要，请打印 error
            com.aliyun.teautil.Common.assertAsString(error.message);
        }
        return Validator.assertException(SmsErrorCode.APPLY_FAILED, saSmsTemplate.getTemplateTitle());
    }

    private QuerySmsTemplateResponseBody buildQuerySmsTemplateResponseBody() {
        QuerySmsTemplateResponseBody body = new QuerySmsTemplateResponseBody();
        body.setTemplateStatus(AliMessageTemplateConfirmStatusDict.AUDIT_STATE_PASS.getValue());
        return body;
    }

    public QuerySmsSignResponseBody getSmsSignStatus(String sign) {
        QuerySmsSignRequest querySmsSignRequest = new QuerySmsSignRequest();
        querySmsSignRequest.setSignName(sign);
        com.aliyun.teautil.models.RuntimeOptions runtime = new com.aliyun.teautil.models.RuntimeOptions();
        try {
            Client client = createClient();
            // 复制代码运行请自行打印 API 的返回值
            QuerySmsSignResponse response = client.querySmsSignWithOptions(querySmsSignRequest, runtime);
            return response.getBody();
        } catch (TeaException error) {
            // 如有需要，请打印 error
            com.aliyun.teautil.Common.assertAsString(error.message);
        } catch (Exception _error) {
            TeaException error = new TeaException(_error.getMessage(), _error);
            // 如有需要，请打印 error
            com.aliyun.teautil.Common.assertAsString(error.message);
        }
        return Validator.assertException(SmsErrorCode.QUERY_SIGN_FAILED, sign);
    }

    public QuerySmsSignListResponseBody getSmsSignList() {
        QuerySmsSignListRequest querySmsSignListRequest = new QuerySmsSignListRequest();
        com.aliyun.teautil.models.RuntimeOptions runtime = new com.aliyun.teautil.models.RuntimeOptions();
        try {
            Client client = createClient();
            // 复制代码运行请自行打印 API 的返回值
            QuerySmsSignListResponse response = client.querySmsSignListWithOptions(querySmsSignListRequest, runtime);
            return response.getBody();
        } catch (TeaException error) {
            // 如有需要，请打印 error
            com.aliyun.teautil.Common.assertAsString(error.message);
        } catch (Exception _error) {
            TeaException error = new TeaException(_error.getMessage(), _error);
            // 如有需要，请打印 error
            com.aliyun.teautil.Common.assertAsString(error.message);
        }
        return Validator.assertException(SmsErrorCode.QUERY_SIGN_FAILED, "");
    }

    public QuerySmsTemplateListResponseBody querySmsTemplateList(Integer currentPage, Integer pageSize) {
        QuerySmsTemplateListRequest querySmsTemplateListRequest = new QuerySmsTemplateListRequest();
        querySmsTemplateListRequest.setPageIndex(currentPage);
        querySmsTemplateListRequest.setPageSize(pageSize);
        try {
            Client client = createClient();
            // 复制代码运行请自行打印 API 的返回值
            QuerySmsTemplateListResponse response = client.querySmsTemplateList(querySmsTemplateListRequest);
            Validator.assertEquals(response.getBody().getCode(), ALI_SUCCESS, ErrCodeSys.SYS_ERR_MSG,
                    response.getBody().getMessage());
            return response.getBody();
        } catch (TeaException error) {
            // 如有需要，请打印 error
            com.aliyun.teautil.Common.assertAsString(error.message);
        } catch (Exception _error) {
            TeaException error = new TeaException(_error.getMessage(), _error);
            // 如有需要，请打印 error
            com.aliyun.teautil.Common.assertAsString(error.message);
        }
        return Validator.assertException(SmsErrorCode.QUERY_SMS_TEMPLATE_FAILED, "");
    }
}
