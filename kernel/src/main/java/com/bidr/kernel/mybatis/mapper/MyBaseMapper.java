package com.bidr.kernel.mybatis.mapper;

import com.diboot.core.mapper.BaseCrudMapper;
import com.github.jeffreyning.mybatisplus.base.MppBaseMapper;
import com.github.yulichang.base.MPJBaseMapper;

/**
 * Title: MyBaseMapper
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2022/12/09 09:04
 */
public interface MyBaseMapper<T> extends MppBaseMapper<T>, MPJBaseMapper<T>, BaseCrudMapper<T> {
}
