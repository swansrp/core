package com.bidr.authorization.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.authorization.dao.entity.AcTenant;
import com.bidr.authorization.dao.mapper.AcTenantMapper;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.FuncUtil;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Title: AcTenantService
 * Description: Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 * <p>🔴 类名硬约束：框架 {@code BaseAdminController.getRepo()} 按
 * {@code Introspector.decapitalize(实体简名)+"Service"} 从容器取 bean，本类必须叫此名。</p>
 *
 * @author Sharp
 * @since 2026-10-17
 */
@Service
public class AcTenantService extends BaseSqlRepo<AcTenantMapper, AcTenant> {

    /**
     * 按业务编码取有效租户（valid='1'）。
     * <p>🔴 code 索引故意非唯一（软删位 × 唯一键的三态问题，同 AcSsoRedirect 定案）：
     * 撤销后再启用同 code 会并存多行，故这里只取第一条有效行，不用 selectOne——
     * 历史脏数据下同 code 多有效行时 MyBatis 会抛 TooManyResultsException。</p>
     *
     * @param code 业务编码（null/空串 ⇒ null，不抛）
     * @return 命中的有效租户；未命中 ⇒ null
     */
    public AcTenant getByCode(String code) {
        if (FuncUtil.isEmpty(code)) {
            return null;
        }
        LambdaQueryWrapper<AcTenant> wrapper = super.getQueryWrapper();
        wrapper.eq(AcTenant::getCode, code);
        wrapper.eq(AcTenant::getValid, CommonConst.YES);
        List<AcTenant> tenants = select(wrapper);
        return FuncUtil.isEmpty(tenants) ? null : tenants.get(0);
    }

    /**
     * 租户目录列表（有效租户全量，管理面用，批乙棒4 加法）。
     *
     * @return valid='1' 的租户列表，按 id 升序；无数据时返回空列表
     */
    public List<AcTenant> listValid() {
        LambdaQueryWrapper<AcTenant> wrapper = super.getQueryWrapper();
        wrapper.eq(AcTenant::getValid, CommonConst.YES);
        wrapper.orderByAsc(AcTenant::getId);
        return select(wrapper);
    }
}
