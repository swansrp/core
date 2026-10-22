package com.bidr.authorization.service.menu;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.authorization.constants.dict.MenuTypeDict;
import com.bidr.authorization.constants.param.AccountParam;
import com.bidr.authorization.dao.entity.AcMenu;
import com.bidr.authorization.dao.entity.AcRoleMenu;
import com.bidr.authorization.dao.repository.AcMenuService;
import com.bidr.authorization.dao.repository.AcRoleMenuService;
import com.bidr.authorization.service.menu.MenuDeclaration.Node;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.platform.service.cache.SysConfigCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Title: MenuSeeder
 * Description: 菜单声明种子器——启动期把 classpath 全部 menu-seed.yml 声明的嵌套树幂等种进 ac_menu。
 *
 * 时序（为何 ApplicationRunner 即可，无需额外等待）：
 * ac_menu 建表走 MybatisPlusConfig.initTables()（@PostConstruct，容器刷新期完成），
 * ApplicationRunner.run() 在容器刷新**之后**才由 SpringApplication 调用——建表先于种子天然成立。
 *
 * 幂等（重启零重复）：每节点先查库判重，命中即复用、只对自己**新插入**的行绑 admin 角色：
 * - 顶级 topnav：pid IS NULL ∧ menu_type=0 ∧ (title∨path)，查到复用 id、没有先创建；
 * - 导航直挂子行：pid IS NULL ∧ grand_id=导航id ∧ path（库既有行形态如此）；
 * - 下钻行：pid=父id ∧ grand_id=继承祖父 ∧ path；
 * - 叶子自然键 component 只插不覆盖——库里既有行与声明不一致只告警，改行走菜单管理页。
 * 插入行形态照库基线：menu_type / visible='1' / status='1' / client_type='0'
 * （生效值是 '1'，与列注释"0 显示"相反）。
 *
 * 跨模块分布：声明按页面宿主模块拆成多份（auth/platform/redis/forge/admin/agent-console/insight＋业务），
 * 他人子树只留同名 path 骨架，先 pruneAnchorSkeletons（宿主缺席整支剪除＋告警）再 validate——
 * 薄装机形态不因缺模块炸启动，全量装机形态剪枝零命中（回归针见 MenuDeclarationTest）。
 *
 * ⚠ 本类的 DB 写路径（insert/updateById/ac_role_menu 绑定）不在离线单测覆盖（交真机腿：
 * 起服务后跑一次，重启第二次应零新增）；离线腿覆盖的是 MenuDeclaration 解析/归并/校验纯函数。
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-10-17
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MenuSeeder implements ApplicationRunner {

    /** 审计备注：方便日后按 remark 反查播种来源（menu-audit 对账闸同值） */
    static final String REMARK = "menu-seed 播种";
    static final String SYSTEM_USER = "000001";
    static final String DEFAULT_ICON = "SettingOutlined";

    private final AcMenuService acMenuService;
    private final AcRoleMenuService acRoleMenuService;
    private final SysConfigCacheService sysConfigCacheService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) throws Exception {
        List<Node> roots = MenuDeclaration.parseAll(getClass().getClassLoader());
        if (roots.isEmpty()) {
            return;
        }
        for (Node dangling : MenuDeclaration.pruneAnchorSkeletons(roots)) {
            log.warn("[menu-seed] 锚点骨架无宿主声明（模块缺席？），整支跳过：{}", dangling);
        }
        MenuDeclaration.validate(roots);
        Long adminRoleId = sysConfigCacheService.getParamLong(AccountParam.ACCOUNT_ADMIN_ROLE_ID);
        // R-D D-4：硬拒——缺省或 ≤0 时插了菜单也绑不上角色，
        // 半截播种比不播种更糟（命中即跳过会让下次重启继续跳过，永远缺绑定）。
        if (adminRoleId == null || adminRoleId <= 0L) {
            throw new IllegalStateException("[menu-seed] sys_config 里没有有效的 ACCOUNT_ADMIN_ROLE_ID"
                    + "（缺省或 ≤0）——拒绝播种（框架 addMenu 也靠它绑角色，请先确认这张库）");
        }
        int inserted = 0;
        for (Node nav : roots) {
            inserted += seedTopNav(nav, adminRoleId);
        }
        log.info("[menu-seed] 声明树 {} 棵、节点 {} 个，本次新插入 {} 行（命中即跳过，重启零重复）",
                roots.size(), MenuDeclaration.flatten(roots).size(), inserted);
    }

    /** 顶级 topnav：判据唯一一次查库＝pid IS NULL ∧ menu_type=0 ∧ (title∨path)。 */
    private int seedTopNav(Node nav, Long adminRoleId) {
        LambdaQueryWrapper<AcMenu> wrapper = new LambdaQueryWrapper<AcMenu>()
                .isNull(AcMenu::getPid)
                .eq(AcMenu::getMenuType, MenuTypeDict.MENU.getValue())
                .and(q -> q.eq(AcMenu::getTitle, nav.title).or().eq(AcMenu::getPath, nav.path));
        List<AcMenu> found = acMenuService.select(wrapper);
        int inserted = 0;
        Long navId;
        if (found.isEmpty()) {
            AcMenu row = insertRow(nav, null, null, MenuTypeDict.MENU.getValue(), adminRoleId);
            navId = row.getMenuId();
            inserted++;
        } else {
            if (found.size() > 1) {
                throw new IllegalStateException("[menu-seed] topnav 判据命中多行（数据已损坏，请人工核对 ac_menu）："
                        + nav.title + "/" + nav.path + " 命中 " + found.size() + " 行");
            }
            navId = found.get(0).getMenuId();
            warnIfComponentMismatch(found.get(0), nav);
        }
        inserted += seedChildren(nav.children, null, navId, adminRoleId);
        return inserted;
    }

    /**
     * 逐层下钻：parentId=null 时＝导航直挂子行（pid IS NULL ∧ grand_id=父导航id）；
     * 否则 pid=父id ∧ grand_id=继承的祖父id ∧ path 判重。
     */
    private int seedChildren(List<Node> children, Long parentId, Long grandId, Long adminRoleId) {
        int inserted = 0;
        for (Node child : children) {
            LambdaQueryWrapper<AcMenu> wrapper = new LambdaQueryWrapper<AcMenu>()
                    .eq(AcMenu::getGrandId, grandId)
                    .eq(AcMenu::getPath, child.path);
            if (parentId == null) {
                wrapper.isNull(AcMenu::getPid);
            } else {
                wrapper.eq(AcMenu::getPid, parentId);
            }
            List<AcMenu> found = acMenuService.select(wrapper);
            Long rowId;
            Long rowGrandId;
            if (found.isEmpty()) {
                AcMenu row = insertRow(child, parentId, grandId, MenuTypeDict.SUB_MENU.getValue(), adminRoleId);
                rowId = row.getMenuId();
                rowGrandId = grandId;
                inserted++;
            } else {
                if (found.size() > 1) {
                    throw new IllegalStateException("[menu-seed] 下钻判重命中多行（请人工核对 ac_menu）："
                            + child + " 父=" + parentId + " 祖父=" + grandId + " 命中 " + found.size() + " 行");
                }
                AcMenu row = found.get(0);
                rowId = row.getMenuId();
                rowGrandId = row.getGrandId() == null ? grandId : row.getGrandId();
                warnIfComponentMismatch(row, child);
            }
            inserted += seedChildren(child.children, rowId, rowGrandId, adminRoleId);
        }
        return inserted;
    }

    /** 自然键 component 只插不覆盖：既有行与声明不一致仅告警（差异由 menu-audit 汇总）。 */
    private void warnIfComponentMismatch(AcMenu row, Node node) {
        String dbComponent = row.getComponent() == null ? "" : row.getComponent();
        String declared = node.hasComponent() ? node.component : "";
        if (!dbComponent.equals(declared)) {
            log.warn("[menu-seed] 既有行 component 与声明不一致（只插不覆盖，未改动）：menu_id={} db={} declared={}",
                    row.getMenuId(), dbComponent, declared);
        }
    }

    /**
     * 插一行并把形态钉到库基线：
     * visible/status='1'、client_type='0'、ancestors='#'、query=''、is_frame/is_cache='0'；
     * key=menu_id 回填（框架 AdminMenuService.addMenu 同姿势）；只对这一类新行绑 admin 角色。
     */
    private AcMenu insertRow(Node node, Long parentId, Long grandId, int menuType, Long adminRoleId) {
        AcMenu menu = new AcMenu();
        menu.setPid(parentId);
        menu.setGrandId(grandId);
        menu.setAncestors("#");
        menu.setClientType("0");
        menu.setTitle(node.title);
        menu.setShowOrder(parentId == null
                ? acMenuService.countByGrandId(grandId) + 1
                : acMenuService.countByPid(parentId) + 1);
        menu.setPath(node.path);
        menu.setComponent(node.hasComponent() ? node.component : "");
        menu.setQuery("");
        menu.setIsFrame("0");
        menu.setIsCache("0");
        menu.setMenuType(menuType);
        menu.setVisible(CommonConst.YES);
        menu.setStatus(CommonConst.YES);
        menu.setIcon(node.icon == null ? DEFAULT_ICON : node.icon);
        menu.setCreateBy(SYSTEM_USER);
        menu.setUpdateBy(SYSTEM_USER);
        menu.setRemark(REMARK);
        acMenuService.insert(menu);
        menu.setKey(menu.getMenuId());
        acMenuService.updateById(menu);
        acRoleMenuService.insert(new AcRoleMenu(adminRoleId, menu.getMenuId()));
        log.info("[menu-seed] 播种 menu_id={} {} pid={} grand_id={}", menu.getMenuId(), node, parentId, grandId);
        return menu;
    }
}
