package com.bidr.insight.smartquery.constant.dict;

import com.bidr.kernel.constant.dict.Dict;
import com.bidr.kernel.constant.dict.MetaDict;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Title: TechColumnDict
 * Description: 技术列忽略黑名单（骨架预填 role=ignore），一处定义：
 * 1) @MetaDict 自动注册为系统字典（value=列名/模式，label=忽略原因），字典维护页可增删；
 * 2) 骨架生成预填时命中即落 ignore，人工确认页无需逐列点——来自数仓实证：
 *    dw_cdt 遍布 328 表、pt_key 41 表，合计约 458 列次（含 ods 未加工层同族列）。
 * 匹配三模式：exact 列名精确（大小写不敏感）/ prefix 列名前缀 / suffix 列名后缀。
 *
 * @author Sharp
 * @since 2026/8/23
 */
@Getter
@RequiredArgsConstructor
@MetaDict(value = "TECH_COLUMN_DICT", remark = "技术列忽略黑名单")
public enum TechColumnDict implements Dict {

    /** 数仓 ETL 写入时间，与业务时间无关 */
    DW_CDT("dw_cdt", "数仓创建时间", Mode.EXACT),
    /** 数仓分区键（dy/dm/dd 已单独承载时间语义） */
    PT_KEY("pt_key", "分区键", Mode.EXACT),
    /** 源系统逻辑删除标识 */
    DR("dr", "删除标识", Mode.EXACT),
    /** 数仓有效标记（0 无效 1 有效），ETL 过滤用 */
    VALID_STATUS("valid_status", "有效状态", Mode.EXACT),
    /** NC 系启用状态（1 未启用 2 已启用 3 已停用） */
    ENABLESTATE("enablestate", "启用状态", Mode.EXACT),
    /** 源系统主表链 id */
    MASTERID("masterid", "主表链ID", Mode.EXACT),
    /** 源系统行程序号 */
    SEQU("sequ", "行序号", Mode.EXACT),
    /** 源系统记录版本号 */
    REVISION("revision", "记录版本", Mode.EXACT),
    /** NC 系录入人 */
    REGHUMID("reghumid", "录入人", Mode.EXACT),
    /** NC 系录入日期（源系统登记时间，非业务时间） */
    REGDATE("regdate", "录入日期", Mode.EXACT),
    /** NC 系创建时间（无下划线变体，带下划线族由 TECH_TIME_COL 正则兜住） */
    CREATETIME("createtime", "创建时间", Mode.EXACT),
    /** NC 系修改时间（无下划线变体） */
    MODIFYTIME("modifytime", "修改时间", Mode.EXACT),
    /** NC 系创建人 */
    CREATORID("creatorid", "创建人", Mode.EXACT),
    /** 平台内部主键前缀（NC pk_*；业务组织 pk_org/pk_group 单独精确忽略） */
    PK_("pk_", "内部主键前缀", Mode.PREFIX),
    /** 组织主键（NC 内部 org id，组织语义走 dept_code/dept_name） */
    PK_ORG("pk_org", "组织内部主键", Mode.EXACT),
    /** 集团主键 */
    PK_GROUP("pk_group", "集团内部主键", Mode.EXACT),
    /** NC 自定义扩展字段 def1~defN */
    DEF("def", "自定义扩展字段", Mode.PREFIX),
    ;

    /** 字典键值 = 列名或模式 */
    private final String value;
    /** 字典标签 = 忽略原因（人工确认页可展示） */
    private final String label;
    /** 匹配模式 */
    private final Mode mode;

    /** 匹配模式：精确 / 前缀 / 后缀（均大小写不敏感） */
    public enum Mode {
        EXACT, PREFIX, SUFFIX
    }

    /** 该列是否命中黑名单（静态可测）：按枚举声明顺序逐条匹配 */
    public static boolean isTechColumn(String col) {
        if (col == null || col.isEmpty()) {
            return false;
        }
        String lower = col.toLowerCase();
        for (TechColumnDict t : values()) {
            switch (t.mode) {
                case EXACT:
                    if (lower.equals(t.value)) {
                        return true;
                    }
                    break;
                case PREFIX:
                    if (lower.startsWith(t.value)) {
                        return true;
                    }
                    break;
                case SUFFIX:
                    if (lower.endsWith(t.value)) {
                        return true;
                    }
                    break;
                default:
                    break;
            }
        }
        return false;
    }
}
