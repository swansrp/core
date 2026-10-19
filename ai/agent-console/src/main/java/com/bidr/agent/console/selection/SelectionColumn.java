package com.bidr.agent.console.selection;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * Title: SelectionColumn
 * Description: 勾选列的三态读数（M14 棒6，五列共用的一把尺）。
 *
 * <p>三态之所以必须显式建模：{@code NULL} 与 {@code []} 在 Java 侧都会读成"没有元素"，
 * 但它们的业务含义<b>相反</b>——前者＝未启用勾选＝全部放行（存量 agent 的形态），
 * 后者＝显式勾了零个＝登记过的工具一个都不给。这一层把两者分开，之后所有判据只认
 * {@link State}，不再有人写 {@code list.isEmpty()} 顺手把两态合并。</p>
 *
 * <p>坏值（JSON 解析不了）按 {@link State#NONE} 处理：读不出勾选集时给"全部放行"等于
 * 在最脏的数据上开最大的面；给"一个都不给"会让这个 agent 当场不能干活，但那是
 * <b>能立刻被发现并且必然被追责</b>的故障，符合"收窄层宁可窄不可宽"的红线取向。
 * 写口只由平台管理员经服务层走，正常路径产生不了坏值——出现即说明有人手改过库。</p>
 *
 * @author Sharp
 * @since 2026-10-13
 */
@Slf4j
public final class SelectionColumn {

    public enum State {
        /** 未启用勾选＝该维全部放行 */
        UNSET,
        /** 显式勾了零个＝该维登记过的全不给 */
        NONE,
        /** 勾了若干＝按并集取交集 */
        SELECTED
    }

    /** JSON 列的序列化/反序列化只在这一处做（与装配单同款 ObjectMapper，无全局配置依赖） */
    private static final ObjectMapper OM = new ObjectMapper();

    /** 坏值日志只打前 80 字节：这一列里存的是勾选名，截断足够定位，且不给整段脏文本上日志的机会 */
    private static final int CORRUPT_PREVIEW_CHARS = 80;

    private final State state;

    private final Set<String> names;

    private SelectionColumn(State state, Set<String> names) {
        this.state = state;
        this.names = names;
    }

    public static SelectionColumn unset() {
        return new SelectionColumn(State.UNSET, Collections.emptySet());
    }

    /**
     * 读一列勾选。
     *
     * @param rawJson 库里存的 JSON 数组原文（可为 null）
     * @param column 出问题的列名（进日志用，可为 null）
     */
    public static SelectionColumn parse(String rawJson, String column) {
        if (!StringUtils.hasText(rawJson)) {
            return unset();
        }
        try {
            List<String> values = OM.readValue(rawJson, new TypeReference<List<String>>() {
            });
            Set<String> kept = new LinkedHashSet<>();
            for (String value : values) {
                if (StringUtils.hasText(value)) {
                    kept.add(value.trim());
                }
            }
            return kept.isEmpty()
                    ? new SelectionColumn(State.NONE, Collections.emptySet())
                    : new SelectionColumn(State.SELECTED, Collections.unmodifiableSet(kept));
        } catch (Exception e) {
            log.error("勾选列解析失败，按\"一个都不给\"处理（出现本条＝有人手改过库，请修数据）: column={} 原文=[{}]",
                    column, preview(rawJson), e);
            return new SelectionColumn(State.NONE, Collections.emptySet());
        }
    }

    private static String preview(String raw) {
        return raw.length() <= CORRUPT_PREVIEW_CHARS ? raw : raw.substring(0, CORRUPT_PREVIEW_CHARS) + "…";
    }

    /** 写侧：勾选清单 → JSON 原文（null 入参＝写 NULL＝未启用；空清单＝写 []＝全不给） */
    public static String toColumnValue(List<String> names) {
        if (names == null) {
            return null;
        }
        Set<String> kept = new LinkedHashSet<>();
        for (String name : names) {
            if (StringUtils.hasText(name)) {
                kept.add(name.trim());
            }
        }
        try {
            return OM.writeValueAsString(new ArrayList<>(kept));
        } catch (Exception e) {
            // 字符串列表序列化不可能失败；真失败时按"未启用"落，好过落一个坏值让读侧按全关处理
            return null;
        }
    }

    public State getState() {
        return state;
    }

    public boolean isUnset() {
        return state == State.UNSET;
    }

    public Set<String> getNames() {
        return names;
    }

    @Override
    public String toString() {
        return state + (state == State.SELECTED ? names.toString() : "");
    }
}
