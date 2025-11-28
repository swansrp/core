package com.bidr.kernel.mybatis.dao.schema;

import com.bidr.kernel.mybatis.dao.entity.SaSequence;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * 序列表Schema
 *
 * @author Sharp
 * @since 2025-11-28
 */
@Service
public class SaSequenceSchema extends BaseMybatisSchema<SaSequence> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `sa_sequence` (\n" +
                "  `seq_name` varchar(128) NOT NULL COMMENT '序列名称',\n" +
                "  `platform` varchar(50) NOT NULL COMMENT '所属平台',\n" +
                "  `value` int NOT NULL COMMENT '目前序列值',\n" +
                "  `prefix` varchar(10) NOT NULL DEFAULT '' COMMENT '序列前缀',\n" +
                "  `suffix` varchar(10) NOT NULL DEFAULT '' COMMENT '序列后缀',\n" +
                "  `min_value` int NOT NULL COMMENT '最小值',\n" +
                "  `max_value` int NOT NULL COMMENT '最大值',\n" +
                "  `step` int NOT NULL DEFAULT '1' COMMENT '每次取值的数量',\n" +
                "  `create_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                "  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',\n" +
                "  `update_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',\n" +
                "  `update_by` varchar(50) DEFAULT NULL COMMENT '修改人',\n" +
                "  PRIMARY KEY (`seq_name`)\n" +
                ") ENGINE=InnoDB COMMENT='队列表'\n"
        );
        setInitData("INSERT INTO `sa_sequence` (`seq_name`, `platform`, `value`, `prefix`, `suffix`, `min_value`, `max_value`, `step`) VALUES\n" +
                "\t('AC_DEPT_ID_SEQ', 'sys', 10000000, '', '', 10000000, 99999999, 1),\n" +
                "\t('AC_USER_CUSTOMER_NUMBER_SEQ', 'sys', 10000000, '', '', 10000000, 99999999, 1)\n");
    }
}
