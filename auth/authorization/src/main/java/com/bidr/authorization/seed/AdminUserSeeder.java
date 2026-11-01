package com.bidr.authorization.seed;

import com.bidr.authorization.config.AdminProperties;
import com.bidr.authorization.constants.AuthConstants;
import com.bidr.authorization.constants.dict.UserTypeDict;
import com.bidr.authorization.constants.param.AccountParam;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.entity.AcUserRole;
import com.bidr.authorization.dao.repository.AcRoleService;
import com.bidr.authorization.dao.repository.AcUserService;
import com.bidr.authorization.dao.repository.AcUserRoleService;
import com.bidr.authorization.dao.schema.AcUserSchema;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.constant.dict.common.ActiveStatusDict;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.Md5Util;
import com.bidr.platform.service.cache.SysConfigCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Date;
import java.util.List;

/**
 * Title: AdminUserSeeder
 * Description: 平台超管账号种子器（T1.0 空库引导·棒1）——三件事：账号在位、密码策略、超管角色绑定。
 * 排在 TenantSeeder 之后（种子链第二棒）。
 *
 * <p>① 账号在位：identity.admin.username 有值 → 按 user_name 找、缺则建（user_type='00'，
 * 字段形态照 {@code CreateUserServiceImpl#buildBaseUser} 既有写法，<b>绝不绑 ac_user_tenant</b>
 * ——平台超管是全局账号，租户归属留给棒3 的 TenantResolver 身份创建点语义）；
 * username 未配置 → 操作 Schema 种子行（user_id=1，M-1：AcUserSchema.setInitData 清库后该行必然存在）。</p>
 *
 * <p>② 密码策略（pristine 检测，只对"从未被人改过"的密码生效）：</p>
 * <ul>
 *   <li>pristine ＝ 密码 hash 等于工厂态常量 {@link AcUserSchema#FACTORY_ADMIN_PASSWORD_HASH}
 *       或账号为本 seeder 本次新建；</li>
 *   <li>pristine 且 identity.admin.initial-password 有值 → {@code Md5Util.generate(配置明文)} 落库；</li>
 *   <li>pristine 且未配置 → 随机 16 位落库，log.warn 打印一次明文（仅此一次，日志即凭据）；</li>
 *   <li>非 pristine（人工已改）→ 一律不覆盖，log.info 说明。</li>
 * </ul>
 *
 * <p>③ 超管角色绑定：sys_config ACCOUNT_ADMIN_ROLE_ID 值 &gt; 0 且 ac_user_role 缺该
 * (user_id, role_id) 行时补绑（幂等）；取值路径照 PermitService 现成形态（SysConfigCacheService）；
 * 参数缺席/≤0/角色行不存在（角色由 AcRoleManager 体系种）→ 跳过并 log.warn，不拒启动
 * ——本步是补绑尽力而为，启动硬闸归 MenuSeeder（M-4）。</p>
 *
 * <p>⚠ 本类 DB 写路径不在离线单测覆盖（离线腿 mock 仓库层断言三态分支）。</p>
 *
 * @author Sharp
 * @since 2026-11-01
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 2)
@RequiredArgsConstructor
public class AdminUserSeeder implements ApplicationRunner {

    /** 随机初始密码长度（log.warn 一次性打印明文） */
    static final int RANDOM_PASSWORD_LENGTH = 16;
    private static final String PASSWORD_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AdminProperties adminProperties;
    private final AcUserService acUserService;
    private final AcUserRoleService acUserRoleService;
    private final AcRoleService acRoleService;
    private final SysConfigCacheService sysConfigCacheService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) throws Exception {
        boolean[] newlyCreated = {false};
        AcUser user = ensureAdminAccount(newlyCreated);
        if (user == null) {
            return;
        }
        applyPasswordPolicy(user, newlyCreated[0]);
        bindAdminRole(user);
    }

    /** ① 账号在位。username 未配置走 Schema 种子行 user_id=1（M-1）；有值按 user_name 找、缺则建。 */
    private AcUser ensureAdminAccount(boolean[] newlyCreated) {
        String username = adminProperties.getUsername();
        if (FuncUtil.isEmpty(username)) {
            AcUser seed = acUserService.selectById(1L);
            if (seed == null) {
                log.warn("[admin-seed] identity.admin.username 未配置且 ac_user 无 user_id=1 种子行"
                        + "（表未建/行被清？）——跳过管理员种子");
                return null;
            }
            log.info("[admin-seed] identity.admin.username 未配置——操作 Schema 种子行 user_id=1（user_name={}）",
                    seed.getUserName());
            return seed;
        }
        AcUser existing = acUserService.getUserByUserName(username);
        if (existing != null) {
            log.info("[admin-seed] 管理员已在位 user_name={} user_id={}——幂等跳过建号",
                    username, existing.getUserId());
            return existing;
        }
        AcUser user = buildBaseUser();
        user.setUserName(username);
        user.setName(username);
        user.setNickName(username);
        user.setUserType(UserTypeDict.SYSTEM.getValue());
        acUserService.insert(user);
        newlyCreated[0] = true;
        log.info("[admin-seed] 新建平台超管账号 user_name={} user_id={}（user_type='00'，不绑 ac_user_tenant）",
                username, user.getUserId());
        return user;
    }

    /**
     * ② 密码策略（pristine 检测）。新建号必落策略；存量号仅工厂态 hash 才动，人工改过一律不覆盖。
     */
    private void applyPasswordPolicy(AcUser user, boolean newlyCreated) {
        boolean pristine = newlyCreated || FuncUtil.equals(user.getPassword(), AcUserSchema.FACTORY_ADMIN_PASSWORD_HASH);
        if (!pristine) {
            log.info("[admin-seed] 管理员密码非工厂态（人工已改）user_id={}——不覆盖",
                    user.getUserId());
            return;
        }
        String initial = adminProperties.getInitialPassword();
        if (FuncUtil.isNotEmpty(initial)) {
            // 🔴 平台密码惯例＝双 md5（登录侧提交 md5(明文)，存侧 generate(md5(明文))）：
            // 同款见 PasswordServiceImpl 与 AdminUserServiceImpl；单层 generate 与登录链 verify 不匹配（清库演练 2026-11-01 实证）。
            user.setPassword(Md5Util.generate(Md5Util.MD5(initial)));
            log.info("[admin-seed] 已按 identity.admin.initial-password 落管理员密码 user_id={}", user.getUserId());
        } else {
            String plain = randomPlain(RANDOM_PASSWORD_LENGTH);
            user.setPassword(Md5Util.generate(Md5Util.MD5(plain)));
            log.warn("[admin-seed] identity.admin.initial-password 未配置——已为管理员随机生成密码，"
                    + "明文仅此一次打印（请立即取用并修改）user_id={} password={}", user.getUserId(), plain);
        }
        // 带密码落库＝已设过密码：passwordLastTime 置 now（CreateUserServiceImpl.createUser 同姿势），
        // 防 PASSWORD_EXPIRED 把首登判成过期；未设密码的 epoch(0) 标记语义只属无密码建号链。
        user.setPasswordLastTime(new Date());
        if (newlyCreated) {
            return; // 新建号随 insert 一起落库，不再补一次 update
        }
        acUserService.updateById(user);
    }

    /**
     * ③ 超管角色绑定（幂等补绑）。参数缺席/≤0/角色行不存在 → 跳过并 warn，不拒启动（硬闸在 MenuSeeder）。
     */
    private void bindAdminRole(AcUser user) {
        Long adminRoleId;
        try {
            adminRoleId = sysConfigCacheService.getParamLong(AccountParam.ACCOUNT_ADMIN_ROLE_ID);
        } catch (Exception e) {
            log.warn("[admin-seed] sys_config 无有效 ACCOUNT_ADMIN_ROLE_ID（{}）——跳过超管角色绑定", e.getMessage());
            return;
        }
        if (adminRoleId == null || adminRoleId <= 0L) {
            log.warn("[admin-seed] ACCOUNT_ADMIN_ROLE_ID 缺省或 ≤0（{}）——跳过超管角色绑定", adminRoleId);
            return;
        }
        if (!acRoleService.existedById(adminRoleId)) {
            log.warn("[admin-seed] 角色 role_id={} 不存在（由 AcRoleManager 体系种）——跳过超管角色绑定", adminRoleId);
            return;
        }
        List<Long> roleIds = acUserRoleService.getRoleIdsByUserId(user.getUserId());
        if (roleIds != null && roleIds.contains(adminRoleId)) {
            return; // 幂等：绑定已在位
        }
        AcUserRole binding = new AcUserRole();
        binding.setUserId(user.getUserId());
        binding.setRoleId(adminRoleId);
        acUserRoleService.insert(binding);
        log.info("[admin-seed] 已补绑超管角色 user_id={} role_id={}", user.getUserId(), adminRoleId);
    }

    /**
     * 平台超管建号字段形态：照 {@code CreateUserServiceImpl#buildBaseUser} 既有写法
     * （status=ACTIVATE / passwordErrorTime=0 / passwordLastTime=epoch(0) / valid='1'）；
     * customerNumber 不手工赋值——走 AcUserService.insert 的 CustomerNumberHandler/AutoInsert 既有机制。
     * 🔴 不绑 ac_user_tenant：平台超管是全局账号。
     */
    private AcUser buildBaseUser() {
        AcUser user = new AcUser();
        user.setStatus(ActiveStatusDict.ACTIVATE.getValue());
        user.setPasswordErrorTime(0);
        user.setPasswordLastTime(new Date(0L));
        user.setValid(CommonConst.YES);
        user.setUserType(UserTypeDict.SYSTEM.getValue());
        user.setCreateBy(AuthConstants.SYSTEM_USER);
        user.setUpdateBy(AuthConstants.SYSTEM_USER);
        return user;
    }

    /** 随机明文（去易混淆字符表，SecureRandom）——只在 initial-password 未配置的 pristine 腿用。 */
    private static String randomPlain(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(PASSWORD_ALPHABET.charAt(RANDOM.nextInt(PASSWORD_ALPHABET.length())));
        }
        return sb.toString();
    }
}
