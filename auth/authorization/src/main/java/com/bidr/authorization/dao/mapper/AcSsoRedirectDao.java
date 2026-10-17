package com.bidr.authorization.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bidr.authorization.dao.entity.AcSsoRedirect;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Title: AcSsoRedirectDao
 * Description: Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-10-14
 */
@Mapper
public interface AcSsoRedirectDao extends BaseMapper<AcSsoRedirect>, MyBaseMapper<AcSsoRedirect> {
}
