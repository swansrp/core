package com.bidr.authorization.dao.schema;

import com.bidr.authorization.dao.entity.AcDept;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;


/**
 * Title: AcDeptSchema
 *
 * @author Sharp
 * @since 2025/11/28
 */
@Service
public class AcDeptSchema extends BaseMybatisSchema<AcDept> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `ac_dept` (\n" +
                "  `dept_id` varchar(50) NOT NULL COMMENT '部门id',\n" +
                "  `pid` varchar(50) DEFAULT NULL COMMENT '父部门id',\n" +
                "  `grand_id` varchar(50) DEFAULT NULL COMMENT '祖父id',\n" +
                "  `ancestors` varchar(200) DEFAULT '' COMMENT '祖级列表',\n" +
                "  `name` varchar(100) DEFAULT '' COMMENT '部门名称',\n" +
                "  `abbreviate` varchar(50) DEFAULT NULL COMMENT '简称',\n" +
                "  `founded_time` datetime DEFAULT NULL COMMENT '建立时间',\n" +
                "  `category` varchar(20) DEFAULT NULL COMMENT '类别',\n" +
                "  `type` varchar(20) DEFAULT NULL COMMENT '类型',\n" +
                "  `function` varchar(20) DEFAULT NULL COMMENT '职能',\n" +
                "  `leader` varchar(20) DEFAULT NULL COMMENT '负责人',\n" +
                "  `contact` varchar(11) DEFAULT NULL COMMENT '联系电话',\n" +
                "  `address` varchar(50) DEFAULT NULL COMMENT '地址',\n" +
                "  `status` int(11) DEFAULT NULL COMMENT '部门状态',\n" +
                "  `show_order` int(11) DEFAULT '0' COMMENT '显示顺序',\n" +
                "  `create_by` varchar(50) DEFAULT NULL COMMENT '创建者',\n" +
                "  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                "  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',\n" +
                "  `update_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',\n" +
                "  `valid` char(1) DEFAULT '1' COMMENT '有效性',\n" +
                "  PRIMARY KEY (`dept_id`),\n" +
                "  KEY `pid` (`pid`),\n" +
                "  KEY `grand_id` (`grand_id`)\n" +
                ") COMMENT='部门表';");
        // 🔴 2026-11-11 存量库补宽（只动组织同步实际写入、且会被飞书数据撑爆的 4 列）：
        //    · dept_id：飞书 open_department_id＝`od-`+32 hex＝35 字符，旧库 varchar(20)
        //      ——zz 33 个部门同步时就是这列 Data too long 整批回滚；
        //    · pid：存父部门 id，同一个 35 字符口径；
        //    · name：pan 库实测部门名最长 48，旧库 varchar(30)；
        //    · leader：Feishu 部门接口默认 user_id_type=open_id，leader_user_id 是 `ou_`+32
        //      ＝35 字符（旧库 20）；bidr MDM 侧给的是短姓名/工号，50 两边都够。
        //    createDDL 同步改成 50/50/100/50，新建库与存量库同形。
        //    grand_id/ancestors 当前代码**没有写入路径**（OrgSyncApplier 只写 dept_id/pid/name/
        //    leader/show_order），不随本腿改宽；将来真有写入需求再加新腿。
        setUpgradeDDL(1, "ALTER TABLE `ac_dept`\n" +
                "  MODIFY COLUMN `dept_id` varchar(50) NOT NULL COMMENT '部门id',\n" +
                "  MODIFY COLUMN `pid` varchar(50) DEFAULT NULL COMMENT '父部门id',\n" +
                "  MODIFY COLUMN `name` varchar(100) DEFAULT '' COMMENT '部门名称',\n" +
                "  MODIFY COLUMN `leader` varchar(50) DEFAULT NULL COMMENT '负责人'");
    }
}
