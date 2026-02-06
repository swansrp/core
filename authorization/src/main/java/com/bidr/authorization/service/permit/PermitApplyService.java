package com.bidr.authorization.service.permit;

import com.bidr.authorization.dao.entity.AcMenu;
import com.bidr.authorization.dao.entity.AcPermitApply;
import com.bidr.authorization.dao.repository.AcMenuService;
import com.bidr.authorization.dao.repository.AcPermitApplyService;
import com.bidr.authorization.vo.permit.PermitApplyMenuTreeItem;
import com.bidr.authorization.vo.permit.PermitApplyMenuTreeRes;
import com.bidr.authorization.vo.permit.PermitApplyVO;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.constant.dict.common.ApprovalDict;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Title: PermitApplyService
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026/2/6 21:23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermitApplyService {

    private final AcMenuService acMenuService;
    private final AcPermitApplyService acPermitApplyService;

    /**
     * 根据完整路径查找对应的菜单ID
     *
     * @param fullPath 完整路径，如 "/SystemManage/permit/PermissionSwitch"
     * @return 对应的menu_id，若路径不合法或不存在则返回null
     */
    public Long findMenuIdByFullPath(String fullPath) {
        // 参数校验
        if (!StringUtils.hasText(fullPath)) {
            log.warn("输入路径为空");
            return null;
        }

        // 去掉前导斜杠并分割路径
        String cleanPath = fullPath.startsWith("/") ? fullPath.substring(1) : fullPath;
        if (!StringUtils.hasText(cleanPath)) {
            log.warn("路径格式不正确: {}", fullPath);
            return null;
        }

        String[] pathSegments = cleanPath.split("/");
        if (pathSegments.length == 0) {
            log.warn("路径分割后为空: {}", fullPath);
            return null;
        }

        // 逐级查找菜单
        Long currentPid = null;
        Long lastMenuId = null;

        for (int i = 0; i < pathSegments.length; i++) {
            String segment = pathSegments[i];

            // 查找当前层级的菜单
            AcMenu menu = acMenuService.findByPathAndPid(segment, currentPid);

            if (menu == null) {
                log.warn("未找到路径段对应的菜单: level={}, path={}, segment={}", i, fullPath, segment);
                return null;
            }

            // 更新当前PID为找到的菜单ID，用于下一级查找
            currentPid = menu.getMenuId();
            lastMenuId = menu.getMenuId();

            log.debug("找到菜单: level={}, menuId={}, path={}", i, menu.getMenuId(), segment);
        }

        log.info("路径解析成功: {} -> menuId={}", fullPath, lastMenuId);
        return lastMenuId;
    }

    public void applyUserPermit(String operator, String url) {
        Long menuId = findMenuIdByFullPath(url);
        if (FuncUtil.isNotEmpty(menuId)) {
            acPermitApplyService.applyUserPermit(operator, menuId);
        }
    }

    public PermitApplyVO getUserPermit(String operator, String url) {
        Long menuId = findMenuIdByFullPath(url);
        if (FuncUtil.isNotEmpty(menuId)) {
            AcPermitApply userPermitApply = acPermitApplyService.getUserPermitApply(operator, menuId);
            return Resp.convert(userPermitApply, PermitApplyVO.class);
        } else {
            return null;
        }
    }

    public List<PermitApplyMenuTreeRes> getMenuTree() {
        MPJLambdaWrapper<AcMenu> wrapper = acMenuService.getMPJLambdaWrapper();
        wrapper.selectAs(AcMenu::getMenuId, PermitApplyMenuTreeItem::getMenuId);
        wrapper.selectAs(AcMenu::getTitle, PermitApplyMenuTreeItem::getTitle);
        wrapper.selectAs(AcMenu::getPid, PermitApplyMenuTreeItem::getPid);
        wrapper.selectAs(AcMenu::getShowOrder, PermitApplyMenuTreeItem::getGrandId);
        wrapper.leftJoin(AcPermitApply.class, AcPermitApply::getMenuId, AcMenu::getMenuId);
        wrapper.selectCount(AcPermitApply::getId, PermitApplyMenuTreeItem::getWaitApproveCount);
        wrapper.eq(AcPermitApply::getStatus, ApprovalDict.APPLY.getValue());
        wrapper.orderByAsc(AcMenu::getShowOrder);
        wrapper.eq(AcMenu::getStatus, CommonConst.YES);
        List<PermitApplyMenuTreeItem> menuList = acMenuService.selectJoinList(PermitApplyMenuTreeItem.class, wrapper);
        for (PermitApplyMenuTreeItem acMenu : menuList) {
            if (FuncUtil.isEmpty(acMenu.getPid())) {
                acMenu.setPid(acMenu.getGrandId());
            }
        }
        return ReflectionUtil.buildTree(PermitApplyMenuTreeRes::setChildren, menuList, PermitApplyMenuTreeItem::getMenuId, PermitApplyMenuTreeItem::getPid);
    }

    public void approvePermit(Long id) {
    }

    public void rejectPermit(Long id) {
    }
}