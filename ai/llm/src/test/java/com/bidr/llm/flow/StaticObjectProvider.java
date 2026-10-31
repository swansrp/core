package com.bidr.llm.flow;

import org.springframework.beans.factory.ObjectProvider;

/**
 * Title: StaticObjectProvider
 * Description: 测试用固定值 ObjectProvider——单测不起容器时模拟可选装配：
 * value 为 null 即"bean 缺席"（getIfAvailable 返回 null），非 null 即"已装配"。
 *
 * @author Sharp
 * @since 2026/11/1
 */
public class StaticObjectProvider<T> implements ObjectProvider<T> {

    private final T value;

    public StaticObjectProvider(T value) {
        this.value = value;
    }

    @Override
    public T getObject() {
        if (value == null) {
            throw new IllegalStateException("测试 ObjectProvider 未持有实例");
        }
        return value;
    }

    @Override
    public T getObject(Object... args) {
        return getObject();
    }

    @Override
    public T getIfAvailable() {
        return value;
    }

    @Override
    public T getIfUnique() {
        return value;
    }
}
