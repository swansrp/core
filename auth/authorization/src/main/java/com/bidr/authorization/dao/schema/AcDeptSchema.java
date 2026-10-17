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
                "  `dept_id` varchar(20) NOT NULL COMMENT '部门id',\n" +
                "  `pid` varchar(20) DEFAULT NULL COMMENT '父部门id',\n" +
                "  `grand_id` varchar(20) DEFAULT NULL COMMENT '祖父id',\n" +
                "  `ancestors` varchar(50) DEFAULT '' COMMENT '祖级列表',\n" +
                "  `name` varchar(30) DEFAULT '' COMMENT '部门名称',\n" +
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
    }
}
