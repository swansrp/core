1.创建脚本文件
  执行createNewDDLChangeFile.bat创建DDL（数据定义语言，用来维护数据库对象）脚本文件，
在src/main/sql目录下会创建对应的DDL的sql文件，每个DDL语句一个脚本文件。

  执行createNewDMLChangeFile.bat创建DML（用于增删改表中数据，DML是伴随事务控制的）脚本文件，
在src/main/sql目录下会创建对应的DML的sql文件


2.编写脚本
用正斜杠/分割DML语句,不可用";"来分割，形如：
insert into fw_permission (PERMISSION_ID, NAME, DESCRIPTION)
values ('function_financeManagement_liqsettle', '扣款明细导出', '')
/
insert into fw_permission_management (NODE_ID, PARENT_NODE_ID, PERMISSION_ID, NODE_NAME, DISPLAY_ORDER, NOTES)
values ('110062', '100004', 'function_financeManagement_liqsettle', '扣款明细导出', 350, '')
/

用正斜杠/分割DDL语句，形如：
create table CO_SEND
(
  SEND_ID            VARCHAR2(20) not null,
  MOBILE             VARCHAR2(24)
)
/
comment on column CO_SEND.SEND_ID
  is '发送ID'
/
comment on column CO_SEND.MOBILE
  is '手机号码'
/

编写数据库function时，其sql中的";"不可替换为 "/",形如:
CREATE OR REPLACE 
FUNCTION     F_BENIFIT(AMOUNT NUMBER,DURATION NUMBER,DURATION_UNIT VARCHAR2,INTEREST_TYPE VARCHAR2,RATE NUMBER)
       RETURN VARCHAR2 IS BENIFIT NUMBER;
begin
  CASE
    WHEN INTEREST_TYPE NOT IN  ('0' , '1') OR DURATION_UNIT NOT IN ('D')
      THEN
      BENIFIT := 0;
   ELSE
      SELECT  AMOUNT * RATE/100 * DURATION/ decode(INTEREST_TYPE, '0', 360, 365)  INTO BENIFIT
      FROM dual;
  END CASE;
  return(BENIFIT);
END F_BENIFIT;
/

编写数据库存储过程时，其sql中的";"不可替换为 "/"。

编写数据库触发器时，其sql中的";"不可替换为 "/"。

3.DDL语句不回滚，一个脚本文件只能放一个DDL语句。DDL与DML语句不要写在一个文件下,DML不要写commit

4.如果发现已经执行的语句有错误，则需要新生成一个文件来修改记录，不要在原文件上修改

5.sql语句中表名前不要加schema

6.时间格式要写成TO_TIMESTAMP('2018-03-28 10:01:54:000000', 'YYYY-MM-DD HH24:MI:SS:FF6')

7.sql中不允许使用truncate语句。

8.insert语句必须完整，即要添加所有字段，不可省略。

9.写db脚本的时候 务必用注释写一下此脚本是为了啥功能 尽量让咱们代码审核或者运维上线的时候做到有凭有据，注释使用双横线  --

10.执行run.bat
执行后，如无报错信息，则会将文件中的内容同步到脚本文件指定的数据库库中，
并会在CHANGELOG表中新增一条记录