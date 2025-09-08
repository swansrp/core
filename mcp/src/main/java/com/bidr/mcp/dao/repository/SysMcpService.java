package com.bidr.mcp.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.kernel.mybatis.inf.MybatisPlusTableInitializerInf;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.vo.common.KeyValueResVO;
import com.bidr.mcp.dao.entity.SysMcp;
import com.bidr.mcp.dao.mapper.SysMcpMapper;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author sharp
 */
@Service
public class SysMcpService extends BaseSqlRepo<SysMcpMapper, SysMcp> implements MybatisPlusTableInitializerInf {

    public SysMcp get(String endPoint, String name, String type) {
        LambdaQueryWrapper<SysMcp> wrapper = super.getQueryWrapper();
        wrapper.eq(SysMcp::getEndPoint, endPoint);
        wrapper.eq(SysMcp::getName, name);
        wrapper.eq(SysMcp::getType, type);
        return selectOne(wrapper);
    }

    public List<SysMcp> get(String endPoint, String type) {
        LambdaQueryWrapper<SysMcp> wrapper = super.getQueryWrapper();
        wrapper.eq(SysMcp::getEndPoint, endPoint);
        wrapper.eq(SysMcp::getType, type);
        return select(wrapper);
    }

    public List<KeyValueResVO> groupBy() {
        MPJLambdaWrapper<SysMcp> wrapper = super.getMPJLambdaWrapper();
        wrapper.selectAs(SysMcp::getEndPoint, KeyValueResVO::getValue);
        wrapper.selectAs(SysMcp::getEndPointName, KeyValueResVO::getLabel);
        wrapper.groupBy(SysMcp::getEndPoint, SysMcp::getEndPointName);
        return selectJoinList(KeyValueResVO.class, wrapper);
    }

    @Override
    public String getSql() {
        return "CREATE TABLE IF NOT EXISTS `sys_mcp` (\n" +
                "  `end_point` varchar(200) NOT NULL COMMENT 'mcp服务',\n" +
                "  `end_point_name` varchar(200) NOT NULL COMMENT 'mcp服务名称',\n" +
                "  `name` varchar(200) NOT NULL COMMENT 'mcp方法名',\n" +
                "  `type` varchar(20) NOT NULL COMMENT 'mcp类型',\n" +
                "  `description` LONGTEXT NOT NULL COMMENT 'mcp方法描述',\n" +
                "  PRIMARY KEY (`end_point`,`name`,`type`) USING BTREE\n" +
                ") COMMENT='mcp配置表';";
    }
}
