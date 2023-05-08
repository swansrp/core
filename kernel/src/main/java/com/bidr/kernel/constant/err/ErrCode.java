package com.bidr.kernel.constant.err;

/**
 * Title: ErrCode
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2022/6/6 11:17
 */
public interface ErrCode {
    /**
     * 错误码
     *
     * @return 错误码
     */
    Integer getErrCode();

    /**
     * 错误名称
     *
     * @return 错误名称
     */
    String name();

    /**
     * 错误类型
     *
     * @return 报错等级
     */
    default String getErrLevel() {
        return ErrCodeLevel.INFO.getValue();
    }

    /**
     * 错误类型
     *
     * @return 报错类型
     */
    default String getErrType() {
        return ErrCodeType.SYSTEM.getValue();
    }

    /**
     * 获取报错信息内容
     *
     * @param parameterArr 报错内容
     * @return 报错信息内容
     */
    default String getErrText(Object... parameterArr) {
        return String.format(getErrMsg(), parameterArr);
    }

    /**
     * 报错信息
     *
     * @return 报错信息
     */
    String getErrMsg();
}
