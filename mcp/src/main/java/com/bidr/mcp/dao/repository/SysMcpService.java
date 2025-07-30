package com.bidr.mcp.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.mcp.dao.entity.SysMcp;
import com.bidr.mcp.dao.mapper.SysMcpMapper;
import org.springframework.stereotype.Service;

/**
 * @author sharp
 */
@Service
public class SysMcpService extends BaseSqlRepo<SysMcpMapper, SysMcp> {

    public SysMcp get(String endPoint, String name, String type) {
        LambdaQueryWrapper<SysMcp> wrapper = super.getQueryWrapper();
        wrapper.eq(SysMcp::getEndPoint, endPoint);
        wrapper.eq(SysMcp::getName, name);
        wrapper.eq(SysMcp::getType, type);
        return selectOne(wrapper);
    }
}
