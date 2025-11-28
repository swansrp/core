package com.bidr.kernel.mybatis.dao.schema;


import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * @author Sharp
 * @since 2025/11/28 10:29
 */
@Service
public class NextValSchema extends BaseMybatisSchema<Object> {
    static {
        // 先删除旧函数，再创建新函数（分两步执行）
        setCreateDDL("DROP FUNCTION IF EXISTS `f_nextval`");
        
        // 创建函数（移除 DELIMITER 命令）
        setUpgradeDDL(1, 
                "CREATE FUNCTION `f_nextval`(`SEQ_NAME` VARCHAR(128)) RETURNS varchar(50) CHARSET utf8mb4 " +
                "    SQL SECURITY INVOKER " +
                "    COMMENT '获取流水号' " +
                "BEGIN " +
                "  declare exsited int default 0; " +
                "  declare cur int default 0; " +
                "  declare next int default 0; " +
                "  declare _min int default 0; " +
                "  declare _max int default 0; " +
                "  declare _step int default 0; " +
                "  declare _prefix varchar(20) default ''; " +
                "  declare _suffix varchar(20) default ''; " +
                "  declare result varchar(50) default ''; " +
                "  select count(1) into exsited from sa_sequence seq where seq.seq_name = SEQ_NAME; " +
                "  CASE " +
                "    when exsited = 0 " +
                "      then set result = ''; " +
                "    ELSE " +
                "      select value, prefix, suffix, min_value, max_value, step into cur,_prefix,_suffix,_min,_max,_step " +
                "      from sa_sequence seq " +
                "      where seq.seq_name = SEQ_NAME FOR UPDATE; " +
                "      if cur < _min then " +
                "        set cur = _min; " +
                "      end if; " +
                "      if cur > _max then " +
                "        set cur = _min; " +
                "      end if; " +
                "      set next = cur + _step; " +
                "      if next > _max then " +
                "        set next = _min; " +
                "      end if; " +
                "      if _prefix = 'now' then " +
                "        set _prefix = date_format(now(), '%Y%m%d%H%i%s'); " +
                "      end if; " +
                "      UPDATE sa_sequence seq SET seq.value=next WHERE seq.seq_name = SEQ_NAME; " +
                "      SELECT CONCAT(_prefix, cur, _suffix) into result; " +
                "    END CASE; " +
                "  return (result); " +
                "END"
        );
    }

    @Override
    public String getTableName() {
        return "f_nextval";
    }
}
