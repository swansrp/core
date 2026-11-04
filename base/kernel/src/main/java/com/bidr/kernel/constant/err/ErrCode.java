package com.bidr.kernel.constant.err;

import java.util.Arrays;

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
    default ErrCodeLevel getErrLevel() {
        return ErrCodeLevel.INFO;
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
     * 获取报错信息内容。
     *
     * <p>🔴 格式化失败不得把调用点炸成 500（2026-11-04 修，对账单「信封契约」）：错误码文案允许带
     * 占位符（如 {@code PA_DATA_NOT_EXIST(13, "该%s数据不存在")}），而调用方可能无参抛出
     * （{@code new ServiceException(PA_DATA_NOT_EXIST)}）——`String.format` 会抛
     * {@code MissingFormatArgumentException}，把"业务报错"变成"框架异常"，
     * 掩盖真实原因（实测：EnvelopeContractTest 的 provider 无参失败腿因此红）。</p>
     *
     * <p>策略：①无参 ⇒ 原样返回文案（占位符保持可见，至少可读且不炸）；②有参但个数/类型不匹配 ⇒
     * 原文 ＋ 实参串（最接近原意）。正常路径（参数与占位符匹配）结果与改前逐字一致。</p>
     *
     * @param parameterArr 报错内容
     * @return 报错信息内容
     */
    default String getErrText(Object... parameterArr) {
        String msg = getErrMsg();
        if (msg == null) {
            return null;
        }
        if (parameterArr == null || parameterArr.length == 0) {
            return msg;
        }
        try {
            return String.format(msg, parameterArr);
        } catch (RuntimeException ignore) {
            return msg + Arrays.toString(parameterArr);
        }
    }

    /**
     * 报错信息
     *
     * @return 报错信息
     */
    String getErrMsg();
}
