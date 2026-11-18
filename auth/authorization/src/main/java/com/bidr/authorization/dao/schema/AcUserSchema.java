package com.bidr.authorization.dao.schema;

import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;


/**
 * Title: AcUserSchema
 *
 * @author Sharp
 * @since 2025/11/28
 */
@Service
public class AcUserSchema extends BaseMybatisSchema<AcUser> {

    /**
     * 工厂态管理员密码 hash（user_id=1 'BidrAdmin' 初始密码的 48 位盐化 MD5，Md5Util.generate 形态）。
     *
     * <p>唯一用途＝{@link #setInitData} 的种子行字面量。🔴 <b>纪律（2026-11-02 定稿，勿再长出第二个种子器）</b>：
     * 该 hash 只标识"这个号从没被人工设过密码"，<b>任何启动过程都不许据此改写口令</b>。
     * 历史教训＝T1.0 的 AdminUserSeeder 曾以"hash 等于本常量"为 pristine 判据：只要库里处于工厂态，
     * <b>下一次重启就随机重置口令并把明文打进日志</b>；联调环境里把口令故意设回工厂值后，一次部署就把
     * 在场的人全部锁在门外（2026-11-02 6.60 实锤）。该 seeder 已退役，配置项 {@code identity.admin.*}
     * 一并删除。交付需要指定初始口令时走运维 SQL 或框架既有首登设密链（{@code /web/login/password/init}）。</p>
     *
     * <p>空库引导链路（清库后自动成立，与本常量有关的部分）：{@code ac_user} 建表即插
     * user_id=1/'000001'/'BidrAdmin'/工厂 hash（本类 setInitData）→ {@code ac_role} 插角色 1/2
     * （AcRoleSchema）→ {@code ac_user_role} 插 (1,1) 角色绑定（AcUserRoleSchema）。</p>
     */
    public static final String FACTORY_ADMIN_PASSWORD_HASH = "a49a1ea1612f58a29968025b73ba2653274711c198c79465";

    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `ac_user` (\n" +
                "  `user_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '用户ID',\n" +
                "  `customer_number` varchar(50) NOT NULL COMMENT '用户编码',\n" +
                "  `wechat_id` varchar(50) DEFAULT NULL COMMENT '微信id',\n" +
                "  `id_number` varchar(50) DEFAULT NULL COMMENT '身份证id',\n" +
                "  `name` varchar(50) DEFAULT NULL COMMENT '用户姓名',\n" +
                "  `dept_id` varchar(50) DEFAULT NULL COMMENT '部门ID',\n" +
                "  `user_name` varchar(30) NOT NULL COMMENT '用户账号',\n" +
                "  `nick_name` varchar(30) DEFAULT NULL COMMENT '用户昵称',\n" +
                "  `user_type` varchar(2) DEFAULT '00' COMMENT '用户类型（00系统用户）',\n" +
                "  `email` varchar(50) DEFAULT '' COMMENT '用户邮箱',\n" +
                "  `phone_number` varchar(11) DEFAULT '' COMMENT '手机号码',\n" +
                "  `sex` char(1) DEFAULT '1' COMMENT '用户性别（1男 2女）',\n" +
                "  `avatar` varchar(500) DEFAULT '' COMMENT '头像地址',\n" +
                "  `password` varchar(100) DEFAULT '' COMMENT '密码',\n" +
                "  `password_error_time` int(11) DEFAULT NULL COMMENT '密码输入错误次数',\n" +
                "  `password_last_time` datetime DEFAULT NULL COMMENT '上次密码修改时间',\n" +
                "  `status` int(11) DEFAULT NULL COMMENT '帐号状态ACTIVE_STATUS_DICT',\n" +
                "  `login_ip` varchar(128) DEFAULT '' COMMENT '最后登录IP',\n" +
                "  `login_date` datetime DEFAULT NULL COMMENT '最后登录时间',\n" +
                "  `create_by` varchar(50) DEFAULT NULL COMMENT '创建者',\n" +
                "  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                "  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',\n" +
                "  `update_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',\n" +
                "  `remark` varchar(500) DEFAULT NULL COMMENT '备注',\n" +
                "  `valid` char(1) DEFAULT '1' COMMENT '有效性',\n" +
                "  PRIMARY KEY (`user_id`) USING BTREE,\n" +
                "  UNIQUE KEY `user_name` (`user_name`),\n" +
                "  KEY `email` (`email`),\n" +
                "  KEY `dept_id` (`dept_id`),\n" +
                "  KEY `customer_number` (`customer_number`),\n" +
                "  KEY `phonenumber` (`phone_number`) USING BTREE\n" +
                ") COMMENT='用户信息表';");

        setInitData("INSERT INTO `ac_user` (`user_id`, `customer_number`, `wechat_id`, `id_number`, `name`, `dept_id`, `user_name`, `nick_name`, `user_type`," +
                " `email`, `phone_number`, `sex`, `avatar`, `password`, `password_error_time`, `password_last_time`, `status`, `login_ip`, `login_date`, " +
                "`create_by`, `update_by`, `remark`, `valid`) VALUES\n\t(1, '000001', NULL, NULL, '系统管理员', NULL, 'BidrAdmin', NULL, '00', '', '', '1', '', " +
                "'" + FACTORY_ADMIN_PASSWORD_HASH + "', 0, NOW(), 1, '127.0.0.1', '2025-06-04 11:44:53', NULL, NULL, NULL, '1');");

        // 🔴 2026-11-11：飞书账号身份键回退 open_id 时是 35 字符（ou_…），旧库 user_name varchar(30)
        //    会插不进去；user_id（工号）一般 8 位，故平时不暴露。存量库放宽到 64（UNIQUE 索引随列宽）。
        setUpgradeDDL(1, "ALTER TABLE `ac_user` MODIFY COLUMN `user_name` varchar(64) NOT NULL COMMENT '用户账号'");
    }
}
