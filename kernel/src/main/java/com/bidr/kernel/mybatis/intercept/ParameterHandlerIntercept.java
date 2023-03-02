package com.bidr.kernel.mybatis.intercept;

import org.apache.ibatis.executor.parameter.ParameterHandler;

/**
 * Title: ParameterHandlerIntercept
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2022/7/25 11:19
 */
public interface ParameterHandlerIntercept extends MybatisIntercept {

    void proceed(ParameterHandler parameterHandler);
}
