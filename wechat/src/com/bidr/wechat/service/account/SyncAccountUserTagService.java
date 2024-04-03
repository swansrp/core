package com.bidr.wechat.service.account;

import com.bidr.authorization.bo.role.RoleInfo;
import com.bidr.authorization.dao.entity.AcRole;
import com.bidr.authorization.dao.repository.AcRoleService;
import com.bidr.authorization.dao.repository.join.AcUserRoleMenuService;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.exception.ServiceException;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.wechat.dao.entity.MmOpenidMap;
import com.bidr.wechat.dao.entity.MmRoleTagMap;
import com.bidr.wechat.dao.repository.MmOpenidMapService;
import com.bidr.wechat.dao.repository.MmRoleTagMapService;
import com.bidr.wechat.po.platform.tag.TagListRes;
import com.bidr.wechat.po.platform.tag.UserTag;
import com.bidr.wechat.service.user.WechatPublicUserService;
import com.bidr.wechat.service.user.WechatPublicUserTagService;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * Title: SyncAccountUserTagService
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2020/6/17 18:01
 */
@Service
public class SyncAccountUserTagService {

    private static final String INVALID_TAG_NAME = "45157";

    @Resource
    private AcRoleService acRoleService;
    @Resource
    private AcUserRoleMenuService acUserRoleMenuService;
    @Resource
    private MmRoleTagMapService mmRoleTagMapService;
    @Resource
    private MmOpenidMapService mmOpenidMapService;
    @Resource
    private WechatPublicUserService wechatPublicUserService;
    @Resource
    private WechatPublicUserTagService wechatPublicUserTagService;

    @Transactional(rollbackFor = Exception.class)
    public void syncUserTagByUserRole(String openId) {
        String wechatId = openId;
        MmOpenidMap openIdMap = mmOpenidMapService.getOpenidMapByOpenId(openId);
        if (openIdMap != null) {
            wechatId = openIdMap.getUnionId();
        }
        List<RoleInfo> roleList = acUserRoleMenuService.getRole(wechatId);
        Validator.assertNotNull(roleList, ErrCodeSys.PA_DATA_NOT_EXIST, "用户");
        if (CollectionUtils.isNotEmpty(roleList)) {
            for (RoleInfo role : roleList) {
                MmRoleTagMap map = mmRoleTagMapService.getOneRoleTagMapByRoleId(role.getRoleId());
                if (map == null) {
                    syncWechatPublicUserTagByRole(role.getRoleId());
                } else {
                    wechatPublicUserService.setUserTag(openId, map.getTagId());
                }
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public UserTag syncWechatPublicUserTagByRole(Long roleId) {
        UserTag req = buildUserTag(roleId);
        UserTag res = null;
        try {
            res = wechatPublicUserTagService.createUserTag(req);
        } catch (ServiceException e) {
            if (e.getErrObj().equals(INVALID_TAG_NAME)) {
                res = getUserTagFromWechat(req);
            }
            if (res == null) {
                throw e;
            }
        }
        syncMmRoleTagMap(res.getId(), res.getName(), roleId);
        return res;
    }

    private UserTag buildUserTag(Long roleId) {
        AcRole role = acRoleService.getById(roleId);
        Validator.assertNotNull(role, ErrCodeSys.PA_DATA_NOT_EXIST, "角色id");
        UserTag userTag = new UserTag();
        userTag.setName(role.getRoleName());
        return userTag;
    }

    private UserTag getUserTagFromWechat(UserTag req) {
        TagListRes tagListRes = wechatPublicUserTagService.getUserTag();
        Map<String, UserTag> tagMap = ReflectionUtil.reflectToMap(tagListRes.getTags(), "name");
        return tagMap.get(req.getName());
    }

    private void syncMmRoleTagMap(Integer tagId, String tagName, Long roleId) {
        MmRoleTagMap map = new MmRoleTagMap();
        map.setTagName(tagName);
        map.setTagId(tagId);
        map.setRoleId(roleId);
        mmRoleTagMapService.insert(map);
    }
}
