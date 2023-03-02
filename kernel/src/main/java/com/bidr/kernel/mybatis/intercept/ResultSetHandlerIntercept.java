package com.bidr.kernel.mybatis.intercept;

import java.util.List;

/**
 * Title: ResultSetHandlerIntercept
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2022/7/25 11:20
 */
public interface ResultSetHandlerIntercept extends MybatisIntercept {
    void proceed(List<Object> resultList);
}
