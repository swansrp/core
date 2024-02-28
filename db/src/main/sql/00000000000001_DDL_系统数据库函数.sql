-- 导出  函数 fuelcell.f_nextval 结构
DROP FUNCTION IF EXISTS `f_nextval`
/
-- DELIMITER //
CREATE FUNCTION `f_nextval`(`SEQ_NAME` VARCHAR(128)) RETURNS varchar(50) CHARSET utf8mb4
    SQL SECURITY INVOKER
    COMMENT '获取流水号'
BEGIN
  declare exsited int default 0;
  declare cur int default 0;
  declare next int default 0;
  declare _min int default 0;
  declare _max int default 0;
  declare _step int default 0;
  declare _prefix varchar(20) default '';
  declare _suffix varchar(20) default '';
  declare result varchar(50) default '';
  select count(1) into exsited from sa_sequence seq where seq.seq_name = SEQ_NAME;
  CASE
    when exsited = 0
      then set result = '';
    ELSE
      select value, prefix, suffix, min_value, max_value, step into cur,_prefix,_suffix,_min,_max,_step
      from sa_sequence seq
      where seq.seq_name = SEQ_NAME FOR UPDATE;
      if cur < _min then
        set cur = _min;
      end if;
      if cur > _max then
        set cur = _min;
      end if;
      set next = cur + _step;
      if next > _max then
        set next = _min;
      end if;
      if _prefix = 'now' then
        set _prefix = date_format(now(), '%Y%m%d%H%i%s');
      end if;
      UPDATE sa_sequence seq SET seq.value=next WHERE seq.seq_name = SEQ_NAME;
      SELECT CONCAT(_prefix, cur, _suffix) into result;
    END CASE;
  return (result);
END;
-- DELIMITER ;
/

DROP TABLE IF EXISTS `sa_sequence`
/
-- 导出  表 om_weekly_report.sa_sequence 结构
CREATE TABLE IF NOT EXISTS `sa_sequence` (
  `seq_name` varchar(128) NOT NULL COMMENT '序列名称',
  `platform` varchar(50) NOT NULL COMMENT '所属平台',
  `value` int NOT NULL COMMENT '目前序列值',
  `prefix` varchar(10) NOT NULL DEFAULT '' COMMENT '序列前缀',
  `suffix` varchar(10) NOT NULL DEFAULT '' COMMENT '序列后缀',
  `min_value` int NOT NULL COMMENT '最小值',
  `max_value` int NOT NULL COMMENT '最大值',
  `step` int NOT NULL DEFAULT '1' COMMENT '每次取值的数量',
  `create_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `create_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `update_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  `update_by` varchar(50) DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`seq_name`)
) ENGINE=InnoDB COMMENT='队列表'
/

INSERT INTO `sa_sequence` (`seq_name`, `platform`, `value`, `prefix`, `suffix`, `min_value`, `max_value`, `step`) VALUES
	('AC_DEPT_ID_SEQ', 'sys', 10000000, '', '', 10000000, 99999999, 1),
	('AC_USER_CUSTOMER_NUMBER_SEQ', 'sys', 10000000, '', '', 10000000, 99999999, 1)
/

