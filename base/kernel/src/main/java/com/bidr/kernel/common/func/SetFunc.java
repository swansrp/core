package com.bidr.kernel.common.func;

import java.io.Serializable;

/**
 * Title: SetFunc
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/22 10:03
 */

@FunctionalInterface
public interface SetFunc<T1, T2> extends Serializable {
    /**
     * @param t1
     * @param t2
     */
    void apply(T1 t1, T2 t2);
}
