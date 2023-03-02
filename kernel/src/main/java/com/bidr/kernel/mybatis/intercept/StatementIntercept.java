package com.bidr.kernel.mybatis.intercept;

import org.apache.ibatis.executor.statement.StatementHandler;

/**
 * Title: StatementIntercept
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2022/7/25 11:19
 */
public interface StatementIntercept extends MybatisIntercept {

    void proceed(StatementHandler statementHandler);
}
