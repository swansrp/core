package com.bidr.oss.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.constant.err.ErrCode;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.Base64Util;
import com.bidr.kernel.utils.DateUtil;
import com.bidr.kernel.utils.RandomUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.oss.dao.entity.SaWikiCollaborator;
import com.bidr.oss.dao.entity.SaWikiPage;
import com.bidr.oss.dao.repository.SaWikiCollaboratorService;
import com.bidr.oss.dao.repository.SaWikiPageService;
import com.bidr.oss.vo.OssWikiCollaboratorVO;
import com.bidr.oss.vo.OssWikiPageShareVO;
import com.bidr.platform.redis.service.RedisService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Wiki协作者Service
 *
 * @author sharp
 * @since 2025-12-12
 */
@Service
@RequiredArgsConstructor
public class WikiCollaboratorPortalService {

    private static final String SHARE_CODE_KEY = "wiki:share:code:";
    private final SaWikiPageService wikiPageService;
    private final SaWikiCollaboratorService collaboratorService;
    private final RedisService redisService;

    /**
     * 申请编辑权限
     *
     * @param pageId     页面ID
     * @param permission 权限类型
     * @param requestMsg 申请说明
     */
    @Transactional(rollbackFor = Exception.class)
    public SaWikiCollaborator requestAccess(Long pageId, String permission, String requestMsg) {
        SaWikiPage page = wikiPageService.getById(pageId);
        Validator.assertNotNull(page, ErrCodeSys.PA_DATA_NOT_EXIST, "页面");

        String currentUserId = AccountContext.getOperator();

        Validator.assertNotEquals(currentUserId, page.getAuthorId(), ErrCodeSys.SYS_ERR_MSG, "您是该页面的作者，无需申请权限");

        // 检查是否已有申请
        LambdaQueryWrapper<SaWikiCollaborator> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SaWikiCollaborator::getPageId, pageId)
                .eq(SaWikiCollaborator::getUserId, currentUserId);

        SaWikiCollaborator collaborator = collaboratorService.getOne(wrapper);
        if (collaborator != null) {
            if (collaborator.getPermission().equals(permission)) {
                if ("0".equals(collaborator.getStatus())) {
                    Validator.assertException(ErrCodeSys.SYS_ERR_MSG, "您已提交申请，请等待审批");
                } else if ("1".equals(collaborator.getStatus())) {
                    Validator.assertException(ErrCodeSys.SYS_ERR_MSG, "您已拥有该页面的访问权限");
                }
            } else {
                collaborator.setPermission(permission != null ? permission : "2");
                // 待审批
                collaborator.setStatus("0");
                collaborator.setRequestMsg(requestMsg);
                collaboratorService.updateById(collaborator);
            }
        } else {
            collaborator = new SaWikiCollaborator();
            collaborator.setPageId(pageId);
            collaborator.setUserId(currentUserId);
            collaborator.setPermission(permission != null ? permission : "2");
            // 待审批
            collaborator.setStatus("0");
            collaborator.setRequestMsg(requestMsg);

            collaboratorService.save(collaborator);
        }
        return collaborator;
    }

    /**
     * 获取页面的协作者列表
     *
     * @param pageId 页面ID
     * @return 协作者列表
     */
    public List<OssWikiCollaboratorVO> getCollaborators(Long pageId) {
        SaWikiPage page = wikiPageService.getById(pageId);
        Validator.assertNotNull(page, ErrCodeSys.PA_DATA_NOT_EXIST, "页面");

        String currentUserId = AccountContext.getOperator();
        Validator.assertEquals(currentUserId, page.getAuthorId(), ErrCodeSys.SYS_ERR_MSG, "只有作者可以查看协作者列表");

        LambdaQueryWrapper<SaWikiCollaborator> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SaWikiCollaborator::getPageId, pageId)
                .orderByDesc(SaWikiCollaborator::getCreateAt);

        return Resp.convert(collaboratorService.list(wrapper).stream()
                .map(this::convertToVO)
                .collect(Collectors.toList()), OssWikiCollaboratorVO.class);
    }

    /**
     * 获取待审批的申请
     *
     * @param pageId 页面ID
     * @return 待审批的协作者列表
     */
    public List<OssWikiCollaboratorVO> getPendingRequests(Long pageId) {
        SaWikiPage page = wikiPageService.getById(pageId);
        Validator.assertNotNull(page, ErrCodeSys.PA_DATA_NOT_EXIST, "页面");

        String currentUserId = AccountContext.getOperator();
        Validator.assertEquals(currentUserId, page.getAuthorId(), ErrCodeSys.SYS_ERR_MSG, "只有作者可以查看待审批申请");

        LambdaQueryWrapper<SaWikiCollaborator> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SaWikiCollaborator::getPageId, pageId)
                .eq(SaWikiCollaborator::getStatus, "0")
                .orderByDesc(SaWikiCollaborator::getCreateAt);

        return Resp.convert(collaboratorService.list(wrapper).stream()
                .map(this::convertToVO)
                .collect(Collectors.toList()), OssWikiCollaboratorVO.class);
    }

    /**
     * 审批申请
     *
     * @param pageId   页面ID
     * @param userId   用户ID
     * @param approved 是否通过
     * @return 是否成功
     */
    public boolean approveRequest(Long pageId, String userId, boolean approved) {
        SaWikiPage page = wikiPageService.getById(pageId);
        Validator.assertNotNull(page, ErrCodeSys.PA_DATA_NOT_EXIST, "页面");

        String currentUserId = AccountContext.getOperator();
        Validator.assertEquals(currentUserId, page.getAuthorId(), ErrCodeSys.SYS_ERR_MSG, "只有作者可以审批申请");

        LambdaQueryWrapper<SaWikiCollaborator> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SaWikiCollaborator::getPageId, pageId)
                .eq(SaWikiCollaborator::getUserId, userId);

        SaWikiCollaborator collaborator = collaboratorService.getOne(wrapper);
        Validator.assertNotNull(collaborator, ErrCodeSys.SYS_ERR_MSG, "申请不存在");

        collaborator.setStatus(approved ? "1" : "2");
        collaboratorService.updateById(collaborator);

        return true;
    }

    /**
     * 移除协作者
     *
     * @param pageId 页面ID
     * @param userId 用户ID
     * @return 是否成功
     */
    public boolean removeCollaborator(Long pageId, String userId) {
        SaWikiPage page = wikiPageService.getById(pageId);
        Validator.assertNotNull(page, ErrCodeSys.PA_DATA_NOT_EXIST, "页面");

        String currentUserId = AccountContext.getOperator();
        Validator.assertEquals(currentUserId, page.getAuthorId(), ErrCodeSys.SYS_ERR_MSG, "只有作者可以移除协作者");

        LambdaQueryWrapper<SaWikiCollaborator> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SaWikiCollaborator::getPageId, pageId)
                .eq(SaWikiCollaborator::getUserId, userId);

        return collaboratorService.remove(wrapper);
    }

    /**
     * 更新协作者权限
     *
     * @param pageId     页面ID
     * @param userId     用户ID
     * @param permission 新权限
     * @return 是否成功
     */
    public boolean updatePermission(Long pageId, String userId, String permission) {
        SaWikiPage page = wikiPageService.getById(pageId);
        Validator.assertNotNull(page, ErrCodeSys.PA_DATA_NOT_EXIST, "页面");

        String currentUserId = AccountContext.getOperator();
        Validator.assertEquals(currentUserId, page.getAuthorId(), ErrCodeSys.SYS_ERR_MSG, "只有作者可以修改协作者权限");

        LambdaQueryWrapper<SaWikiCollaborator> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SaWikiCollaborator::getPageId, pageId)
                .eq(SaWikiCollaborator::getUserId, userId);

        SaWikiCollaborator collaborator = collaboratorService.getOne(wrapper);
        Validator.assertNotNull(collaborator, ErrCodeSys.SYS_ERR_MSG, "协作者不存在");

        collaborator.setPermission(permission);
        collaboratorService.updateById(collaborator);

        return true;
    }

    /**
     * 获取当前用户的所有协作页面
     *
     * @return 协作页面列表
     */
    public List<OssWikiCollaboratorVO> getMyCollaborations() {
        String currentUserId = AccountContext.getOperator();

        LambdaQueryWrapper<SaWikiCollaborator> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SaWikiCollaborator::getUserId, currentUserId)
                .eq(SaWikiCollaborator::getStatus, "1")
                .orderByDesc(SaWikiCollaborator::getCreateAt);

        return Resp.convert(collaboratorService.list(wrapper).stream()
                .map(this::convertToVO)
                .collect(Collectors.toList()), OssWikiCollaboratorVO.class);
    }

    /**
     * 转换为VO
     */
    private OssWikiCollaboratorVO convertToVO(SaWikiCollaborator collaborator) {
        OssWikiCollaboratorVO vo = new OssWikiCollaboratorVO();
        BeanUtils.copyProperties(collaborator, vo);
        return vo;
    }

    public OssWikiPageShareVO getPermissionKey(Long pageId, String permission, String password, Integer expiredTime) {
        SaWikiPage page = wikiPageService.getById(pageId);
        Validator.assertNotNull(page, ErrCodeSys.PA_DATA_NOT_EXIST, "页面");
        String currentUserId = AccountContext.getOperator();
        Validator.assertEquals(currentUserId, page.getAuthorId(), ErrCodeSys.SYS_ERR_MSG, "只有作者可以申请分享链接");
        String key = SHARE_CODE_KEY + RandomUtil.getStringWithNumber(12);
        SharePermission share = new SharePermission(pageId, password, permission);
        redisService.set(key, expiredTime, share);
        OssWikiPageShareVO vo = new OssWikiPageShareVO();
        vo.setShareCode(Base64Util.encode(key));
        vo.setExpiredTime(DateUtil.addSeconds(new Date(), expiredTime));
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public void parsePermission(String shareCode, String password) {
        String key = Base64Util.decode(shareCode);
        Validator.assertNotBlank(key, ErrCodeSys.SYS_ERR_MSG, "不是有效的分享码");
        Validator.assertTrue(key.startsWith(SHARE_CODE_KEY), ErrCodeSys.SYS_ERR_MSG, "不是有效的分享码");
        SharePermission share = redisService.get(key, SharePermission.class);
        Validator.assertNotNull(share, ErrCodeSys.SYS_ERR_MSG, "分享码已失效");
        Validator.assertEquals(share.getPassword(), password, ErrCodeSys.SYS_ERR_MSG, "密码错误");
        SaWikiCollaborator collaborator = requestAccess(share.getPageId(), share.getPermission(), "通过分享码");
        collaborator.setStatus(CommonConst.YES);
        collaboratorService.updateById(collaborator);
        redisService.delete(key);
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SharePermission {
        private Long pageId;
        private String password;
        private String permission;
    }
}
