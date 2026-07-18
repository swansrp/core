package com.bidr.authorization.dao.schema;

import com.bidr.authorization.dao.entity.AcGroupBind;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * 用户组通用绑定关系表Schema Service
 * <p>
 * 遵循 DDL 演进规范：createDDL 冻结为最原始建表语句，
 * 后续结构变更通过 upgradeDDL 按版本号逐步追加。
 *
 * @author sharp
 * @since 2026/07/18
 */
@Service
public class AcGroupBindSchema extends BaseMybatisSchema<AcGroupBind> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `ac_group_bind` (\n" +
                "  `group_id` bigint NOT NULL COMMENT '组id',\n" +
                "  `bind_type` varchar(50) NOT NULL COMMENT '绑定类型(前端约定,如 hr_dept/area/lead_dept)',\n" +
                "  `attach_value` varchar(100) NOT NULL COMMENT '绑定目标值(字典项value或业务id)',\n" +
                "  `extra_data` text COMMENT '绑定属性JSON(如 {\"readOnly\":\"1\"})',\n" +
                "  PRIMARY KEY (`group_id`,`bind_type`,`attach_value`),\n" +
                "  KEY `idx_group_type` (`group_id`,`bind_type`)\n" +
                ") COMMENT='用户组通用绑定关系表';");
    }
}
