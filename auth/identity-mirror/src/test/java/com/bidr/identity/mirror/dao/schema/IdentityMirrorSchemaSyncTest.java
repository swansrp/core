package com.bidr.identity.mirror.dao.schema;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bidr.identity.mirror.dao.entity.IdmMirrorState;
import com.bidr.identity.mirror.dao.entity.IdmTenantMirror;
import com.bidr.identity.mirror.dao.entity.IdmUserMirror;
import com.bidr.identity.mirror.dao.entity.IdmUserTenantMirror;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

/**
 * Title: IdentityMirrorSchemaSyncTest
 * Description: 镜像四表建表面守卫（T2.3d，离线反射，不起 Spring 不连库；精神照主仓
 * inkhub-common TenantAppSchemaSyncTest 的 DDL↔实体逐列同步闸，core 内自建轻版）——
 * {@code setCreateDDL} 的列集 == 实体 @TableName/@TableId/@TableField 推导列集，
 * 双向相等（漏列＝首条 INSERT 报 Unknown column，多列＝僵尸列没人写）。
 * 另钉各表承诺面键：UK(customer_number)/UK(user_name)/UK(tenant_code)/UK(user_id,tenant_id)/UK(state_key)。
 *
 * @author Sharp
 * @since 2026/11/03
 */
public class IdentityMirrorSchemaSyncTest {

    // ======================== 腿 1：idm_user_mirror ========================

    @Test
    public void userMirrorColumnsStayInSync() {
        assertSync(new IdmUserMirrorSchema(), IdmUserMirror.class, "idm_user_mirror");
        Assert.assertTrue("缺 UK(customer_number)（@BindUser SPI 解析的 matchField 落点）",
                new IdmUserMirrorSchema().getCreateSql().contains("UNIQUE KEY `uk_customer_number` (`customer_number`)"));
        Assert.assertTrue("缺 UK(user_name)（T2.4a：子系统登录闸/成员同步按登录名查人）",
                new IdmUserMirrorSchema().getCreateSql().contains("UNIQUE KEY `uk_user_name` (`user_name`)"));
    }

    // ======================== 腿 2：idm_tenant_mirror ========================

    @Test
    public void tenantMirrorColumnsStayInSync() {
        assertSync(new IdmTenantMirrorSchema(), IdmTenantMirror.class, "idm_tenant_mirror");
        Assert.assertTrue("缺 UK(tenant_code)",
                new IdmTenantMirrorSchema().getCreateSql().contains("UNIQUE KEY `uk_tenant_code` (`tenant_code`)"));
    }

    // ======================== 腿 3：idm_user_tenant_mirror ========================

    @Test
    public void userTenantMirrorColumnsStayInSync() {
        assertSync(new IdmUserTenantMirrorSchema(), IdmUserTenantMirror.class, "idm_user_tenant_mirror");
        Assert.assertTrue("缺 UK(user_id,tenant_id)（同步全量比对判据键，承诺面点名）",
                new IdmUserTenantMirrorSchema().getCreateSql().contains("UNIQUE KEY `uk_user_tenant` (`user_id`,`tenant_id`)"));
    }

    // ======================== 腿 4：idm_mirror_state ========================

    @Test
    public void mirrorStateColumnsStayInSync() {
        assertSync(new IdmMirrorStateSchema(), IdmMirrorState.class, "idm_mirror_state");
        Assert.assertTrue("缺 UK(state_key)（水位 upsert 判据键）",
                new IdmMirrorStateSchema().getCreateSql().contains("UNIQUE KEY `uk_state_key` (`state_key`)"));
    }

    // ======================== 反射脚手架（形态随主仓 TenantAppSchemaSyncTest，轻版） ========================

    /** 双向列集相等＋表名一致＋惯例列在位（valid/create_at/update_at 按 per-table 预期） */
    private static void assertSync(BaseMybatisSchema<?> schema, Class<?> entityClass, String expectedTable) {
        TableName annotation = entityClass.getAnnotation(TableName.class);
        Assert.assertNotNull("实体缺 @TableName: " + entityClass.getSimpleName(), annotation);
        Assert.assertEquals("实体表名", expectedTable, annotation.value());
        Assert.assertEquals("Schema 表名应读实体注解", expectedTable, schema.getTableName());

        String ddl = schema.getCreateSql();
        Assert.assertNotNull("Schema DDL 未注册（static 块缺失/未触发）", ddl);
        Assert.assertTrue("DDL 表名不符: " + ddl, ddl.contains("CREATE TABLE IF NOT EXISTS `" + expectedTable + "`"));

        Set<String> ddlColumns = columnsOf(ddl);
        Set<String> entityColumns = entityColumnsOf(entityClass);
        Assert.assertEquals("DDL 列集 != 实体映射列集（加/删列要 DDL、实体、逐列清单三处一齐动）——DDL="
                + ddlColumns + " 实体=" + entityColumns, entityColumns, ddlColumns);
    }

    /** DDL 逐行取反引号列名（手写 DDL 每列一行，形态同主仓先例） */
    private static Set<String> columnsOf(String ddl) {
        Set<String> columns = new HashSet<>();
        for (String line : ddl.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("`")) {
                int end = trimmed.indexOf('`', 1);
                if (end > 1) {
                    columns.add(trimmed.substring(1, end));
                }
            }
        }
        return columns;
    }

    /** 实体声明字段 → 映射列集（@TableId/@TableField value 剥反引号；无注解字段＝非库列，不计） */
    private static Set<String> entityColumnsOf(Class<?> entityClass) {
        Set<String> columns = new HashSet<>();
        for (Field field : entityClass.getDeclaredFields()) {
            String column = columnOf(field);
            if (column != null && !column.isEmpty()) {
                columns.add(column);
            }
        }
        Assert.assertFalse("实体映射列集为空（反射假设已坏，拒绝空比对）", columns.isEmpty());
        return columns;
    }

    private static String columnOf(Field field) {
        TableId tableId = field.getAnnotation(TableId.class);
        String raw = tableId != null ? tableId.value() : null;
        if (raw == null || raw.isEmpty()) {
            TableField tableField = field.getAnnotation(TableField.class);
            raw = tableField == null ? null : tableField.value();
        }
        return raw == null ? null : raw.replace("`", "");
    }
}
