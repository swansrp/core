package com.bidr.platform.service.callback;

/**
 * Title: Promise
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2022/6/6 11:12
 */
public interface MyCallBack {
    /**
     * 成功回调
     *
     * @param param 参数表
     */
    default void onSuccess(Object... param) {
    }

    /**
     * 失败回调
     *
     * @param param 参数表
     */
    default void onException(Object... param) {
    }

}
