package com.bidr.kernel.common.func;

import java.io.Serializable;
import java.util.function.Function;

/**
 * Title: GetFunc
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/22 10:07
 */
@FunctionalInterface
public interface GetFunc<T, R> extends Function<T, R>, Serializable {
}
