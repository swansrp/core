package com.bidr.oss.dao.repository;

import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.oss.dao.entity.SaWikiCollaborator;
import com.bidr.oss.dao.entity.SaWikiPage;
import com.bidr.oss.dao.mapper.SaWikiPageMapper;
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
        wrapper.selectAs(AcUser::getName, OssWikiPageVO::getAuthorName);
        wrapper.selectAs(SaWikiPage::getModifyAt, OssWikiPageVO::getModifyAt);
        wrapper.leftJoin(SaWikiCollaborator.class, SaWikiCollaborator::getPageId, SaWikiPage::getId);
        wrapper.leftJoin(AcUser.class, AcUser::getCustomerNumber, SaWikiPage::getAuthorId);
        wrapper.and(FuncUtil.isNotEmpty(searchName), w -> w.like(SaWikiPage::getTitle, searchName)
                .or()
                .like(SaWikiPage::getContentHtml, searchName));

        wrapper.and(w->w.eq(SaWikiPage::getIsPublic, CommonConst.YES).or().eq(SaWikiPage::getAuthorId, operator)
                .or(r -> r.eq(SaWikiCollaborator::getUserId, operator).eq(SaWikiCollaborator::getStatus, CommonConst.YES)));
        wrapper.orderByAsc(SaWikiPage::getSortOrder);
        return super.selectJoinList(OssWikiPageVO.class, wrapper);
    }
}
