package com.bidr.insight.smartquery.constant.dict;

import com.bidr.kernel.constant.dict.Dict;
import com.bidr.kernel.constant.dict.MetaDict;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;

/**
 * Title: DimensionGroupDict
 * Description: 维度归类大类目录（启发式目录字典），一处定义两端消费：
 * 1) @MetaDict 自动注册为系统字典（value/label 入库），前端实体确认页归类下拉直接取字典接口，
 *    新增大类只加一个枚举项，前后端都不用另改；
 * 2) 扩展匹配字段（enWordRoots/cnWords/colExact）供 SmartAgentMetaService 启发式建底逻辑使用，
 *    枚举声明顺序即匹配优先级。
 *
 * @author Sharp
 * @since 2026/8/23
 */
@Getter
@RequiredArgsConstructor
@MetaDict(value = "DIMENSION_GROUP_DICT", remark = "维度归类大类")
public enum DimensionGroupDict implements Dict {

    /** 时间类：年/季/月/周/日，含数仓时间分区列 dy/dm/dd */
    TIME("时间类", "时间类",
            Arrays.asList("year", "quarter", "month", "week", "day"),
            Arrays.asList("年", "季", "月", "周", "日", "日期", "时间"),
            Arrays.asList("dy", "dm", "dd", "date")),

    /** 组织类：部门/机构/单位等组织归属属性（colExact 来自数仓实证高频列） */
    ORGANIZATION("组织类", "组织类",
            Arrays.asList("dept", "org", "unit", "company", "team", "branch"),
            Arrays.asList("部门", "机构", "单位", "科室", "公司", "院", "处"),
            Arrays.asList("dept_code", "dept_name", "cost_dept_code", "cost_dept_name", "ass_org_code")),

    /** 地区类：省市区县/区域/所在地（bprov=归属省份、bidr=区域、area1=一级经营区域，数仓实证） */
    REGION("地区类", "地区类",
            Arrays.asList("region", "prov", "province", "city", "district", "county", "area", "location", "addr"),
            Arrays.asList("地区", "省", "市", "区县", "区域", "所在地", "地址"),
            Arrays.asList("bprov_code", "bidr_code", "bidr_name", "area1_code", "area1_name")),

    /** 人员类：员工/责任人/经办人（不含泛 "name" 词根，防"项目名称"类误伤） */
    PERSONNEL("人员类", "人员类",
            Arrays.asList("emp", "staff", "worker", "person", "engineer"),
            Arrays.asList("人员", "职工", "姓名", "责任人", "负责人", "经办人"),
            Arrays.asList("user_no", "user_name")),

    /** 项目类：项目/工程/标段（pmp=生产项目、dct=生产任务，数仓实证） */
    PROJECT("项目类", "项目类",
            Arrays.asList("project", "proj", "prj"),
            Arrays.asList("项目", "工程", "标段"),
            Arrays.asList("pmp_code", "pmp_name", "project_code", "project_name", "projectcode",
                    "dct_code", "dct_id")),

    /** 合同类：合同/协议（tpc=传统采购合同、tcpc=传统采购合同明细，数仓实证） */
    CONTRACT("合同类", "合同类",
            Arrays.asList("contract"),
            Arrays.asList("合同", "协议"),
            Arrays.asList("contract_code", "tpc_code", "tpc_id", "tcpc_id", "tcpc_code")),

    /** 客户类：客户/供应商等合作对象（clue=销售线索，数仓实证） */
    CUSTOMER("客户类", "客户类",
            Arrays.asList("customer", "client", "supplier", "vendor"),
            Arrays.asList("客户", "甲方", "乙方", "供应商", "承包商"),
            Arrays.asList("customer_no", "clue_no")),

    /** 设备类：设备/机械/车辆 */
    EQUIPMENT("设备类", "设备类",
            Arrays.asList("equip", "device", "machine", "vehicle"),
            Arrays.asList("设备", "机械", "车辆"),
            Arrays.asList()),

    /** 物资类：材料/物资 */
    MATERIAL("物资类", "物资类",
            Arrays.asList("material"),
            Arrays.asList("物资", "材料"),
            Arrays.asList()),

    /** 科目类：财务科目（数仓实证：account_id/account_name 遍布 14 表，财务域核心维度） */
    ACCOUNT("科目类", "科目类",
            Arrays.asList("account", "subj"),
            Arrays.asList("科目"),
            Arrays.asList("account_id", "account_name")),

    /** 类型类：分类/性质等切面属性（泛词根置后，领域组优先命中） */
    TYPE("类型类", "类型类",
            Arrays.asList("type", "category", "kind"),
            Arrays.asList("类型", "类别", "分类"),
            Arrays.asList()),

    /** 状态类：状态/阶段（泛词根置后，如"项目状态"优先落项目类） */
    STATUS("状态类", "状态类",
            Arrays.asList("status", "state"),
            Arrays.asList("状态", "阶段"),
            Arrays.asList()),
    ;

    /** 字典键值 = 组名（与 concepts hierarchy 组名、实体字段 dim_group 存储值同源） */
    private final String value;
    /** 字典标签 = 组名 */
    private final String label;
    /** 维度名英文词根（子串匹配，不区分大小写） */
    private final List<String> enWordRoots;
    /** 显示名中文词（子串匹配） */
    private final List<String> cnWords;
    /** expression 末段列名精确匹配 */
    private final List<String> colExact;
}
