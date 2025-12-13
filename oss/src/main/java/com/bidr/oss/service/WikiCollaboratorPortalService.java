package com.bidr.oss.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.kernel.config.response.Resp;
import com.bidr.oss.dao.entity.SaWikiCollaborator;
import com.bidr.oss.dao.entity.SaWikiPage;
import com.bidr.oss.dao.repository.SaWikiCollaboratorService;
import com.bidr.oss.dao.repository.SaWikiPageService;
import com.bidr.oss.vo.OssWikiCollaboratorVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

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

    private final SaWikiPageService wikiPageService;
    private final SaWikiCollaboratorService collaboratorService;

    /**
     * 申请编辑权限
     *
     * @param pageId     页面ID
     * @param permission 权限类型
     * @param requestMsg 申请说明
     * @return 是否成功
     */
    public boolean requestAccess(Long pageId, String permission, String requestMsg) {
        SaWikiPage page = wikiPageService.getById(pageId);
        if (page == null || !"1".equals(page.getValid())) {
            throw new RuntimeException("页面不存在");
        }

        String currentUserId = AccountContext.getOperator();

        // 不能申请自己创建的页面
        if (currentUserId.equals(page.getAuthorId())) {
            throw new RuntimeException("您是该页面的作者，无需申请权限");
        }

        // 检查是否已有申请
        LambdaQueryWrapper<SaWikiCollaborator> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SaWikiCollaborator::getPageId, pageId)
                .eq(SaWikiCollaborator::getUserId, currentUserId);

        SaWikiCollaborator existing = collaboratorService.getOne(wrapper);
        if (existing != null) {
            if ("0".equals(existing.getStatus())) {
                throw new RuntimeException("您已提交申请，请等待审批");
            } else if ("1".equals(existing.getStatus())) {
                throw new RuntimeException("您已拥有该页面的访问权限");
            }
        }

        SaWikiCollaborator collaborator = new SaWikiCollaborator();
        collaborator.setPageId(pageId);
        collaborator.setUserId(currentUserId);
        collaborator.setPermission(permission != null ? permission : "2");
        collaborator.setStatus("0"); // 待审批
        collaborator.setRequestMsg(requestMsg);

        collaboratorService.save(collaborator);
        return true;
    }

    /**
     * 获取页面的协作者列表
     *
     * @param pageId 页面ID
     * @return 协作者列表
     */
    public List<OssWikiCollaboratorVO> getCollaborators(Long pageId) {
        SaWikiPage page = wikiPageService.getById(pageId);
        if (page == null) {
            throw new RuntimeException("页面不存在");
        }

        String currentUserId = AccountContext.getOperator();
        if (!currentUserId.equals(page.getAuthorId())) {
            throw new RuntimeException("只有作者可以查看协作者列表");
        }

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
        if (page == null) {
            throw new RuntimeException("页面不存在");
        }

        String currentUserId = AccountContext.getOperator();
        if (!currentUserId.equals(page.getAuthorId())) {
            throw new RuntimeException("只有作者可以查看待审批申请");
        }

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
        if (page == null) {
            throw new RuntimeException("页面不存在");
        }

        String currentUserId = AccountContext.getOperator();
        if (!currentUserId.equals(page.getAuthorId())) {
            throw new RuntimeException("只有作者可以审批申请");
        }

        LambdaQueryWrapper<SaWikiCollaborator> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SaWikiCollaborator::getPageId, pageId)
                .eq(SaWikiCollaborator::getUserId, userId);

        SaWikiCollaborator collaborator = collaboratorService.getOne(wrapper);
        if (collaborator == null) {
            throw new RuntimeException("申请不存在");
        }

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
        if (page == null) {
            throw new RuntimeException("页面不存在");
        }

        String currentUserId = AccountContext.getOperator();
        if (!currentUserId.equals(page.getAuthorId())) {
            throw new RuntimeException("只有作者可以移除协作者");
        }

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
        if (page == null) {
            throw new RuntimeException("页面不存在");
        }

        String currentUserId = AccountContext.getOperator();
        if (!currentUserId.equals(page.getAuthorId())) {
            throw new RuntimeException("只有作者可以修改协作者权限");
        }

        LambdaQueryWrapper<SaWikiCollaborator> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SaWikiCollaborator::getPageId, pageId)
                .eq(SaWikiCollaborator::getUserId, userId);

        SaWikiCollaborator collaborator = collaboratorService.getOne(wrapper);
        if (collaborator == null) {
            throw new RuntimeException("协作者不存在");
        }

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
}
