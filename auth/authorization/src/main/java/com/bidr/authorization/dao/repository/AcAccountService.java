package com.bidr.authorization.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.authorization.dao.entity.AcAccount;
import com.bidr.authorization.dao.entity.AcDept;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.mapper.AcAccountDao;
import com.bidr.kernel.constant.dict.common.ActiveStatusDict;
import com.bidr.kernel.constant.dict.common.BoolDict;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

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
                .in(FuncUtil.isNotEmpty(deptIdList), AcAccount::getDepartment, deptIdList);
        if (FuncUtil.isNotEmpty(name)) {
            String[] nameArray = name.split(",");
            boolean hasMultipleNames = nameArray.length > 1;
            wrapper.nested(w -> {
                AtomicBoolean first = new AtomicBoolean(true);

                if (hasMultipleNames) {
                    // 多个名字时，使用精确匹配
                    Arrays.stream(nameArray).forEach(n -> {
                        if (!first.get()) {
                            w.or();
                        }
                        first.set(false);
                        w.nested(inner -> inner
                                .eq(AcAccount::getName, n)
                                .or()
                                .eq(AcAccount::getUserName, n)
                        );
                    });
                } else {
                    // 单个名字时，使用模糊匹配
                    w.nested(inner -> inner
                            .like(AcAccount::getName, nameArray[0])
                            .or()
                            .like(AcAccount::getUserName, nameArray[0])
                    );
                }
            });
        }
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

    public List<AcAccount> getAccountByName(String name) {
        LambdaQueryWrapper<AcAccount> wrapper = super.getQueryWrapper();
        wrapper.eq(AcAccount::getStatus, ActiveStatusDict.ACTIVATE.getValue())
                .eq(AcAccount::getEmployStatus, BoolDict.YES.getValue()).eq(AcAccount::getName, name);
        return select(wrapper);
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

    public Map<Object, Object> getNamesByCustomerNumberList(HashSet<Object> customerNumberList) {
        Map<Object, Object> map = new HashMap<>();
        if (FuncUtil.isNotEmpty(customerNumberList)) {
            LambdaQueryWrapper<AcAccount> wrapper = super.getQueryWrapper().in(AcAccount::getId, customerNumberList);
            List<AcAccount> users = select(wrapper);
            Map<String, AcAccount> acUserMap = ReflectionUtil.reflectToMap(users, AcAccount::getId);
            for (Object customerNumber : customerNumberList) {
                map.put(customerNumber, acUserMap.get(customerNumber));
            }
        }
        return map;
    }
}


