package com.bidr.authorization.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.mapper.AcUserDao;
import com.bidr.authorization.vo.user.UserExistedReq;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.FuncUtil;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Title: AcUserService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/17 10:02
 */
@Service
public class AcUserService extends BaseSqlRepo<AcUserDao, AcUser> {

    public List<AcUser> getUserByDeptAndName(List<String> deptIdList, String name) {
        LambdaQueryWrapper<AcUser> wrapper = super.getQueryWrapper();
        wrapper.in(FuncUtil.isNotEmpty(deptIdList), AcUser::getDeptId, deptIdList)
                .like(FuncUtil.isNotEmpty(name), AcUser::getName, name).or(w -> w.eq(AcUser::getUserName, name));
        if (FuncUtil.isEmpty(deptIdList)) {
            return super.select(wrapper, 1, 50, false).getRecords();
        } else {
            return super.select(wrapper);
        }

    }

    public AcUser getUserByUserName(String userName) {
        LambdaQueryWrapper<AcUser> wrapper = super.getQueryWrapper().eq(AcUser::getUserName, userName)
                .eq(AcUser::getValid, CommonConst.YES);
        return selectOne(wrapper);
    }

    public AcUser getUserByPhoneNumber(String phoneNumber) {
        LambdaQueryWrapper<AcUser> wrapper = super.getQueryWrapper().eq(AcUser::getPhoneNumber, phoneNumber)
                .eq(AcUser::getValid, CommonConst.YES);
        return selectOne(wrapper);
    }

    public AcUser getUserByEmail(String email) {
        LambdaQueryWrapper<AcUser> wrapper = super.getQueryWrapper().eq(AcUser::getEmail, email)
                .eq(AcUser::getValid, CommonConst.YES);
        return selectOne(wrapper);
    }

    public AcUser getByCustomerNumber(String customerNumber) {
        LambdaQueryWrapper<AcUser> wrapper = super.getQueryWrapper().eq(AcUser::getCustomerNumber, customerNumber)
                .eq(AcUser::getValid, CommonConst.YES);
        return selectOne(wrapper);
    }

    public List<AcUser> existedUser(UserExistedReq req) {
        LambdaQueryWrapper<AcUser> wrapper = super.getQueryWrapper().eq(AcUser::getValid, CommonConst.YES)
                .nested(wr -> wr.eq(FuncUtil.isNotEmpty(req.getPhoneNumber()), AcUser::getPhoneNumber,
                                req.getPhoneNumber()).or()
                        .eq(FuncUtil.isNotEmpty(req.getEmail()), AcUser::getEmail, req.getEmail()).or()
                        .eq(FuncUtil.isNotEmpty(req.getUserName()), AcUser::getUserName, req.getUserName()).or()
                        .eq(FuncUtil.isNotEmpty(req.getName()), AcUser::getName, req.getName()).or()
                        .eq(FuncUtil.isNotEmpty(req.getIdNumber()), AcUser::getIdNumber, req.getIdNumber()));
        return select(wrapper);
    }

    public AcUser getUserByWechatId(String wechatOpenId) {
        LambdaQueryWrapper<AcUser> wrapper = super.getQueryWrapper().eq(AcUser::getWechatId, wechatOpenId)
                .eq(AcUser::getValid, CommonConst.YES);
        return selectOne(wrapper);
    }

    public void deleteByUserName(String userName) {
        LambdaQueryWrapper<AcUser> wrapper = super.getQueryWrapper().eq(AcUser::getUserName, userName)
                .eq(AcUser::getValid, CommonConst.YES);
        super.delete(wrapper);
    }
}






