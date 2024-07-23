package com.bidr.authorization.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.authorization.dao.entity.AcAccount;
import com.bidr.authorization.dao.entity.AcDept;
import com.bidr.authorization.dao.mapper.AcAccountDao;
import com.bidr.kernel.constant.dict.common.ActiveStatusDict;
import com.bidr.kernel.constant.dict.common.BoolDict;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.FuncUtil;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Title: AcAccountService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/23 11:30
 */
@Service
public class AcAccountService extends BaseSqlRepo<AcAccountDao, AcAccount> {
    @Override
    public void truncate() {
        super.baseMapper.truncate();
    }

    public List<AcAccount> getAccountByDeptAndName(List<String> deptIdList, String name) {
        LambdaQueryWrapper<AcAccount> wrapper = super.getQueryWrapper();
        wrapper.eq(AcAccount::getStatus, ActiveStatusDict.ACTIVATE.getValue())
                .eq(AcAccount::getEmployStatus, BoolDict.YES.getValue())
                .in(FuncUtil.isNotEmpty(deptIdList), AcAccount::getDepartment, deptIdList)
                .like(FuncUtil.isNotEmpty(name), AcAccount::getName, name);
        if (FuncUtil.isEmpty(deptIdList)) {
            return super.select(wrapper, 1, 50, false).getRecords();
        } else {
            return super.select(wrapper);
        }

    }

    public AcAccount getAccountByUserName(String userName) {
        LambdaQueryWrapper<AcAccount> wrapper = super.getQueryWrapper();
        wrapper.eq(AcAccount::getStatus, ActiveStatusDict.ACTIVATE.getValue())
                .eq(AcAccount::getEmployStatus, BoolDict.YES.getValue()).eq(AcAccount::getUserName, userName);
        return selectOne(wrapper);
    }

    public AcAccount getAccountByMobile(String mobile) {
        LambdaQueryWrapper<AcAccount> wrapper = super.getQueryWrapper();
        wrapper.eq(AcAccount::getStatus, ActiveStatusDict.ACTIVATE.getValue())
                .eq(AcAccount::getEmployStatus, BoolDict.YES.getValue()).eq(AcAccount::getMobile, mobile);
        return selectOne(wrapper);
    }

    public List<AcAccount> selectActive() {
        LambdaQueryWrapper<AcAccount> wrapper = super.getQueryWrapper();
        wrapper.eq(AcAccount::getStatus, ActiveStatusDict.ACTIVATE.getValue())
                .eq(AcAccount::getEmployStatus, BoolDict.YES.getValue());
        return super.select(wrapper);
    }

    public List<AcAccount> getAccountByUserNameOrDeptNameOrAccountId(List<String> names, boolean active) {
        MPJLambdaWrapper<AcAccount> wrapper = new MPJLambdaWrapper<>();
        wrapper.leftJoin(AcDept.class, AcDept::getDeptId, AcAccount::getDepartment);
        wrapper.eq(active, AcAccount::getStatus, ActiveStatusDict.ACTIVATE.getValue())
                .eq(active, AcAccount::getEmployStatus, BoolDict.YES.getValue());
        wrapper.nested(FuncUtil.isNotEmpty(names), wr -> {
            for (String name : names) {
                wr.or(rr -> rr.like(AcAccount::getName, name).or(r -> r.like(AcDept::getName, name))
                        .or(r -> r.eq(AcAccount::getId, name)).or(r -> r.like(AcAccount::getUserName, name)));
            }
        });
        return selectJoinList(AcAccount.class, wrapper);
    }
}


