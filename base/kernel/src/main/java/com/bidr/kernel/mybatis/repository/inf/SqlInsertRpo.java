package com.bidr.kernel.mybatis.repository.inf;

import java.util.Collection;

/**
 * Title: SqlInsertRpo
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2022/12/07 18:41
 */
public interface SqlInsertRpo<T> {
    boolean insert(T entity);

    boolean insert(Collection<T> entity);
}
