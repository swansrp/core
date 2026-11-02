package com.bidr.authorization.identity;

import java.util.Date;
import java.util.List;

/**
 * Title: OrgSyncResult
 * Description: 组织同步结果——{@link com.bidr.authorization.identity.IdentityBridge#syncOrg()} 的返回体，
 * 部门树＋账号一次拉平的纯数据面。语义来自 bidr MdmJobService.syncMdmDept()/syncMdmAccount()
 * 的落库要素与 plsintec FeishuSyncResult（飞书同步结果）；
 * 落库/建缓存由内核统一执行，桥不写内核表。
 *
 * @author Sharp
 * @since 2026-10-31
 */
public class OrgSyncResult {

    /** 同步到的部门行（父引用用 parentExternalId 表达，内核自行组树） */
    public List<DeptRow> depts;

    /** 同步到的用户行（挂部门用 deptExternalId 表达） */
    public List<UserRow> users;

    /** 同步完成时间（桥侧时钟） */
    public Date syncedAt;

    /**
     * 部门行——外部部门五要素：外部 ID / 名称 / 父外部 ID / 展示排序 / 负责人。
     * 语义来自 MdmJobService.syncMdmDept 与 FeishuSyncService.syncDepts 的部门字段面。
     */
    public static class DeptRow {

        /** 桥侧部门唯一 ID */
        public String externalId;

        /** 部门名称 */
        public String name;

        /** 父部门外部 ID（根部门为 null） */
        public String parentExternalId;

        /** 部门展示排序（数值小者靠前；语义来源 AcDept.showOrder，允许为 null） */
        public Integer showOrder;

        /** 部门负责人标识（external user id），非显示名（无负责人为 null） */
        public String leader;
    }

    /**
     * 用户行——外部用户八要素：外部 ID / 登录名 / 显示名 / 挂靠部门外部 ID / 在职状态
     * ＋联系人三件（email / mobile / avatar）。
     * 语义来自 MdmJobService.syncMdmAccount 与 FeishuSyncService.syncAccounts 的账号字段面。
     */
    public static class UserRow {

        /** 桥侧账号唯一 ID（与 BridgeUserPrincipal.externalId 同一账号体系） */
        public String externalId;

        /** 登录名 */
        public String username;

        /** 显示名 */
        public String displayName;

        /** 挂靠部门外部 ID（未挂部门为 null） */
        public String deptExternalId;

        /** 在职状态：active（在职）/ resigned（离职）/ frozen（冻结停用）；语义来源
         * MdmJobService 同步的 employStatus 与飞书侧 frozen/resigned 状态（允许为 null） */
        public String status;

        /** 邮箱（语义来源两桥同步实现的 setEmail；IdP 未返回为 null） */
        public String email;

        /** 手机号（语义来源两桥同步实现的 setMobile；IdP 未返回为 null） */
        public String mobile;

        /** 头像地址（语义来源 bidr setPictureLink 与飞书 setAvatarUrl；IdP 未返回为 null） */
        public String avatar;
    }
}
