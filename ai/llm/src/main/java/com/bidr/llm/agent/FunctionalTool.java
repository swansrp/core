package com.bidr.llm.agent;

import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;

import java.util.Map;
import java.util.function.Function;

/**
 * Title: FunctionalTool
 * Description: 函数式工具通道：一只工具＝一份声明（名字＋说明＋参数 JSON schema）＋一个进程内处理器，
 * 不要求它是带 {@code @Tool} 注解的反射方法。
 * <p>
 * 开这条通道只有一个理由：<b>让"一只工具"能被纯数据描述</b>。反射面的 schema 是从方法签名倒推出来的，
 * 所以工具的形态绑死在源码上；声明式这三件字段全是可直接序列化的值，于是声明有朝一日能从台账行、
 * 外部契约甚至一次 HTTP 注册里读出来，而不必随发版进代码（执行仍在进程内，本接口不引入任何远程调用）。
 * <p>
 * 三条边界，都在 {@link ToolAgentRunner} 那一处收口，不在实现方各守一份：
 * <ol>
 *   <li><b>结构性上限不因为开了这条路而松动</b>：声明式与反射式合并成<b>同一份</b> specs 清单，
 *       所以平台勾选、装配单、发给模型的 schema、执行期准入名单四处仍是同一份数据——
 *       声明式工具同样"能被勾掉，勾掉即执行不到"；</li>
 *   <li>🔴 <b>同名＝代码优先</b>：反射面已有这个名字时，整份声明失效（不进 specs、执行期也路由不到）
 *       并 warn 一条。一条配置不该顶掉已上线的行为，冲突必须显形；</li>
 *   <li><b>失败文本同形</b>：参数解析失败与处理器抛错，走的是与反射腿一字不差的错误文案——
 *       模型看到的自纠提示不该因为工具是哪条通道来的而不同。</li>
 * </ol>
 *
 * @author Sharp
 * @since 2026-10-13
 */
public interface FunctionalTool {

    /** 工具名（模型调用时的键）；与反射面同名即冲突，冲突裁决＝代码优先 */
    String name();

    /** 给模型看的说明，等价于 {@code @Tool} 的 value */
    String description();

    /** 参数 JSON schema，等价于反射从方法签名倒推出来的那份（可直接由数据反序列化） */
    ToolParameters parameters();

    /**
     * 执行：入参是模型给出的 arguments JSON 解析后的扁平 Map（键＝schema 里的属性名，值＝JSON 原值，
     * 缺省键不入图）；返回文本原样回填给模型，null 视同"无返回值"。
     * <p>抛出的异常由框架转成与反射腿同形的错误文本回给模型自纠，实现方不必自己兜。
     */
    String invoke(Map<String, Object> args);

    /** 声明三件套拼成引擎用的 spec——与反射腿 {@code ToolSpecifications} 产出的是同一类型，可逐字段比对 */
    default ToolSpecification spec() {
        ToolSpecification.Builder builder = ToolSpecification.builder().name(name());
        if (description() != null) {
            builder.description(description());
        }
        if (parameters() != null) {
            builder.parameters(parameters());
        }
        return builder.build();
    }

    /** 轻量工厂：把现成的 service 方法用一个 lambda 声明成工具（处理器即那次调用） */
    static FunctionalTool of(String name, String description, ToolParameters parameters,
                             Function<Map<String, Object>, String> handler) {
        return new Declared(name, description, parameters, handler);
    }

    /** {@link #of} 的实现体：四件字段全部来自声明，无任何反射 */
    final class Declared implements FunctionalTool {

        private final String name;
        private final String description;
        private final ToolParameters parameters;
        private final Function<Map<String, Object>, String> handler;

        public Declared(String name, String description, ToolParameters parameters,
                        Function<Map<String, Object>, String> handler) {
            this.name = name;
            this.description = description;
            this.parameters = parameters;
            this.handler = handler;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String description() {
            return description;
        }

        @Override
        public ToolParameters parameters() {
            return parameters;
        }

        @Override
        public String invoke(Map<String, Object> args) {
            return handler.apply(args);
        }
    }
}
