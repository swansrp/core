package com.bidr.admin.service.excel.progress;

import com.bidr.platform.constant.upload.UploadProgressStep;
import com.bidr.platform.vo.upload.PortalUploadProgressRes;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.kernel.utils.FuncUtil;

import java.util.ArrayList;

/**
 * Title: PortalExcelUploadProgressInf
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/02/15 22:45
 */
public interface PortalExcelUploadProgressInf {
    /**
     * 开始上传进度
     *
     * @param total 数据总数
     */
    default void startUploadProgress(Integer total) {
        PortalUploadProgressRes item = new PortalUploadProgressRes(UploadProgressStep.UPLOAD,
                total, 0, new ArrayList<>());
        setUploadProgress(item);
    }

    /**
     * tokenService
     *
     * @return tokenService
     */
    TokenService getTokenService();

    /**
     * 上传进度key
     *
     * @return 上传进度key
     */
    String getProgressKey();

    /**
     * 开始校验数据
     *
     * @param total 数据总数
     */
    default void startValidateRecord(Integer total) {
        PortalUploadProgressRes item = new PortalUploadProgressRes(UploadProgressStep.VALIDATE,
                total, 0, new ArrayList<>());
        setUploadProgress(item);
    }

    /**
     * 开始存储数据
     */
    default void startSaveRecord() {
        PortalUploadProgressRes item = getUploadProgress();
        item.setStep(UploadProgressStep.SAVE);
        item.setLoaded(0);
        setUploadProgress(item);
    }

    /**
     * 获取上传处理进度
     *
     * @return 进度
     */
    default PortalUploadProgressRes getUploadProgress() {
        PortalUploadProgressRes res = getTokenService().getItem(getProgressKey(), PortalUploadProgressRes.class);
        if (FuncUtil.isEmpty(res)) {
            res = new PortalUploadProgressRes(UploadProgressStep.INIT, 0, 0, new ArrayList<>());
        }
        return res;
    }

    /**
     * 设置上传处理进度
     *
     * @param progress 进度信息
     */

    default void setUploadProgress(PortalUploadProgressRes progress) {
        getTokenService().putItem(getProgressKey(), progress);
    }

    /**
     * 设置上传处理进度
     *
     * @param loaded 已处理个数
     */

    default void addUploadProgress(Integer loaded) {
        PortalUploadProgressRes item = getUploadProgress();
        item.setLoaded(loaded);
        setUploadProgress(item);
    }

    /**
     * 上传数据处理完成
     */
    default void uploadProgressFinish() {
        PortalUploadProgressRes item = getUploadProgress();
        item.setStep(UploadProgressStep.SUCCESS);
        item.setLoaded(item.getTotal());
        item.getComments().clear();
        setUploadProgress(item);
    }

    /**
     * 上传数据失败
     *
     * @param reason 失败原因
     */
    default void uploadProgressException(String reason) {
        PortalUploadProgressRes item = getUploadProgress();
        item.setStep(UploadProgressStep.FAILED);
        item.setLoaded(item.getTotal());
        item.getComments().add(reason);
        setUploadProgress(item);
    }


}
