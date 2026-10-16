package com.bidr.authorization.dao.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface AcUserDao extends MyBaseMapper<AcUser> {

    /**
     * 🔴 租户管理面专用 ac_user 取数口（批乙修1，M-24；全模块 {@code @InterceptorIgnore} 唯一命中点，
     * 守卫针 {@code TenantGateConventionTest} 钉这个数量）。addMember 的目标查在否与
     * guardTenantAdmin 的 operator 解析必须绕过租户数据闸——闸按登录烤定的 claims 租户档改写
     * SELECT（M-11 时效窗口：成员后挂、claims 未含该租户），否则"单租户管理员加新人"
     * （目标仅属他租户或尚无租户）永假。判权不在此口：调用方（AcTenantPortalService）
     * 先过 guardTenantAdmin 且动作后仍断言 valid 存在性，取数与判权分离。
     * 两参二选一（userId 优先）；都为空 ⇒ 恒假条件取不到行（防全表）。
     */
    @InterceptorIgnore(dataPermission = "true")
    @Select("<script>"
            + "SELECT * FROM ac_user WHERE "
            + "<choose>"
            + "<when test='userId != null'>user_id = #{userId}</when>"
            + "<when test='customerNumber != null and customerNumber != \"\"'>customer_number = #{customerNumber}</when>"
            + "<otherwise>1 = 0</otherwise>"
            + "</choose>"
            + " LIMIT 1</script>")
    AcUser selectTenantPortalUser(@Param("userId") Long userId, @Param("customerNumber") String customerNumber);
}
