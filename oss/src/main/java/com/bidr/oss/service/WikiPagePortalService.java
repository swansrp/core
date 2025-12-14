package com.bidr.oss.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.StringUtil;
import com.bidr.kernel.vo.common.IdOrderReqVO;
import com.bidr.oss.dao.entity.SaWikiCollaborator;
import com.bidr.oss.dao.entity.SaWikiPage;
import com.bidr.oss.dao.repository.SaWikiCollaboratorService;
import com.bidr.oss.dao.repository.SaWikiPageService;
import com.bidr.oss.vo.OssWikiPageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Wiki页面Portal Service
 *
 * @author sharp
 * @since 2025-12-12
 */
@Service
@RequiredArgsConstructor
public class WikiPagePortalService {

    private final SaWikiPageService wikiPageService;
    private final SaWikiCollaboratorService collaboratorService;
    private final TokenService tokenService;

    /**
     * 获取页面树形结构
     *
     * @param keyword 搜索关键词
     * @return 树形节点列表
     */
    public List<OssWikiPageVO> getPageTree(String keyword, String currentUserId) {
        return wikiPageService.get(currentUserId, keyword);
    }

    /**
     * 获取页面详情
     *
     * @param id 页面ID
     * @return 页面VO
     */
    public OssWikiPageVO getPageDetail(Long id, String currentUserId) {
        SaWikiPage page = wikiPageService.getById(id);
        if (page == null || !"1".equals(page.getValid())) {
            return null;
        }
        if (FuncUtil.isEmpty(currentUserId)) {
            return convertToVO(page, currentUserId);
        }

        // 检查访问权限
        boolean canAccess = "1".equals(page.getIsPublic())
                || currentUserId.equals(page.getAuthorId())
                || hasCollaboratorAccess(id, currentUserId);

        if (!canAccess) {
            return null;
        }

        // 增加浏览次数
        page.setViewCount(page.getViewCount() + 1);
        wikiPageService.updateById(page);

        return convertToVO(page, currentUserId);
    }

    /**
     * 保存页面
     *
     * @param vo 页面VO
     * @return 保存后的页面VO
     */
    public OssWikiPageVO savePage(OssWikiPageVO vo) {
        String currentUserId = AccountContext.getOperator();

        SaWikiPage page;
        if (vo.getId() == null) {
            Long count = wikiPageService.countByPid(vo.getParentId());
            // 新增
            page = new SaWikiPage();
            page.setAuthorId(currentUserId);
            // 已发布
            page.setStatus("2");
            // 默认公开
            page.setIsPublic("1");
            page.setViewCount(0L);
            page.setSortOrder(count.intValue() + 1);
            page.setVersion(1);
            page.setValid(CommonConst.YES);
        } else {
            // 更新
            page = wikiPageService.getById(vo.getId());
            if (page == null) {
                throw new RuntimeException("页面不存在");
            }

            // 检查编辑权限
            boolean canEdit = currentUserId.equals(page.getAuthorId())
                    || hasEditPermission(vo.getId(), currentUserId);
            if (!canEdit) {
                throw new RuntimeException("无权编辑此页面");
            }
            page.setSortOrder(vo.getSortOrder() != null ? vo.getSortOrder() : 0);
            page.setVersion(page.getVersion() + 1);
        }

        page.setTitle(vo.getTitle());
        page.setContent(vo.getContent());
        page.setContentHtml(vo.getContentHtml());
        page.setParentId(vo.getParentId());

        if (vo.getIsPublic() != null) {
            page.setIsPublic(vo.getIsPublic());
        }
        page.setModifyAt(new Date());
        wikiPageService.saveOrUpdate(page);

        return convertToVO(page, currentUserId);
    }

    /**
     * 删除页面
     *
     * @param id 页面ID
     * @return 是否成功
     */
    public boolean deletePage(Long id) {
        SaWikiPage page = wikiPageService.getById(id);
        if (page == null) {
            return false;
        }

        String currentUserId = AccountContext.getOperator();
        if (!currentUserId.equals(page.getAuthorId())) {
            throw new RuntimeException("只有作者可以删除页面");
        }

        // 递归删除子页面
        deleteChildren(id);

        // 删除当前页面
        page.setValid("0");
        wikiPageService.updateById(page);

        // 删除协作者关系
        LambdaQueryWrapper<SaWikiCollaborator> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SaWikiCollaborator::getPageId, id);
        collaboratorService.remove(wrapper);

        return true;
    }

    /**
     * 更新页面排序
     *
     * @param idOrderList 参数
     */
    public void updateSort(List<IdOrderReqVO> idOrderList) {
        if (FuncUtil.isNotEmpty(idOrderList)) {
            List<SaWikiPage> pageList = new ArrayList<>();
            for (IdOrderReqVO vo : idOrderList) {
                SaWikiPage page = new SaWikiPage();
                page.setId(Long.parseLong(StringUtil.parse(vo.getId())));
                page.setSortOrder(vo.getShowOrder());
                pageList.add(page);
            }
            wikiPageService.updateById(pageList);
        }
    }

    /**
     * 搜索页面
     *
     * @param keyword 关键词
     * @return 页面列表
     */
    public List<OssWikiPageVO> searchPages(String keyword, String currentUserId) {
        return wikiPageService.get(currentUserId, keyword);
    }

    /**
     * 检查是否有协作者访问权限
     */
    private boolean hasCollaboratorAccess(Long pageId, String userId) {
        LambdaQueryWrapper<SaWikiCollaborator> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SaWikiCollaborator::getPageId, pageId)
                .eq(SaWikiCollaborator::getUserId, userId)
                .eq(SaWikiCollaborator::getStatus, CommonConst.YES);
        return collaboratorService.count(wrapper) > 0;
    }

    /**
     * 检查是否有编辑权限
     */
    private boolean hasEditPermission(Long pageId, String userId) {
        LambdaQueryWrapper<SaWikiCollaborator> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SaWikiCollaborator::getPageId, pageId)
                .eq(SaWikiCollaborator::getUserId, userId)
                // 编辑权限
                .eq(SaWikiCollaborator::getPermission, "2")
                .eq(SaWikiCollaborator::getStatus, CommonConst.YES);
        return collaboratorService.count(wrapper) > 0;
    }

    /**
     * 递归删除子页面
     */
    private void deleteChildren(Long parentId) {
        LambdaQueryWrapper<SaWikiPage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SaWikiPage::getParentId, parentId)
                .eq(SaWikiPage::getValid, "1");

        List<SaWikiPage> children = wikiPageService.list(wrapper);
        for (SaWikiPage child : children) {
            deleteChildren(child.getId());
            child.setValid("0");
            wikiPageService.updateById(child);
        }
    }

    /**
     * 转换为VO
     */
    private OssWikiPageVO convertToVO(SaWikiPage page, String operator) {
        OssWikiPageVO vo = Resp.convert(page, OssWikiPageVO.class);
        // 设置权限信息
        vo.setIsAuthor(page.getAuthorId().equals(operator));
        vo.setCanEdit(vo.getIsAuthor() || (FuncUtil.isNotEmpty(operator) && hasEditPermission(page.getId(), operator)));
        return vo;
    }

    public void setPid(Long id, Long parentId) {
        SaWikiPage page = wikiPageService.getById(id);
        page.setParentId(parentId);
        wikiPageService.updateById(page, false);
    }
}
