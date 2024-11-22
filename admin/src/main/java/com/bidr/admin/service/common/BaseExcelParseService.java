package com.bidr.admin.service.common;

import com.bidr.admin.service.excel.handler.PortalExcelHandlerInf;
import com.bidr.admin.service.excel.progress.PortalExcelUploadProgressInf;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.platform.constant.upload.UploadProgressStep;
import com.bidr.platform.service.excel.ModelDataListener;
import com.bidr.platform.utils.excel.EasyExcelUtil;
import com.bidr.platform.vo.upload.PortalUploadProgressRes;
import org.springframework.scheduling.annotation.Async;

import javax.annotation.Resource;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Map;

public abstract class BaseExcelParseService<ENTITY, VO> implements PortalExcelUploadProgressInf, PortalExcelHandlerInf {
    @Resource
    protected TokenService tokenService;

    protected Class<VO> getEntityClass() {
        return (Class<VO>) ReflectionUtil.getSuperClassGenericType(this.getClass(), 0);
    }

    protected Class<VO> getVoClass() {
        return (Class<VO>) ReflectionUtil.getSuperClassGenericType(this.getClass(), 1);
    }

    protected abstract ModelDataListener<ENTITY, VO> getExcelListener(Map<String, Object> arg);

    @Override
    public TokenService getTokenService() {
        return tokenService;
    }

    @Async
    public void parseFile(File file, Map<String, Object> arg) {
        startUploadProgress(100);
        EasyExcelUtil.read(file, getExcelListener(arg), getVoClass());
    }

    public byte[] templateExport() {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        templateExcel(os, getVoClass());
        return os.toByteArray();
    }

    protected void setUploadProgress(UploadProgressStep step, Integer total, Integer loaded, String comment) {
        if (FuncUtil.isEmpty(loaded) && FuncUtil.isNotEmpty(total)) {
            startValidateRecord(total);
        } else if (FuncUtil.isNotEmpty(loaded) && FuncUtil.isEmpty(comment)) {
            addUploadProgress(loaded);
        } else if (FuncUtil.isNotEmpty(comment)) {
            uploadProgressException(comment);
        } else {
            updateUploadProgress(step, total, loaded, comment);
        }
    }

    private void updateUploadProgress(UploadProgressStep step, Integer total, Integer loaded, String comment) {
        PortalUploadProgressRes item = getUploadProgress();
        item.setStep(step);
        if (FuncUtil.isNotEmpty(total)) {
            item.setTotal(total);
        }
        if (FuncUtil.isNotEmpty(loaded)) {
            item.setLoaded(loaded);
        }
        if (FuncUtil.isNotEmpty(comment)) {
            item.getComments().add(comment);
        }
        setUploadProgress(item);
    }
}
