package com.bidr.llm.flow;

import lombok.Data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Title: FlowNodeMeta
 * Description: 结点类型元数据（工作台画布的数据源）——执行器经 {@link com.bidr.llm.flow.executor.FlowNodeExecutor#nodeMeta()}
 * 声明，管理页据此渲染左侧结点清单与右侧属性表单（schema 驱动，新结点类型无需改前端）：
 * <ul>
 *     <li>type/label/desc：palette 条目（label 同时作画布默认结点名）；</li>
 *     <li>hint：结点级块提示（属性面板尾部，如 extract 的模式说明），空则不显示；</li>
 *     <li>fields：配置表单 schema，按 input 分控件渲染——text/textarea/select/switch/outputMap，
 *         defaultValue 同时是画布新增结点的初始 config（未声明的 key 不预置）。</li>
 * </ul>
 * 颜色等视觉资产由前端色板维护，不在元数据内。
 *
 * @author Sharp
 * @since 2026/8/16
 */
@Data
public class FlowNodeMeta {

    /**
     * 结点类型标识（与执行器 type() 一致）
     */
    private String type;

    /**
     * 画布显示名（palette 名称与新增结点缺省名）
     */
    private String label;

    /**
     * 类型职责一句话（palette 提示）
     */
    private String desc;

    /**
     * 结点级块提示（属性面板尾部），空则不渲染
     */
    private String hint;

    /**
     * 配置表单 schema（空列表 = 该类型无需额外配置）
     */
    private List<ConfigField> fields = new ArrayList<>();

    public static FlowNodeMeta of(String type, String label, String desc) {
        FlowNodeMeta meta = new FlowNodeMeta();
        meta.setType(type);
        meta.setLabel(label);
        meta.setDesc(desc);
        return meta;
    }

    /**
     * Title: ConfigField
     * Description: 单个配置项的表单元数据——input 决定控件：text/textarea/select/switch/outputMap
     *
     * @author Sharp
     * @since 2026/8/16
     */
    @Data
    public static class ConfigField {

        /**
         * config 键（读写 graph.nodes[].config[key]）
         */
        private String key;

        /**
         * 表单标签
         */
        private String label;

        /**
         * 控件类型：text/textarea/select/switch/outputMap
         */
        private String input;

        /**
         * 输入提示（text/textarea）
         */
        private String placeholder;

        /**
         * switch 的行内提示
         */
        private String hint;

        /**
         * textarea 行数
         */
        private Integer rows;

        /**
         * select 选项
         */
        private List<Option> options;

        /**
         * 画布新增结点的初始 config 值（null 则不预置该键）
         */
        private Object defaultValue;

        public static ConfigField text(String key, String label, String placeholder) {
            ConfigField field = new ConfigField();
            field.setKey(key);
            field.setLabel(label);
            field.setInput("text");
            field.setPlaceholder(placeholder);
            return field;
        }

        public static ConfigField textarea(String key, String label, String placeholder, int rows) {
            ConfigField field = new ConfigField();
            field.setKey(key);
            field.setLabel(label);
            field.setInput("textarea");
            field.setPlaceholder(placeholder);
            field.setRows(rows);
            return field;
        }

        public static ConfigField select(String key, String label, Option... options) {
            ConfigField field = new ConfigField();
            field.setKey(key);
            field.setLabel(label);
            field.setInput("select");
            field.setOptions(Arrays.asList(options));
            return field;
        }

        public static ConfigField switchField(String key, String label, String hint) {
            ConfigField field = new ConfigField();
            field.setKey(key);
            field.setLabel(label);
            field.setInput("switch");
            field.setHint(hint);
            return field;
        }

        /**
         * output 结点的映射表专用控件（响应字段名 → 变量名，键值对行编辑）
         */
        public static ConfigField outputMap(String key, String label) {
            ConfigField field = new ConfigField();
            field.setKey(key);
            field.setLabel(label);
            field.setInput("outputMap");
            return field;
        }

        public ConfigField defaultValue(Object value) {
            this.defaultValue = value;
            return this;
        }
    }

    /**
     * Title: Option
     * Description: select 控件选项（value 即 config 存值）
     *
     * @author Sharp
     * @since 2026/8/16
     */
    @Data
    public static class Option {

        private String value;

        private String label;

        public Option(String value, String label) {
            this.value = value;
            this.label = label;
        }
    }
}
