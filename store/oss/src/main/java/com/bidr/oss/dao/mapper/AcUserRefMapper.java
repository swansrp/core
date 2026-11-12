package com.bidr.oss.dao.mapper;

import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import com.bidr.oss.dao.entity.AcUserRef;
import org.apache.ibatis.annotations.Mapper;

/**
 * Title: AcUserRefMapper
 * Description: ac_user 只读映射 mapper（D-1 依赖收窄）：MPJLambdaWrapper 的 selectJoinList/leftJoin
 * 要求 JOIN 实体有注册 Mapper（原先靠 authorization 侧 AcUserMapper，AcUserRef 本地化后须自带，
 * 否则运行时报 "mapper not find by class"）。空接口；包形命中 BaseApplication @MapperScan 默认，
 * 形态同 AcSubsystemMapper 先例。本表对该模块只读，不落任何写方法调用。
 *
 * @author Sharp
 * @since 2026-11-13
 */
@Mapper
public interface AcUserRefMapper extends MyBaseMapper<AcUserRef> {
}
