package com.bidr.authorization.constants.role;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.authorization.dao.entity.AcRole;
import com.bidr.authorization.dao.repository.AcRoleService;
import com.bidr.kernel.constant.dict.common.ActiveStatusDict;
import com.bidr.kernel.utils.FuncUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * 系统内置角色管理器
 * 启动时反射扫描所有实现 SystemRole 接口的枚举，按 role_key 做 upsert 同步到 ac_role 表
 * 已存在的角色（按 role_key 匹配）只更新名称/排序等字段，不更新 roleId，保留关联数据
 *
 * @author Sharp
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AcRoleManager implements CommandLineRunner {

    @Value("${my.base-package}")
    private String basePackage;

    private final AcRoleService acRoleService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(String... args) {
        Reflections reflections = new Reflections(basePackage);
        Set<Class<? extends SystemRole>> roleSet = reflections.getSubTypesOf(SystemRole.class);

        if (FuncUtil.isEmpty(roleSet)) {
            return;
        }

        for (Class<? extends SystemRole> clazz : roleSet) {
            if (!Enum.class.isAssignableFrom(clazz)) {
                continue;
            }
            for (SystemRole roleDef : clazz.getEnumConstants()) {
                upsert(roleDef);
            }
        }

        log.info("系统角色初始化完成，共扫描 {} 个枚举类", roleSet.size());
    }

    /**
     * 按 role_key 做 upsert：
     * - 存在：更新 roleName、displayOrder、remark、status（强制 SYSTEM）
     * - 不存在：插入新记录
     */
    private void upsert(SystemRole roleDef) {
        String roleKey = roleDef.roleKey();

        LambdaQueryWrapper<AcRole> wrapper = new LambdaQueryWrapper<AcRole>()
                .eq(AcRole::getRoleKey, roleKey);
        AcRole existing = acRoleService.getOne(wrapper);

        if (existing != null) {
            // 已存在：更新字段，保留 roleId
            existing.setRoleName(roleDef.roleName());
            existing.setDisplayOrder(roleDef.displayOrder());
            existing.setRemark(roleDef.remark());
            existing.setStatus(ActiveStatusDict.SYSTEM.getValue());
            acRoleService.updateById(existing);
            log.debug("更新系统角色: {} ({})", roleDef.roleName(), roleKey);
        } else {
            // 不存在：插入
            AcRole role = new AcRole();
            role.setRoleKey(roleKey);
            role.setRoleName(roleDef.roleName());
            role.setDisplayOrder(roleDef.displayOrder());
            role.setRemark(roleDef.remark());
            role.setStatus(ActiveStatusDict.SYSTEM.getValue());
            acRoleService.insert(role);
            log.info("新建系统角色: {} ({})", roleDef.roleName(), roleKey);
        }
    }
}
