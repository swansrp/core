-- 基本工具函数
-- 沙若鹏

-- 导出  函数 f_nextval 结构
DROP FUNCTION IF EXISTS `f_nextval`
/
-- DELIMITER //
CREATE
  DEFINER =`dbAdmin`@`%` FUNCTION `f_nextval`(`SEQ_NAME` VARCHAR(128)) RETURNS varchar(50) CHARSET utf8
  COMMENT '获取流水号'
  SQL SECURITY INVOKER
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

  select count(1) into exsited from sequence seq where seq.seq_name = SEQ_NAME;
  CASE
    when exsited = 0
      then set result = '';
    ELSE
      select value, prefix, suffix, min_value, max_value, step into cur,_prefix,_suffix,_min,_max,_step
      from sequence seq
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
      UPDATE sequence seq SET seq.value=next WHERE seq.seq_name = SEQ_NAME;
      SELECT CONCAT(_prefix, cur, _suffix) into result;
    END CASE;
  return (result);
END;
-- DELIMITER ;
/
-- 导出  表 sequence 结构
DROP TABLE IF EXISTS `sequence`
/
CREATE TABLE IF NOT EXISTS `sequence`
(
  `seq_name`         varchar(128) NOT NULL COMMENT '序列名称',
  `value`            int(11)      NOT NULL COMMENT '目前序列值',
  `prefix`           varchar(10)  NOT NULL DEFAULT '' COMMENT '序列前缀',
  `suffix`           varchar(10)  NOT NULL DEFAULT '' COMMENT '序列后缀',
  `min_value`        int(11)      NOT NULL COMMENT '最小值',
  `max_value`        int(11)      NOT NULL COMMENT '最大值',
  `step`             int(11)      NOT NULL DEFAULT '1' COMMENT '每次取值的数量',
  `create_time`      datetime(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
  `last_update_time` datetime(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '修改时间',
  PRIMARY KEY (`seq_name`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='队列表'
/


