# 智能问数语义层

## 1. 实体

### SchedulePlan 进度计划
- 数据表：fpim.icms_schedule_plan
- 主键：id
- 别名：计划, WBS, 施工进度计划
- 说明：进度计划 WBS 树：level=1 总控计划（全项目）→ level=2 年度施工进度计划（2025/2026）→ 施工标 → 单位/分部/分项工程；投资/工程量字段父子层级均存值，聚合指标仅取末级（if_last_level=1）避免重复累计；年度口径查询用 full_path_name 锚定（如 contains '/2026年施工进度计划'）；默认仅统计启用且已审核数据
- 可列表：是（展示字段：pro_name, name, level, plan_start_date, plan_end_date, reality_start_date, reality_end_date, invest_price, finish_rate, accumulate_proportion, head_user_name, paragraph_name）
- 关键字段：
  - id：表ID
  - pro_id：项目ID
  - pro_name：项目名称
  - code：单据编码
  - name：名称
  - original_code：原始编码
  - parent_id：上级ID
  - parent_name：上级名称
  - type：类型
  - plan_duration：原定工期
  - plan_start_date：计划开始
  - plan_end_date：计划完成
  - reality_start_date：实际开始
  - reality_end_date：实际完成
  - desir_end_date：期望完成
  - weight：权重
  - head_user_name：责任人
  - level：级次
  - if_last_level：是否末级（枚举，映射见 value-domains.json）
  - full_path_name：全路径名称
  - paragraph_id：标段ID
  - paragraph_name：标段名称
  - unit_project_id：单位工程ID
  - period_accumulate_proportion：上期累计占比
  - accumulate_proportion：累计占比
  - latest_feedback_date：最新反馈日期
  - proportion：完成占比
  - invest_price：投资金额（万元）
  - data_type：数据类型（枚举，映射见 value-domains.json）
  - work_num：工程数量
  - unit：单位
  - finish_work_num：已完成数量
  - finish_estimate：已完成估投
  - finish_rate：已完成比例
  - project_code：工程编码

### QualityQbs 质量验评（QBS）
- 数据表：fpim.icms_quality_qbs
- 主键：id
- 别名：验评, 质量验评, QBS, 分部工程验评
- 粒度（Grain）：一行 = 一个 QBS 树节点（验评结论挂在单元工程节点上）
- 说明：质量验评 QBS 树（合同工程→单位工程→分部工程→单元工程）；验评结果仅末级单元工程（type=4）有效，验评率/优良率口径均以单元工程为基数；默认仅统计启用且已审核数据
- 可列表：是（展示字段：pro_name, name, pro_unit_name, type, level, result, result_date, record_date）
- 关键字段：
  - id：表ID
  - pro_id：项目ID
  - pro_name：项目名称
  - pro_code：项目编码
  - code：编码
  - name：名称
  - pro_unit_id：单位ID
  - pro_unit_name：单位名称
  - type：类型（枚举，映射见 value-domains.json）
  - level：级次
  - if_last_level：是否末级（枚举，映射见 value-domains.json）
  - parent_id：上级ID
  - parent_name：上级名称
  - full_path_name：全路径名称
  - result：验评结果（枚举，映射见 value-domains.json）
  - result_date：验评时间
  - record_date：备案时间
  - start_date：开工时间
  - evaluation_date：评定/验收时间
  - handle_person：责任人
  - input_person：登记人员
  - input_time：登记时间

### InvestMonthly 月度投资
- 数据表：fpim.icms_invest_monthly
- 主键：id
- 别名：月度投资执行单, 当月投资, 投资完成
- 粒度（Grain）：一行 = 一个单位在一个月的完成投资（同一单位同月可有多张执行单）
- 说明：月度投资执行单：一行 = 某单位某月完成投资；year/month 为整数年度与月份，finish_price 为当月完成投资（万元）；按单位（标段）pro_unit_name 填报；默认仅统计启用且已审核数据
- 可列表：是（展示字段：pro_name, year, month, pro_unit_name, finish_price, handle_person, handle_date）
- 关键字段：
  - id：表ID
  - pro_id：项目ID
  - pro_name：项目名称
  - pro_code：项目编码
  - code：单据编码
  - name：单据名称
  - year：年度
  - month：月度
  - handle_person：填报人员
  - handle_date：填报日期
  - pro_unit_id：单位ID
  - pro_unit_name：单位名称
  - finish_price：当月完成投资（万元）
  - input_time：登记时间

### ProjectHazard 危险源
- 数据表：fpim.icms_project_hazard
- 主键：id
- 别名：危险源管理, 安全风险, 隐患
- 粒度（Grain）：一行 = 一个危险源记录
- 说明：危险源台账：一行 = 一个危险源；project 为施工项目（如明挖施工/填筑工程），type 为危险源分级（1重大/2较大/3一般），type_class 为类别（施工作业类等），level 为风险等级文本；confirm_status 确认状态：1=当前确认在册，2=已解除确认（cancel_confirm_time 为解除时间）；find_time 为发现时间；各责任人字段均为姓名文本；默认仅统计启用且已审核数据
- 可列表：是（展示字段：source, project, type_class, level, pro_unit_name, produce_duty, find_time, confirm_status）
- 关键字段：
  - id：主键
  - pro_id：项目ID
  - pro_name：项目名称
  - pro_unit_id：标段ID
  - pro_unit_name：标段单位名称
  - type：危险源分级（枚举，映射见 value-domains.json）
  - type_class：类别（枚举，映射见 value-domains.json）
  - source：危险源名称
  - accident_type：可能导致事故类型
  - level：风险等级（枚举，映射见 value-domains.json）
  - programme：相关方案
  - measure：控制措施
  - produce_duty：生产负责人
  - technology_duty：技术负责人
  - safe_duty：安全负责人
  - first_duty：项目第一负责人
  - project：施工项目
  - confirm_status：确认状态（枚举，映射见 value-domains.json）
  - cancel_confirm_time：解除确认时间
  - find_time：发现时间
  - input_time：登记时间

## 2. 关系


## 3. 指标

### qbs_node_count QBS节点数
- 口径：质量验评 QBS 树节点总数（含合同工程/单位工程/分部工程/单元工程各层级），源表 icms_quality_qbs，默认仅统计启用且已审核数据
- 公式：COUNT(fpim.icms_quality_qbs.id)
- 支持维度：
  - qbs_project
  - qbs_unit
  - qbs_type
  - qbs_result
  - qbs_level
  - qbs_result_date
  - qbs_wbs_name
  - qbs_full_path

### qbs_unit_eng_count 单元工程数
- 口径：单元工程（type=4）总数，验评统计的分母基数，源表 icms_quality_qbs
- 公式：SUM(CASE WHEN fpim.icms_quality_qbs.type = 4 THEN 1 ELSE 0 END)
- 支持维度：
  - qbs_project
  - qbs_unit
  - qbs_result
  - qbs_level
  - qbs_result_date
  - qbs_wbs_name
  - qbs_full_path

### qbs_evaluated_count 已验评数
- 口径：已完成验评的单元工程数（type=4 且验评结果为优良或合格），源表 icms_quality_qbs
- 公式：SUM(CASE WHEN fpim.icms_quality_qbs.type = 4 AND fpim.icms_quality_qbs.result IN (1, 2) THEN 1 ELSE 0 END)
- 支持维度：
  - qbs_project
  - qbs_unit
  - qbs_level
  - qbs_result_date
  - qbs_wbs_name
  - qbs_full_path

### qbs_good_count 优良数
- 口径：验评结果为优良的单元工程数（type=4 且 result=1），源表 icms_quality_qbs
- 公式：SUM(CASE WHEN fpim.icms_quality_qbs.type = 4 AND fpim.icms_quality_qbs.result = 1 THEN 1 ELSE 0 END)
- 支持维度：
  - qbs_project
  - qbs_unit
  - qbs_level
  - qbs_result_date
  - qbs_wbs_name
  - qbs_full_path

### qbs_qualified_count 合格数
- 口径：验评结果为合格的单元工程数（type=4 且 result=2），源表 icms_quality_qbs
- 公式：SUM(CASE WHEN fpim.icms_quality_qbs.type = 4 AND fpim.icms_quality_qbs.result = 2 THEN 1 ELSE 0 END)
- 支持维度：
  - qbs_project
  - qbs_unit
  - qbs_level
  - qbs_result_date
  - qbs_wbs_name
  - qbs_full_path

### qbs_unevaluated_count 未验评数
- 口径：尚未验评的单元工程数（type=4 且 result=3 或为空），源表 icms_quality_qbs
- 公式：SUM(CASE WHEN fpim.icms_quality_qbs.type = 4 AND (fpim.icms_quality_qbs.result = 3 OR fpim.icms_quality_qbs.result IS NULL) THEN 1 ELSE 0 END)
- 支持维度：
  - qbs_project
  - qbs_unit
  - qbs_level
  - qbs_wbs_name
  - qbs_full_path

### qbs_evaluated_rate 验评率
- 口径：已验评单元工程数 / 单元工程总数（0~1 小数，展示时转百分比），源表 icms_quality_qbs
- 公式：SUM(CASE WHEN fpim.icms_quality_qbs.type = 4 AND fpim.icms_quality_qbs.result IN (1, 2) THEN 1 ELSE 0 END) / NULLIF(SUM(CASE WHEN fpim.icms_quality_qbs.type = 4 THEN 1 ELSE 0 END), 0)
- 支持维度：
  - qbs_project
  - qbs_unit
  - qbs_level
  - qbs_full_path

### qbs_good_rate 优良率
- 口径：优良单元工程数 / 已验评单元工程数（0~1 小数，展示时转百分比），源表 icms_quality_qbs
- 公式：SUM(CASE WHEN fpim.icms_quality_qbs.type = 4 AND fpim.icms_quality_qbs.result = 1 THEN 1 ELSE 0 END) / NULLIF(SUM(CASE WHEN fpim.icms_quality_qbs.type = 4 AND fpim.icms_quality_qbs.result IN (1, 2) THEN 1 ELSE 0 END), 0)
- 支持维度：
  - qbs_project
  - qbs_unit
  - qbs_level
  - qbs_full_path

### qbs_qualified_rate 合格率
- 口径：合格单元工程数 / 已验评单元工程数（0~1 小数，展示时转百分比；与优良率互补，两者之和为 100%），源表 icms_quality_qbs
- 公式：SUM(CASE WHEN fpim.icms_quality_qbs.type = 4 AND fpim.icms_quality_qbs.result = 2 THEN 1 ELSE 0 END) / NULLIF(SUM(CASE WHEN fpim.icms_quality_qbs.type = 4 AND fpim.icms_quality_qbs.result IN (1, 2) THEN 1 ELSE 0 END), 0)
- 支持维度：
  - qbs_project
  - qbs_unit
  - qbs_level
  - qbs_full_path

### qbs_unit_work_count 单位工程数
- 口径：单位工程（type=2）总数，源表 icms_quality_qbs
- 公式：SUM(CASE WHEN fpim.icms_quality_qbs.type = 2 THEN 1 ELSE 0 END)
- 支持维度：
  - qbs_project
  - qbs_unit
  - qbs_wbs_name
  - qbs_full_path

### qbs_section_work_count 分部工程数
- 口径：分部工程（type=3）总数，源表 icms_quality_qbs
- 公式：SUM(CASE WHEN fpim.icms_quality_qbs.type = 3 THEN 1 ELSE 0 END)
- 支持维度：
  - qbs_project
  - qbs_unit
  - qbs_wbs_name
  - qbs_full_path

### plan_task_count 计划节点数
- 口径：进度计划 WBS 节点总数（含各层级），源表 icms_schedule_plan，默认仅统计启用且已审核数据
- 公式：COUNT(fpim.icms_schedule_plan.id)
- 支持维度：
  - plan_project
  - plan_wbs_name
  - plan_level
  - plan_data_type
  - plan_owner
  - plan_start_date
  - plan_if_last_level
  - plan_full_path

### plan_leaf_invest 末级节点投资金额
- 口径：进度计划末级节点（if_last_level=1）投资金额合计（万元）；WBS 树父子层级均存投资金额，仅取末级避免重复累计，源表 icms_schedule_plan
- 公式：SUM(CASE WHEN fpim.icms_schedule_plan.if_last_level = 1 THEN fpim.icms_schedule_plan.invest_price ELSE 0 END)
- 支持维度：
  - plan_project
  - plan_wbs_name
  - plan_level
  - plan_data_type
  - plan_owner
  - plan_start_date
  - plan_full_path

### plan_leaf_work_num 末级节点工程数量
- 口径：进度计划末级节点（if_last_level=1）工程数量合计，仅取末级避免父子层级重复累计，源表 icms_schedule_plan
- 公式：SUM(CASE WHEN fpim.icms_schedule_plan.if_last_level = 1 THEN fpim.icms_schedule_plan.work_num ELSE 0 END)
- 支持维度：
  - plan_project
  - plan_wbs_name
  - plan_level
  - plan_data_type
  - plan_owner
  - plan_start_date
  - plan_full_path

### plan_leaf_finish_work_num 末级节点已完成数量
- 口径：进度计划末级节点（if_last_level=1）已完成数量合计，仅取末级避免父子层级重复累计，源表 icms_schedule_plan
- 公式：SUM(CASE WHEN fpim.icms_schedule_plan.if_last_level = 1 THEN fpim.icms_schedule_plan.finish_work_num ELSE 0 END)
- 支持维度：
  - plan_project
  - plan_wbs_name
  - plan_level
  - plan_data_type
  - plan_owner
  - plan_start_date
  - plan_full_path

### plan_duration_avg 平均原定工期
- 口径：草稿口径：进度计划原定工期算术平均（天，含各层级节点，树结构加权口径待业务确认）
- 公式：AVG(fpim.icms_schedule_plan.plan_duration)
- 支持维度：
  - plan_project
  - plan_wbs_name
  - plan_level
  - plan_data_type
  - plan_owner
  - plan_start_date
  - plan_full_path

### plan_project_invest 工程总投资
- 口径：工程总投资（万元）：取总控计划根节点（level=1）的投资金额，全项目唯一；展示时可按需换算亿元
- 公式：SUM(CASE WHEN fpim.icms_schedule_plan.level = 1 THEN fpim.icms_schedule_plan.invest_price ELSE 0 END)
- 支持维度：
  - plan_project
  - plan_wbs_name

### plan_year_invest 年度计划投资
- 口径：年度施工进度计划投资（万元）：取 level=2 年度根节点投资金额；查具体年份时用 plan_wbs_name 或 plan_full_path 过滤（如 '2026年施工进度计划'）
- 公式：SUM(CASE WHEN fpim.icms_schedule_plan.level = 2 THEN fpim.icms_schedule_plan.invest_price ELSE 0 END)
- 支持维度：
  - plan_project
  - plan_wbs_name
  - plan_full_path
  - plan_start_date

### plan_time_progress_rate 计划完成比例
- 口径：计划完成比例 = 已历时工期 / 总工期（节点计划开始至计划完成，截至当前日期，截断在 0~1，仅取 level≤1/2 根节点）；查询时须用 plan_wbs_name 或 plan_full_path 锁定单个根节点（总控计划或年度计划）；0~1 小数，展示时转百分比
- 公式：MAX(CASE WHEN fpim.icms_schedule_plan.level IN (1, 2) THEN GREATEST(0, LEAST(1, DATEDIFF(CURDATE(), fpim.icms_schedule_plan.plan_start_date) / NULLIF(DATEDIFF(fpim.icms_schedule_plan.plan_end_date, fpim.icms_schedule_plan.plan_start_date), 0))) ELSE 0 END)
- 支持维度：
  - plan_wbs_name
  - plan_level
  - plan_full_path

### plan_actual_finish_rate 实际完成比例
- 口径：实际完成比例 = 子树内末级节点已完成估投合计 / 根节点投资；**必须**用 plan_full_path contains 锚定年度根节点（如 '/2026年施工进度计划'，不带尾斜杠，否则会漏掉根节点自身）；不带年度锚定的全局口径依赖反馈明细表（尚未迁入），禁止无过滤使用；0~1 小数，展示时转百分比
- 公式：SUM(CASE WHEN fpim.icms_schedule_plan.if_last_level = 1 THEN fpim.icms_schedule_plan.finish_estimate ELSE 0 END) / NULLIF(SUM(CASE WHEN fpim.icms_schedule_plan.level IN (1, 2) THEN fpim.icms_schedule_plan.invest_price ELSE 0 END), 0)
- 支持维度：
  - plan_full_path
  - plan_wbs_name

### invest_finish_month 当月完成投资
- 口径：月度投资执行单当月完成投资合计（万元）；按 year/month/单位汇总；注意与计划表已完成估投（finish_estimate）为两套独立口径，数值不一致属正常；源表 icms_invest_monthly
- 公式：SUM(fpim.icms_invest_monthly.finish_price)
- 支持维度：
  - invest_year
  - invest_month
  - invest_unit

### hazard_count 危险源数
- 口径：危险源台账记录总数（含已解除确认），源表 icms_project_hazard
- 公式：COUNT(fpim.icms_project_hazard.id)
- 支持维度：
  - hazard_project
  - hazard_type_class
  - hazard_level
  - hazard_unit
  - hazard_find_time
  - hazard_confirm_status
  - hazard_source
  - hazard_produce_duty

### hazard_confirmed_count 当前在册危险源数
- 口径：当前确认在册（confirm_status=1，未解除确认）的危险源数；用户问“现在/当前有多少危险源”时用此指标
- 公式：SUM(CASE WHEN fpim.icms_project_hazard.confirm_status = '1' THEN 1 ELSE 0 END)
- 支持维度：
  - hazard_project
  - hazard_type_class
  - hazard_level
  - hazard_unit
  - hazard_find_time
  - hazard_source
  - hazard_produce_duty

### hazard_major_count 重大危险源数
- 口径：重大危险源（type=1）数量，源表 icms_project_hazard
- 公式：SUM(CASE WHEN fpim.icms_project_hazard.type = '1' THEN 1 ELSE 0 END)
- 支持维度：
  - hazard_project
  - hazard_type_class
  - hazard_level
  - hazard_unit
  - hazard_find_time
  - hazard_confirm_status

### hazard_general_count 一般危险源数
- 口径：一般危险源（type=3）数量，源表 icms_project_hazard
- 公式：SUM(CASE WHEN fpim.icms_project_hazard.type = '3' THEN 1 ELSE 0 END)
- 支持维度：
  - hazard_project
  - hazard_type_class
  - hazard_level
  - hazard_unit
  - hazard_find_time
  - hazard_confirm_status

## 4. 维度

### plan_project 项目
- 字段：fpim.icms_schedule_plan.pro_name

### plan_paragraph 标段
- 字段：fpim.icms_schedule_plan.paragraph_name

### plan_wbs_name 计划名称
- 字段：fpim.icms_schedule_plan.name

### plan_level 级次
- 字段：fpim.icms_schedule_plan.level

### plan_data_type 数据类型
- 字段：fpim.icms_schedule_plan.data_type

### plan_owner 责任人
- 字段：fpim.icms_schedule_plan.head_user_name

### plan_start_date 计划开始日期
- 字段：fpim.icms_schedule_plan.plan_start_date

### plan_if_last_level 是否末级
- 字段：fpim.icms_schedule_plan.if_last_level

### plan_full_path 计划全路径
- 字段：fpim.icms_schedule_plan.full_path_name

### qbs_project 验评项目
- 字段：fpim.icms_quality_qbs.pro_name

### qbs_unit 施工单位
- 字段：fpim.icms_quality_qbs.pro_unit_name

### qbs_type QBS类型
- 字段：fpim.icms_quality_qbs.type

### qbs_result 验评结果
- 字段：fpim.icms_quality_qbs.result

### qbs_level 验评级次
- 字段：fpim.icms_quality_qbs.level

### qbs_result_date 验评时间
- 字段：fpim.icms_quality_qbs.result_date

### qbs_wbs_name 验评名称
- 字段：fpim.icms_quality_qbs.name

### qbs_full_path 验评全路径
- 字段：fpim.icms_quality_qbs.full_path_name

### invest_year 投资年度
- 字段：fpim.icms_invest_monthly.year

### invest_month 投资月份
- 字段：fpim.icms_invest_monthly.month

### invest_unit 投资单位
- 字段：fpim.icms_invest_monthly.pro_unit_name

### hazard_project 施工项目
- 字段：fpim.icms_project_hazard.project

### hazard_type_class 危险源类别
- 字段：fpim.icms_project_hazard.type_class

### hazard_level 风险等级
- 字段：fpim.icms_project_hazard.level

### hazard_unit 危险源所属标段
- 字段：fpim.icms_project_hazard.pro_unit_name

### hazard_find_time 发现时间
- 字段：fpim.icms_project_hazard.find_time

### hazard_confirm_status 确认状态
- 字段：fpim.icms_project_hazard.confirm_status

### hazard_source 危险源名称
- 字段：fpim.icms_project_hazard.source

### hazard_produce_duty 生产负责人
- 字段：fpim.icms_project_hazard.produce_duty

## 5. 枚举值域

- data_type（SchedulePlan.data_type）：0=总控 / 1=分类 / 2=子项，详见 value-domains.json
- qbs_type（QualityQbs.type）：1=合同工程 / 2=单位工程 / 3=分部工程 / 4=单元工程，详见 value-domains.json
- qbs_result（QualityQbs.result）：1=优良 / 2=合格 / 3=未验评，详见 value-domains.json
- if_last_level（SchedulePlan.if_last_level）：1=是 / 2=否，详见 value-domains.json
- hazard_type（ProjectHazard.type）：1=重大危险源 / 2=较大危险源 / 3=一般危险源，详见 value-domains.json
- hazard_confirm_status（ProjectHazard.confirm_status）：1=已确认 / 2=已解除确认，详见 value-domains.json
- hazard_level（ProjectHazard.level）：低风险 / 一般风险 / 较大风险 / 重大风险，详见 value-domains.json
- hazard_class（ProjectHazard.type_class）：施工作业类 / 机械设备类 / 设施场所类 / 作业环境类 / 其他类，详见 value-domains.json
- 值域是本体的一部分：物理码值与业务标签的映射必须显式定义，禁止运行时猜测

## 6. 业务概念与维度层级


> 说明：本文件由 entities.json / relations.json / metrics.json / dimensions.json / concepts.json / value-domains.json 自动生成，禁止手工编辑，以保证本体单一真源。
