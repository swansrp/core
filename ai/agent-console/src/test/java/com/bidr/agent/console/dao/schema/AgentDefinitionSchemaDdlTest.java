package com.bidr.agent.console.dao.schema;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.bidr.agent.console.AgentDefinitionAdminController;
import com.bidr.agent.console.dao.entity.AgentDefinition;
import com.bidr.agent.console.selection.AgentCapabilitySelector;
import com.bidr.agent.console.selection.AgentDefinitionAdminService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Title: AgentDefinitionSchemaDdlTest
 * Description: agent 定义表建表面五道闸（M14 棒6）。挑出来都是"改前看不出、上线第一次点保存才炸"的形态：
 * ① 实体每个映射列都真在 DDL 里（漏一列＝INSERT 报 Unknown column）；
 * ② 五列勾选全是 {@code text}——{@code varchar(255)} 会在勾了七八个包时静默截断成坏 JSON，
 *    而坏 JSON 在读侧按"一个都不给"处理，等于把一次保存变成一场"这 agent 怎么没工具了"的悬案；
 * ③ {@code agent_key} 上有 UNIQUE（一行一个 agent，写腿靠它判存在）；
 * ④ 建表纪律：不写 CHARSET/COLLATE/ENGINE，不参与空间隔离（没有 space_id）；
 * ⑤ 🔴 开关默认关：机制三件套（建表、交集选择器）都挂 {@code agent-console.selection.enabled}
 *    且不得带 matchIfMissing——共享件默认行为不许改。
 *
 * @author Sharp
 * @since 2026-10-13
 */
class AgentDefinitionSchemaDdlTest {

    private static final Set<String> EXPECTED_COLUMNS = new HashSet<>(Arrays.asList(
            "id", "agent_key", "capability_packages", "system_skills", "external_capabilities",
            "model_purpose", "prompt_segments", "create_by", "create_at", "update_by", "update_at", "valid"));

    /** 五个勾选列（建表即冻结，增减要同步本清单与管理页） */
    private static final Set<String> SELECTION_COLUMNS = new HashSet<>(Arrays.asList(
            "capability_packages", "system_skills", "external_capabilities", "model_purpose", "prompt_segments"));

    private final AgentDefinitionSchema schema = new AgentDefinitionSchema();

    @Test
    @DisplayName("表名：core 平台表用裸 sys_ 名，不带项目前缀")
    void tableNameIsBareSysName() {
        assertEquals("sys_agent_definition", schema.getTableName());
        assertTrue(schema.getCreateSql().contains("CREATE TABLE IF NOT EXISTS `sys_agent_definition`"));
    }

    @Test
    @DisplayName("实体每个映射列都在 DDL 内，且列集与钉住的清单一致")
    void everyEntityFieldMapsToAnExistingColumn() {
        Set<String> ddlColumns = columnsOf(schema.getCreateSql());
        for (Field field : AgentDefinition.class.getDeclaredFields()) {
            String column = columnOf(field);
            if (column == null) {
                continue;
            }
            assertTrue(ddlColumns.contains(column),
                    "实体字段 " + field.getName() + " 映射到列 " + column + "，但 DDL 里没有这一列");
        }
        assertEquals(EXPECTED_COLUMNS, ddlColumns,
                "DDL 列集与钉住的清单不符（加/删列要同步本测试、读写端点与管理页）");
    }

    /** 🔴 勾选列必须是 text：varchar 截断出的坏 JSON 会被读侧按"全关"处理，故障形态从"存不下"变成"没工具" */
    @Test
    @DisplayName("五个勾选列全是 text 且注释写明三态；唯一参与运行期的一列把三态逐字写全")
    void selectionColumnsAreTextAndDocumentThreeStates() {
        String ddl = schema.getCreateSql();
        for (String column : SELECTION_COLUMNS) {
            String line = lineOf(ddl, column);
            assertTrue(line.contains("text"), column + " 必须是 text 列（防截断出坏 JSON）: " + line);
            assertTrue(line.contains("三态"), column + " 的列注释要写明三态语义: " + line);
            if (!"capability_packages".equals(column)) {
                assertTrue(line.contains("本棒只登记"), column
                        + " 不参与运行期，注释必须写明白，免得被读成\"表里有列＝那维已经能配\"");
            }
        }
        String live = lineOf(ddl, "capability_packages");
        assertTrue(live.contains("NULL") && live.contains("[]"),
                "唯一参与运行期的这一列要把两态的字面判据写进注释（NULL＝放行／[]＝全不给）: " + live);
    }

    @Test
    @DisplayName("agent_key 非空且带 UNIQUE（一行一个 agent，写腿按它判存在）")
    void agentKeyIsUniqueAndNotNull() {
        String line = lineOf(schema.getCreateSql(), "agent_key");
        assertTrue(line.contains("NOT NULL"), "agent_key 不许为空");
        assertTrue(schema.getCreateSql().contains(
                "UNIQUE KEY `uk_sys_agent_definition_agent` (`agent_key`)"), "缺 agent_key 唯一索引");
    }

    @Test
    @DisplayName("本项目建表纪律：无 CHARSET/COLLATE/ENGINE，且不带 space_id（平台表不参与行级隔离）")
    void ddlCarriesNoCharsetEngineOrSpaceColumn() {
        String ddl = schema.getCreateSql().toUpperCase();
        assertFalse(ddl.contains("CHARSET"), "建表不得写字符集");
        assertFalse(ddl.contains("COLLATE"), "建表不得写排序规则");
        assertFalse(ddl.contains("ENGINE="), "建表不得写引擎");
        assertFalse(columnsOf(schema.getCreateSql()).contains("space_id"),
                "本表按 agent_key 一行、全平台生效，带 space_id 会被行级隔离器筛掉");
    }

    @Test
    @DisplayName("🔴 开关默认关：建表、交集选择器、读写服务与端点四件套都挂 selection.enabled 且无 matchIfMissing")
    void selectionSwitchDefaultsToOff() {
        assertSwitchDefaultsOff(AgentDefinitionSchema.class);
        // 交集选择器是真正改运行期工具面的那一个：没配键的工程里它必须不存在，工具面回到 feature 面
        assertSwitchDefaultsOff(AgentCapabilitySelector.class);
        // 读写面与建表同生同死：表没建（开关关着）却开着端点，第一次调用就是一条 SQL 报错（同棒 5 用量统计口径）
        assertSwitchDefaultsOff(AgentDefinitionAdminService.class);
        assertSwitchDefaultsOff(AgentDefinitionAdminController.class);
    }

    /** 三态的第一态（NULL＝放行）由列默认保证：除主键与 agent_key 外不得有 NOT NULL 列 */
    @Test
    @DisplayName("只有 id／agent_key 是 NOT NULL——勾选列默认 NULL 才等于\"未启用\"")
    void onlyAgentKeyIsNotNull() {
        String ddl = schema.getCreateSql();
        for (String column : columnsOf(ddl)) {
            if ("id".equals(column) || "agent_key".equals(column)) {
                continue;
            }
            assertFalse(lineOf(ddl, column).contains("NOT NULL"),
                    column + " 不该带 NOT NULL：三态的第一态要靠 NULL 表达");
        }
    }

    private static void assertSwitchDefaultsOff(Class<?> type) {
        ConditionalOnProperty condition = type.getAnnotation(ConditionalOnProperty.class);
        assertNotNull(condition, type.getSimpleName() + " 须挂 agent-console.selection.enabled 开关");
        assertEquals("agent-console.selection", condition.prefix());
        assertEquals(1, condition.name().length);
        assertEquals("enabled", condition.name()[0]);
        assertEquals("true", condition.havingValue());
        assertFalse(condition.matchIfMissing(),
                type.getSimpleName() + " 不得默认开启：没配这个键的工程行为必须与改前一致");
    }

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

    /** 取某一列的 DDL 行（本表 DDL 手写、每列一行） */
    private static String lineOf(String ddl, String column) {
        for (String line : ddl.split("\n")) {
            if (line.trim().startsWith("`" + column + "`")) {
                return line;
            }
        }
        throw new AssertionError("DDL 里找不到列 " + column);
    }

    private static String columnOf(Field field) {
        TableId tableId = field.getAnnotation(TableId.class);
        String raw = tableId != null ? tableId.value() : null;
        if (raw == null) {
            TableField tableField = field.getAnnotation(TableField.class);
            raw = tableField == null ? null : tableField.value();
        }
        return raw == null ? null : raw.replace("`", "");
    }
}
