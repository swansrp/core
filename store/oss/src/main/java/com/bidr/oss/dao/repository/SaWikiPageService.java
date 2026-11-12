package com.bidr.oss.dao.repository;

import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.oss.dao.entity.SaWikiCollaborator;
import com.bidr.oss.dao.entity.SaWikiPage;
import com.bidr.oss.dao.mapper.SaWikiPageMapper;
import com.bidr.kernel.config.response.RespConvert;
import com.bidr.oss.vo.OssWikiPageVO;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Wiki页面Repository Service
 *
 * @author sharp
 * @since 2025-12-12
 */
@Service
public class SaWikiPageService extends BaseSqlRepo<SaWikiPageMapper, SaWikiPage> {

    public List<OssWikiPageVO> get(String operator) {
        return get(operator, null);
    }

    public List<OssWikiPageVO> get(String operator, String searchName) {
        MPJLambdaWrapper<SaWikiPage> wrapper = super.getMPJLambdaWrapper();
        wrapper.selectAs(SaWikiPage::getId, OssWikiPageVO::getId);
        wrapper.selectAs(SaWikiPage::getParentId, OssWikiPageVO::getParentId);
        wrapper.selectAs(SaWikiPage::getTitle, OssWikiPageVO::getTitle);
        wrapper.selectAs(SaWikiPage::getMode, OssWikiPageVO::getMode);
        wrapper.selectAs(SaWikiPage::getAuthorId, OssWikiPageVO::getAuthorId);
        wrapper.selectAs(SaWikiPage::getModifyAt, OssWikiPageVO::getModifyAt);
        wrapper.leftJoin(SaWikiCollaborator.class, SaWikiCollaborator::getPageId, SaWikiPage::getId);
        wrapper.and(FuncUtil.isNotEmpty(searchName), w -> w.like(SaWikiPage::getTitle, searchName)
                .or()
                .like(SaWikiPage::getContentHtml, searchName));
        if (FuncUtil.isNotEmpty(operator)) {
            wrapper.and(w -> w.eq(SaWikiPage::getIsPublic, CommonConst.YES).or().eq(SaWikiPage::getAuthorId, operator)
                    .or(r -> r.eq(SaWikiCollaborator::getUserId, operator).eq(SaWikiCollaborator::getStatus, CommonConst.YES)));
        } else {
            wrapper.eq(SaWikiPage::getIsPublic, CommonConst.YES);
        }

        wrapper.orderByAsc(SaWikiPage::getSortOrder);
        List<OssWikiPageVO> pages = super.selectJoinList(OssWikiPageVO.class, wrapper);
        // authorName 不走 SQL JOIN（ac_user 在子系统库不存在）：@BindUser("authorId") 经 BindRepoHandler
        // SPI 兜底展开——epc 容器解析为身份镜像 idm_user_mirror（MirrorBindRepoResolver），
        // provider 容器解析为 AcUser（DefaultBindRepoResolver），行为两端等价；解析器缺席仅告警跳过
        RespConvert.customBindConvert(pages, OssWikiPageVO.class);
        return pages;
    }

    public Long countByPid(Long parentId) {
        return super.count(new MPJLambdaWrapper<SaWikiPage>()
                .eq(FuncUtil.isNotEmpty(parentId), SaWikiPage::getParentId, parentId)
                .isNull(FuncUtil.isEmpty(parentId), SaWikiPage::getParentId)
                .eq(SaWikiPage::getValid, CommonConst.YES));
    }
}
