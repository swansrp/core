package com.bidr.agent.console.selection;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Title: SelectionColumnTest
 * Description: 勾选列三态读数（M14 棒6 的判据底座）。三态之所以值得单独立一个类来钉：
 * {@code NULL} 与 {@code '[]'} 在 Java 侧都会读成"没有元素"，业务含义却<b>相反</b>
 * （前者＝未启用＝全部放行，后者＝显式勾零个＝登记过的全不给）。谁写成 {@code list.isEmpty()}
 * 就把两态合并成一态，上线当天所有存量 agent 一起变"手里没工具"。
 * 另外钉坏值方向：读不出勾选集时按 {@link SelectionColumn.State#NONE}（窄）处理，
 * 而不是"当没看见＝全放行"——否则一次手改库就静默变成一次扩权。
 *
 * @author Sharp
 * @since 2026-10-13
 */
class SelectionColumnTest {

    private static final String COLUMN = "sys_agent_definition.capability_packages";

    // ---------------- 第一态：NULL＝未启用 ----------------

    @Test
    @DisplayName("NULL／空串／纯空白＝UNSET（未启用勾选＝全部放行）")
    void blankColumnIsUnset() {
        for (String raw : Arrays.asList(null, "", "   ")) {
            SelectionColumn column = SelectionColumn.parse(raw, COLUMN);
            assertEquals(SelectionColumn.State.UNSET, column.getState(), "原文=[" + raw + "]");
            assertTrue(column.isUnset());
            assertTrue(column.getNames().isEmpty());
        }
    }

    // ---------------- 第二态：[]＝勾了零个 ----------------

    @Test
    @DisplayName("'[]'＝NONE，且与 UNSET 是两个不同的态")
    void emptyArrayIsNoneNotUnset() {
        SelectionColumn none = SelectionColumn.parse("[]", COLUMN);
        assertEquals(SelectionColumn.State.NONE, none.getState());
        assertFalse(none.isUnset(), "🔴 这一条就是「两态不许被 isEmpty 合并」的机器闸");
        assertTrue(none.getNames().isEmpty());
    }

    // ---------------- 第三态：勾了若干 ----------------

    @Test
    @DisplayName("非空数组＝SELECTED，保序去重并裁首尾空白")
    void selectedKeepsOrderTrimsAndDedups() {
        SelectionColumn column = SelectionColumn.parse(
                "['external-task', ' plan ','plan','  ']".replace('\'', '"'), COLUMN);
        assertEquals(SelectionColumn.State.SELECTED, column.getState());
        assertEquals(Arrays.asList("external-task", "plan"), Arrays.asList(column.getNames().toArray()),
                "顺序＝勾选顺序（管理页回显要能对着看），重复名与空白项吸收掉");
    }

    @Test
    @DisplayName("名字集合不可变（勾选集是判据输入，不该被调用方顺手改）")
    void namesAreImmutable() {
        SelectionColumn column = SelectionColumn.parse("[\"plan\"]", COLUMN);
        try {
            column.getNames().add("external-task");
            throw new AssertionError("勾出的名字集应是不可变视图");
        } catch (UnsupportedOperationException expected) {
            // 预期：unmodifiable 视图
        }
    }

    // ---------------- 坏值方向：按窄 ----------------

    @Test
    @DisplayName("🔴 JSON 读不出＝NONE（按窄不按宽）：宁可这个 agent 当场不能干活，也不静默扩权")
    void corruptColumnFallsToNarrowestState() {
        for (String raw : Arrays.asList("{不是 JSON", "[\"plan\"", "{\"a\":1}", "null")) {
            SelectionColumn column = SelectionColumn.parse(raw, COLUMN);
            assertEquals(SelectionColumn.State.NONE, column.getState(), "原文=[" + raw + "] 该按全关处理");
            assertFalse(column.isUnset(), "坏值绝不能被读成\"未启用\"＝放行");
        }
    }

    // ---------------- 写侧与回环 ----------------

    @Test
    @DisplayName("写侧三态：null→NULL、空清单→'[]'、若干→JSON 数组")
    void writerKeepsTheThreeStates() {
        assertNull(SelectionColumn.toColumnValue(null), "null 入参就是写 NULL＝取消启用");
        assertEquals("[]", SelectionColumn.toColumnValue(Collections.<String>emptyList()));
        assertEquals("[\"plan\",\"ask-user\"]",
                SelectionColumn.toColumnValue(Arrays.asList("plan", " ask-user ", "plan")));
    }

    @Test
    @DisplayName("写→读回环：三态各自闭合，SELECTED 名单逐字回来")
    void roundTripPreservesStates() {
        assertSame(SelectionColumn.State.UNSET,
                SelectionColumn.parse(SelectionColumn.toColumnValue(null), COLUMN).getState());
        assertSame(SelectionColumn.State.NONE,
                SelectionColumn.parse(SelectionColumn.toColumnValue(Collections.<String>emptyList()), COLUMN)
                        .getState());
        SelectionColumn back = SelectionColumn.parse(
                SelectionColumn.toColumnValue(Arrays.asList("plan", "external-task")), COLUMN);
        assertSame(SelectionColumn.State.SELECTED, back.getState());
        assertEquals(Arrays.asList("plan", "external-task"), Arrays.asList(back.getNames().toArray()));
    }

    @Test
    @DisplayName("读数自带态名（进日志与告警用，避免\"空\"到底是哪种空说不清）")
    void toStringNamesTheState() {
        assertEquals("UNSET", SelectionColumn.unset().toString());
        assertEquals("NONE", SelectionColumn.parse("[]", COLUMN).toString());
        assertEquals("SELECTED[plan]", SelectionColumn.parse("[\"plan\"]", COLUMN).toString());
    }
}
