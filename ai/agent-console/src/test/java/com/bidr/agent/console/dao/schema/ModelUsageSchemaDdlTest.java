package com.bidr.agent.console.dao.schema;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.bidr.agent.console.dao.entity.ModelUsage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Title: ModelUsageSchemaDdlTest
 * Description: 计量表建表面四道闸（M14 棒5）。都是"编译期看不出来、上线第一次调用才炸"的形态：
 * ① 实体每个映射列都真在 DDL 里（漏一列＝MyBatis 插入报 Unknown column，且只在落库线程报、
 *    被兜异常吞成一行 warn，主流程毫无察觉）；
 * ② DDL 不写 CHARSET/COLLATE/ENGINE（本项目建表纪律，跟随库默认）；
 * ③ 🔴 表里没有任何能放明文 key 的列，只有 {@code key_fp}——这条是红线 4 的机器闸，
 *    将来谁想"顺手存一下 key 方便排查"会被这里拦住；
 * ④ 开关默认关：{@code @ConditionalOnProperty} 不得带 matchIfMissing（共享件默认行为不许改）。
 *
 * @author Sharp
 * @since 2026-10-13
 */
class ModelUsageSchemaDdlTest {

    private static final Set<String> EXPECTED_COLUMNS = new HashSet<>(Arrays.asList(
            "id", "call_kind", "purpose_type", "model_name", "key_fp", "session_id", "agent_key", "asked_by",
            "prompt_tokens", "completion_tokens", "reasoning_tokens", "total_tokens", "tool_count",
            "finish_reason", "cost_ms", "call_at", "create_by", "create_at", "valid"));

    private final ModelUsageSchema schema = new ModelUsageSchema();

    @Test
    @DisplayName("表名：core 平台表用裸 sys_ 名，不带项目前缀")
    void tableNameIsBareSysName() {
        assertEquals("sys_model_usage", schema.getTableName());
        assertTrue(schema.getCreateSql().contains("CREATE TABLE IF NOT EXISTS `sys_model_usage`"));
    }

    /** 实体列 ⊆ DDL 列：漏列不会编译报错，只会在落库线程炸 */
    @Test
    @DisplayName("实体每个映射列都在 DDL 内")
    void everyEntityFieldMapsToAnExistingColumn() {
        Set<String> ddlColumns = columnsOf(schema.getCreateSql());
        for (Field field : ModelUsage.class.getDeclaredFields()) {
            String column = columnOf(field);
            if (column == null) {
                continue;
            }
            assertTrue(ddlColumns.contains(column),
                    "实体字段 " + field.getName() + " 映射到列 " + column + "，但 DDL 里没有这一列");
        }
        assertEquals(EXPECTED_COLUMNS, ddlColumns, "DDL 列集与钉住的清单不一致（加/删列要同步本测试与统计读腿）");
    }

    /** 本项目建表纪律：不写字符集/引擎子句，跟随库默认 */
    @Test
    @DisplayName("DDL 不含 CHARSET/COLLATE/ENGINE 子句")
    void ddlCarriesNoCharsetOrEngineClause() {
        String ddl = schema.getCreateSql().toUpperCase();
        assertFalse(ddl.contains("CHARSET"), "建表不得写字符集");
        assertFalse(ddl.contains("COLLATE"), "建表不得写排序规则");
        assertFalse(ddl.contains("ENGINE="), "建表不得写引擎");
    }

    /** 🔴 红线 4 的机器闸：凭据只能是指纹形态，任何能装明文的列名都不许出现 */
    @Test
    @DisplayName("带 key 字样的列只有指纹与 agent 注册键两个，没有明文凭据列，也没有 space_id")
    void onlyKeyFingerprintColumnExists() {
        Set<String> ddlColumns = columnsOf(schema.getCreateSql());
        Set<String> keyLike = new HashSet<>();
        for (String column : ddlColumns) {
            if (column.contains("key")) {
                keyLike.add(column);
            }
        }
        // key_fp＝凭据指纹（唯一能凭"看着像凭据"进表的列）；agent_key＝agent 运行时注册键，不是凭据
        assertEquals(new HashSet<>(Arrays.asList("key_fp", "agent_key")), keyLike,
                "除指纹与 agent 注册键外不得再有 key 类列（明文 key 严禁落库）");
        for (String forbidden : Arrays.asList("api_key", "access_key", "secret", "credential", "password")) {
            for (String column : ddlColumns) {
                assertFalse(column.contains(forbidden),
                        "列名命中禁词 " + forbidden + "：这一列的存在理由就是把凭据留在模型层");
            }
        }
        assertFalse(ddlColumns.contains("space_id"),
                "本表是跨空间的用量证据，带 space_id 会被行级隔离器筛掉，统计读腿查不出全平台总量");
        // 列宽也得撑得住指纹：截断会把一把 key 变成"看起来不一样"的两把
        boolean fpWideEnough = false;
        for (String line : schema.getCreateSql().split("\n")) {
            if (line.trim().startsWith("`key_fp`")) {
                fpWideEnough = line.contains("varchar(64)") || line.contains("varchar(128)");
            }
        }
        assertTrue(fpWideEnough, "key_fp 列宽须 ≥ varchar(64)");
    }

    /** 四个聚合入口都要有索引可依：按时间、按 agent、按用途档、按会话 */
    @Test
    @DisplayName("统计读腿用到的四支索引都在")
    void aggregateIndexesExist() {
        String ddl = schema.getCreateSql();
        for (String index : Arrays.asList("idx_sys_model_usage_call_at", "idx_sys_model_usage_agent_call",
                "idx_sys_model_usage_purpose_call", "idx_sys_model_usage_session")) {
            assertTrue(ddl.contains(index), "缺索引 " + index + "：统计端点会走全表扫");
        }
    }

    /** 两个时刻列分工：call_at＝用量发生（聚合按它），create_at＝插入时刻 */
    @Test
    @DisplayName("两个时刻列都在，聚合列 call_at 有注释指明用途")
    void bothTimestampColumnsExist() {
        Set<String> ddlColumns = columnsOf(schema.getCreateSql());
        assertTrue(ddlColumns.contains("call_at"));
        assertTrue(ddlColumns.contains("create_at"));
        assertNotNull(schema.getCreateSql());
    }

    /** 🔴 共享件纪律：新口径挂默认关闭的参数，缺配置时 Bean 不存在＝连上报都不发 */
    @Test
    @DisplayName("建表、落库腿与统计读腿三个 Bean 的开关都默认关（无 matchIfMissing）")
    void usageSwitchDefaultsToOff() {
        assertSwitchDefaultsOff(ModelUsageSchema.class);
        assertSwitchDefaultsOff(com.bidr.agent.console.usage.ModelUsageRecorder.class);
        // 读腿必须跟写侧同生同死：表没建却开着端点，第一次调用就是一条 SQL 报错
        assertSwitchDefaultsOff(com.bidr.agent.console.AgentUsageController.class);
    }

    private static void assertSwitchDefaultsOff(Class<?> type) {
        ConditionalOnProperty condition = type.getAnnotation(ConditionalOnProperty.class);
        assertNotNull(condition, type.getSimpleName() + " 须挂 agent-console.usage.enabled 开关");
        assertEquals("agent-console.usage", condition.prefix());
        assertEquals(1, condition.name().length);
        assertEquals("enabled", condition.name()[0]);
        assertEquals("true", condition.havingValue());
        assertFalse(condition.matchIfMissing(),
                type.getSimpleName() + " 不得默认开启：没配这个键的工程行为必须与改前一致");
    }

    /** 列名→DDL 出现即算（本表 DDL 手写、每列一行，无需解析类型） */
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
