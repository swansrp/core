package com.bidr.kernel.mybatis.intercept;

import org.apache.ibatis.mapping.MappedStatement;

/**
 * Title: ExecutorIntercept
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2022/7/25 11:18
 */
public interface ExecutorUpdateIntercept extends MybatisIntercept {

    /**
     * @param mappedStatement
     * @param parameter
     */
    void proceed(MappedStatement mappedStatement, Object parameter);
}
